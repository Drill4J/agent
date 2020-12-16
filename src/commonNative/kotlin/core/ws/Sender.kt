package com.epam.drill.core.ws

import com.epam.drill.logger.*
import com.epam.drill.ws.*
import com.epam.drill.zstd.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*

object Sender {

    val logger = Logging.logger("AsyncSender")

    inline fun <reified T : Any> send(message: T) {
        val messageForSend = ProtoBuf.dump(T::class.serializer(), message)
        logger.trace { "Initial message size: ${messageForSend.size}" }

        val compressed = Zstd.compress(input = messageForSend)
        logger.trace { "Compressed message size: ${compressed.size}" }

        ws.value?.send(compressed)

    }

}
