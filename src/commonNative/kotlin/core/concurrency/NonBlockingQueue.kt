/**
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.core.concurrency

import kotlin.native.concurrent.*

private typealias Core<E> = LockFreeMPSCQueueCore<E>
//TODO replace to KTOR core.concurrency.LockFreeMPSCQueue in 1.4.0
class LockFreeMPSCQueue<E : Any> {
    private val _cur = AtomicReference(Core<E>(LockFreeMPSCQueueCore.INITIAL_CAPACITY))
    private val closed = AtomicInt(0)

    fun close() {
        try {
            _cur.loop { cur ->
                if (cur.close()) return // closed this copy
                _cur.compareAndSet(cur, cur.next()) // move to next
            }
        } finally {
            if (!closed.compareAndSet(0, 1)) return
        }
    }

    fun addLast(element: E): Boolean {
        _cur.loop { cur ->
            when (cur.addLast(element)) {
                LockFreeMPSCQueueCore.ADD_SUCCESS -> return true
                LockFreeMPSCQueueCore.ADD_CLOSED -> return false
                LockFreeMPSCQueueCore.ADD_FROZEN -> _cur.compareAndSet(cur, cur.next()) // move to next
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun removeFirstOrNull(): E? {
        _cur.loop { cur ->
            val result = cur.removeFirstOrNull()
            if (result !== LockFreeMPSCQueueCore.REMOVE_FROZEN) return result as E?
            _cur.compareAndSet(cur, cur.next())
        }
    }
}

private class LockFreeMPSCQueueCore<E : Any>(private val capacity: Int) {
    private val mask = capacity - 1
    private val _next = AtomicReference<Core<E>?>(null)
    private val _state = AtomicLong(0L)
    private val array = Array<AtomicReference<Any?>>(capacity) { AtomicReference(null) }

    init {
        freeze()
    }

    private fun setArrayValueHelper(index: Int, value: Any?) {
        array[index].value = value
    }

    init {
        check(mask <= MAX_CAPACITY_MASK)
        check(capacity and mask == 0)
    }

    fun close(): Boolean {
        _state.update { state ->
            if (state and CLOSED_MASK != 0L) return true // ok - already closed
            if (state and FROZEN_MASK != 0L) return false // frozen -- try next
            state or CLOSED_MASK // try set closed bit
        }
        return true
    }

    fun addLast(element: E): Int {
        _state.loop { state ->
            if (state and (FROZEN_MASK or CLOSED_MASK) != 0L) return state.addFailReason() // cannot add
            state.withState { head, tail ->
                if ((tail + 2) and mask == head and mask) return ADD_FROZEN // overfull, so do freeze & copy
                val newTail = (tail + 1) and MAX_CAPACITY_MASK
                if (_state.compareAndSet(state, state.updateTail(newTail))) {
                    array[tail and mask].value = element
                    var cur = this
                    while (true) {
                        if (cur._state.value and FROZEN_MASK == 0L) break // all fine -- not frozen yet
                        cur = cur.next().fillPlaceholder(tail, element) ?: break
                    }
                    return ADD_SUCCESS // added successfully
                }
            }
        }
    }

    private fun fillPlaceholder(index: Int, element: E): Core<E>? {
        val old = array[index and mask].value
        if (old is Placeholder && old.index == index) {
            array[index and mask].value = element
            // we've corrected missing element, should check if that propagated to further copies, just in case
            return this
        }
        // it is Ok, no need for further action
        return null
    }

    fun removeFirstOrNull(): Any? {
        _state.loop { state ->
            if (state and FROZEN_MASK != 0L) return REMOVE_FROZEN // frozen -- cannot modify
            state.withState { head, tail ->
                if ((tail and mask) == (head and mask)) return null // empty
                val element = array[head and mask].value ?: return null
                if (element is Placeholder) return null // same story -- consider it not added yet
                val newHead = (head + 1) and MAX_CAPACITY_MASK
                if (_state.compareAndSet(state, state.updateHead(newHead))) {
                    array[head and mask].value = null // now can safely put null (state was updated)
                    return element // successfully removed in fast-path
                }
                var cur = this
                while (true) {
                    @Suppress("UNUSED_VALUE")
                    cur = cur.removeSlowPath(head, newHead) ?: return element
                }
            }
        }
    }

    private fun removeSlowPath(oldHead: Int, newHead: Int): Core<E>? {
        _state.loop { state ->
            state.withState { head, _ ->
                check(head == oldHead) { "This queue can have only one consumer" }
                if (state and FROZEN_MASK != 0L) {
                    return next() // continue to correct head in next
                }
                if (_state.compareAndSet(state, state.updateHead(newHead))) {
                    array[head and mask].value = null // now can safely put null (state was updated)
                    return null
                }
            }
        }
    }

    fun next(): LockFreeMPSCQueueCore<E> = allocateOrGetNextCopy(markFrozen())

    private fun markFrozen(): Long =
        _state.updateAndGet { state ->
            if (state and FROZEN_MASK != 0L) return state // already marked
            state or FROZEN_MASK
        }

    private fun allocateOrGetNextCopy(state: Long): Core<E> {
        _next.loop { next ->
            if (next != null) return next // already allocated & copied
            _next.compareAndSet(null, allocateNextCopy(state))
        }
    }


    private fun allocateNextCopy(state: Long): Core<E> {
        val next =
            LockFreeMPSCQueueCore<E>(capacity * 2)
        state.withState { head, tail ->
            var index = head
            while (index and mask != tail and mask) {
                // replace nulls with placeholders on copy
                val value = array[index and mask].value
                next.setArrayValueHelper(index and next.mask, value ?: Placeholder(
                    index
                )
                )
                index++
            }
            next._state.value = state wo FROZEN_MASK
        }
        return next
    }

    private class Placeholder(val index: Int)

    @Suppress("PrivatePropertyName")
    internal companion object {
        internal const val INITIAL_CAPACITY = 8

        private const val CAPACITY_BITS = 30
        private const val MAX_CAPACITY_MASK = (1 shl CAPACITY_BITS) - 1
        private const val HEAD_SHIFT = 0
        private const val HEAD_MASK = MAX_CAPACITY_MASK.toLong() shl HEAD_SHIFT
        private const val TAIL_SHIFT = HEAD_SHIFT + CAPACITY_BITS
        private const val TAIL_MASK = MAX_CAPACITY_MASK.toLong() shl TAIL_SHIFT

        private const val FROZEN_SHIFT = TAIL_SHIFT + CAPACITY_BITS
        private const val FROZEN_MASK = 1L shl FROZEN_SHIFT
        private const val CLOSED_SHIFT = FROZEN_SHIFT + 1
        private const val CLOSED_MASK = 1L shl CLOSED_SHIFT

        internal val REMOVE_FROZEN = object {
            override fun toString() = "REMOVE_FROZEN"
        }

        internal const val ADD_SUCCESS = 0
        internal const val ADD_FROZEN = 1
        internal const val ADD_CLOSED = 2

        private infix fun Long.wo(other: Long) = this and other.inv()
        private fun Long.updateHead(newHead: Int) = (this wo HEAD_MASK) or (newHead.toLong() shl HEAD_SHIFT)
        private fun Long.updateTail(newTail: Int) = (this wo TAIL_MASK) or (newTail.toLong() shl TAIL_SHIFT)

        private inline fun <T> Long.withState(block: (head: Int, tail: Int) -> T): T {
            val head = ((this and HEAD_MASK) shr HEAD_SHIFT).toInt()
            val tail = ((this and TAIL_MASK) shr TAIL_SHIFT).toInt()
            return block(head, tail)
        }

        // FROZEN | CLOSED
        private fun Long.addFailReason(): Int = if (this and CLOSED_MASK != 0L) ADD_CLOSED else ADD_FROZEN
    }
}

inline fun AtomicLong.loop(action: (Long) -> Unit): Nothing {
    while (true) {
        action(value)
    }
}

inline fun AtomicLong.update(function: (Long) -> Long) {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return
    }
}
inline fun AtomicLong.updateAndGet(function: (Long) -> Long): Long {
    while (true) {
        val cur = value
        val upd = function(cur)
        if (compareAndSet(cur, upd)) return upd
    }
}

inline fun <T> AtomicReference<T>.loop(action: (T) -> Unit): Nothing {
    while (true) {
        action(value)
    }
}
