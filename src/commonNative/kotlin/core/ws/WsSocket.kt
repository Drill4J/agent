package com.epam.drill.core.ws

import com.epam.drill.*
import com.epam.drill.common.*
import com.epam.drill.core.exceptions.*
import com.epam.drill.transport.ws.*
import com.soywiz.krypto.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.*
import mu.*
import kotlin.coroutines.*
import kotlin.native.concurrent.*

const val DELAY = 1000L

@SharedImmutable
private val wsLogger = KotlinLogging.logger("DrillWebsocket")

@ThreadLocal
private val binaryTopicsStorage = HashMap<PluginMetadata, PluginTopic>()

@SharedImmutable
private val dispatcher = newSingleThreadContext("sender coroutine")

private val attemptCounter = AtomicInt(0.freeze()).freeze()

class WsSocket(
    val onBinaryMessage: (suspend (ByteArray) -> Unit)? = null,
    val onStringMessage: (suspend (String) -> Unit)? = null,
    val onAnyMessage: (suspend (Any) -> Unit)? = null
) : CoroutineScope {

    override val coroutineContext: CoroutineContext = dispatcher + CoroutineExceptionHandler { _, ex ->
        wsLogger.error { "WS error: ${ex.message}" }
        wsLogger.debug { "try reconnect" }
        attemptCounter.increment()
        connect(exec { adminAddress.toString() })
    }

    private val mainChannel = msChannel

    init {
        launch { topicRegister() }
    }

    fun connect(adminUrl: String) = launch {
        delay(((DELAY + attemptCounter.value * 1000)))
        val url = "$adminUrl/agent/attach"
        wsLogger.debug { "try to create websocket $url" }
        process(url, mainChannel)
    }

    private suspend fun process(url: String, msChannel: Channel<String>) {
        val wsClient = RWebsocketClient(
            url = url,
            protocols = emptyList(),
            origin = "",
            wskey = "",
            params = mutableMapOf(
                AgentConfigParam to Cbor.dumps(AgentConfig.serializer(), exec { agentConfig }),
                NeedSyncParam to exec { agentConfig.needSync }.toString()
            )
        )
        wsClient.onOpen += {
            wsLogger.debug { "Agent connected" }
        }

        onBinaryMessage?.let { wsClient.onBinaryMessage.add(it) }
        onStringMessage?.let { wsClient.onStringMessage.add(it) }
        onAnyMessage?.let { wsClient.onAnyMessage.add(it) }
        wsClient.onAnyMessage.add {
            Sender.send(Message(MessageType.DEBUG, "", ""))
        }

        wsClient.onStringMessage.add { rawMessage ->
            val message = rawMessage.toWsMessage()
            val destination = message.destination
            val topic = WsRouter[destination]
            if (topic != null) {
                when (topic) {
                    is PluginTopic -> {
                        val pluginMetadata = PluginMetadata.serializer() parse message.data
                        binaryTopicsStorage[pluginMetadata] = topic
                    }
                    is InfoTopic -> {
                        topic.run(message.data)
                        Sender.send(
                            Message(
                                MessageType.MESSAGE_DELIVERED,
                                destination
                            )
                        )
                    }
                    is GenericTopic<*> -> {
                        topic.deserializeAndRun(message.data)
                        Sender.send(
                            Message(
                                MessageType.MESSAGE_DELIVERED,
                                destination
                            )
                        )
                    }
                }
            } else {
                wsLogger.warn { "topic with name '$destination' didn't register" }
            }

        }

        wsClient.onBinaryMessage.add { rawFile ->
            val checkSum = rawFile.sha1().toHexString()
            wsLogger.info { "got '$checkSum' file to binary channel" }
            val metadata = binaryTopicsStorage.keys.first { it.checkSum == checkSum }
            binaryTopicsStorage.remove(metadata)?.block?.invoke(metadata, rawFile) ?: run {
                wsLogger.warn { "can't find corresponded config fo'$checkSum' hash" }
            }
            Sender.send(Message(MessageType.MESSAGE_DELIVERED, "/agent/load"))
        }

        wsClient.onError.add {
            wsLogger.error { "WS error: ${it.message}" }
        }
        wsClient.onClose.add {
            wsLogger.info { "Websocket closed" }
            wsClient.close()
            throw WsClosedException("")
        }
        attemptCounter.value = 0.freeze()
        while (true) {
            wsClient.send(msChannel.receive())
            delay(10)
        }
    }

    fun close() {
        try {
            coroutineContext.cancelChildren()
        } catch (_: Exception) {
        }

    }
}

private fun String.toWsMessage() = Message.serializer().parse(this)

fun ByteArray.toHexString() = asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }
