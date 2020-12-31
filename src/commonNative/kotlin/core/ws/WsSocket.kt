package com.epam.drill.core.ws

import com.epam.drill.*
import com.epam.drill.common.*
import com.epam.drill.logger.*
import com.epam.drill.transport.*
import com.epam.drill.transport.common.ws.*
import com.epam.drill.ws.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*
import kotlin.coroutines.*
import kotlin.native.concurrent.*
import kotlin.time.*

@SharedImmutable
private val wsLogger = Logging.logger("DrillWebsocket")

private val dispatcher = newSingleThreadContext("sender coroutine")


class WsSocket : CoroutineScope {

    override val coroutineContext: CoroutineContext = dispatcher

    init {
        topicRegister()
    }

    fun connect(adminUrl: String) {
        val url = URL("$adminUrl/agent/attach")
        val wsClient = WSClientFactory.createClient(
            url, mutableMapOf(
                AgentConfigParam to ProtoBuf.encodeToHexString(AgentConfig.serializer(), agentConfig),
                NeedSyncParam to agentConfig.needSync.toString(),
                HttpHeaders.ContentEncoding to "deflate"
            )
        )
        ws.value = wsClient
        wsClient.onOpen {
            wsLogger.info { "Agent connected" }
        }

        wsClient.onBinaryMessage { rawMessage ->
            val message = rawMessage.toWsMessage()
            val destination = message.destination
            val topic = WsRouter[destination]
            if (topic != null) {
                launch {
                    when (topic) {
                        is PluginTopic -> {
                            val pluginMetadata = ProtoBuf.decodeFromByteArray(PluginBinary.serializer(), message.data)
                            val duration = measureTime { topic.block(pluginMetadata.meta, pluginMetadata.data) }
                            wsLogger.debug { "'$destination' took $duration" }
                            Sender.send(Message(MessageType.MESSAGE_DELIVERED, "/agent/load"))
                        }
                        is InfoTopic -> {
                            val duration = measureTime { topic.run(message.data) }
                            wsLogger.debug { "'$destination' took $duration" }
                            Sender.send(
                                Message(
                                    MessageType.MESSAGE_DELIVERED,
                                    destination
                                )
                            )
                        }
                        is GenericTopic<*> -> {
                            val duration = measureTime { topic.deserializeAndRun(message.data) }
                            wsLogger.debug { "'$destination' took $duration" }
                            Sender.send(
                                Message(
                                    MessageType.MESSAGE_DELIVERED,
                                    destination
                                )
                            )
                        }
                    }
                }
            } else {
                wsLogger.warn { "topic with name '$destination' didn't register" }
            }
        }

        wsClient.onError {
            wsLogger.error { "WS error" }
        }
        wsClient.onClose {
            wsLogger.info { "Websocket closed" }
        }

    }


}

private fun ByteArray.toWsMessage() = ProtoBuf.decodeFromByteArray(Message.serializer(), this)
