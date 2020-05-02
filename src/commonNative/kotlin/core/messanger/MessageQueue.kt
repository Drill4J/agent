package com.epam.drill.core.messanger

import com.epam.drill.common.*
import com.epam.drill.core.*
import com.epam.drill.core.plugin.dto.*
import com.epam.drill.core.ws.*
import kotlinx.cinterop.*

fun sendNativeMessage(pluginId: CPointer<ByteVar>, content: CPointer<ByteVar>) {
    sendMessage(pluginId.toKString(), content.toKString())
}

fun sendMessage(pluginId: String, content: String) {
    Sender.send(
        Message(
            MessageType.PLUGIN_DATA,
            "",
            (MessageWrapper.serializer() stringify MessageWrapper(
                pluginId,
                DrillMessage(drillRequest()?.drillSessionId ?: "", content)
            )).encodeToByteArray()
        )
    )
}