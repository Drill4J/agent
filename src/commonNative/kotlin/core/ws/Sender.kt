package com.epam.drill.core.ws

import com.epam.drill.logger.*
import com.epam.drill.zlib.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*
import kotlin.coroutines.*
import kotlin.native.concurrent.*
import kotlin.native.internal.*

@SharedImmutable
private val dispatcher = newSingleThreadContext("sender coroutine")

object Sender : CoroutineScope {

    val logger = Logging.logger("AsyncSender")

    operator fun invoke(block: suspend () -> Unit) = launch {
        block()
        GC.collect()
    }

    inline fun <reified T : Any> send(message: T) = launch {
        val messageForSend = ProtoBuf.dump(T::class.serializer(), message)
        logger.trace { "Initial message size: ${messageForSend.size}" }
        val compressed = Deflate.encode(messageForSend, nowrap = true)
        logger.trace { "Compressed message size: ${compressed.size}" }
        msChannel.send(compressed)
        GC.collect()
    }

    override val coroutineContext: CoroutineContext = dispatcher

}