package com.epam.drill.core.messanger

import com.epam.drill.api.drillRequest
import com.epam.drill.common.Message
import com.epam.drill.common.MessageType
import com.epam.drill.common.stringify
import com.epam.drill.core.plugin.dto.DrillMessage
import com.epam.drill.core.plugin.dto.MessageWrapper

object MessageQueue {

    fun sendMessage(pluginId: String, content: String) {
        val message =

            Message.serializer() stringify Message(
                MessageType.PLUGIN_DATA, "",


                MessageWrapper.serializer() stringify
                        MessageWrapper(
                            pluginId, DrillMessage(
                                drillRequest()?.drillSessionId ?: "", content
                            )
                        )
            )


        com.epam.drill.core.ws.sendMessage(message)
    }
}