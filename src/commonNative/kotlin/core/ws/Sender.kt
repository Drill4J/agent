package com.epam.drill.core.ws

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*
import kotlin.coroutines.*
import kotlin.native.concurrent.*
import kotlin.native.internal.*

@SharedImmutable
private val dispatcher = newSingleThreadContext("sender coroutine")

object Sender : CoroutineScope {

    operator fun invoke(block: suspend () -> Unit) = launch {
        block()
        GC.collect()
    }

    inline fun <reified T : Any> send(message: T) = launch {
        msChannel.send(ProtoBuf.dump(T::class.serializer(), message))
        GC.collect()
    }

    override val coroutineContext: CoroutineContext = dispatcher

}