@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.epam.drill.endpoints.plugin

import com.epam.drill.cache.*
import com.epam.drill.cache.type.*
import com.epam.drill.common.*
import com.epam.drill.core.*
import com.epam.drill.endpoints.*
import com.epam.drill.plugin.api.end.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*
import java.util.*
import java.util.concurrent.*

private val logger = KotlinLogging.logger {}

class DrillPluginWs(override val kodein: Kodein) : KodeinAware, Sender {

    private val app: Application by instance()
    private val cacheService: CacheService by instance()
    private val eventStorage: Cache<String, String> by cacheService
    private val sessionStorage: ConcurrentMap<String, MutableSet<SessionData>> = ConcurrentHashMap()
    private val agentManager: AgentManager by instance()

    override suspend fun send(agentInfo: AgentInfo, destination: String, message: String) {
        val id = "${agentInfo.id}:$destination:${agentInfo.buildVersion}"
        if (message.isEmpty()) {
            eventStorage.remove(id)
        } else {
            val messageForSend = WsMessage.serializer() stringify WsMessage(WsMessageType.MESSAGE, destination, message)
            logger.debug { "send data to $id destination" }
            eventStorage[id] = messageForSend

            sessionStorage[destination]?.let { sessionDataSet ->
                sessionDataSet.forEach { data ->
                    try {
                        if (data.subscribeInfo == SubscribeInfo(agentInfo.id, agentInfo.buildVersion))
                            data.session.send(Frame.Text(messageForSend))
                    } catch (ex: Exception) {
                        sessionDataSet.removeIf { it == data }
                    }
                }
            }
        }
    }

    init {
        app.routing {
            authWebSocket("/ws/drill-plugin-socket") {
                incoming.consumeEach { frame ->
                    when (frame) {
                        is Frame.Text -> {
                            val event = WsMessage.serializer() parse frame.readText()
                            when (event.type) {
                                WsMessageType.SUBSCRIBE -> {
                                    val subscribeInfo = SubscribeInfo.serializer() parse event.message

                                    saveSession(event, subscribeInfo)
                                    val buildVersion = subscribeInfo.buildVersion

                                    val message =
                                        eventStorage[
                                                subscribeInfo.agentId + ":" +
                                                        event.destination + ":" +
                                                        if (buildVersion.isNullOrEmpty()) {
                                                            agentManager.buildVersionByAgentId(subscribeInfo.agentId)
                                                        } else buildVersion
                                        ]

                                    if (message.isNullOrEmpty()) {
                                        this.send(
                                            (WsMessage.serializer() stringify
                                                    WsMessage(
                                                        WsMessageType.MESSAGE,
                                                        event.destination,
                                                        ""
                                                    )).textFrame()
                                        )
                                    } else this.send(Frame.Text(message))

                                }
                                WsMessageType.UNSUBSCRIBE -> {
                                    sessionStorage[event.destination]?.let {
                                        it.removeIf { data -> data.session == this }
                                    }
                                }
                                else -> {
                                    close(RuntimeException("Event '${event.type}' is not implemented yet"))
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    private fun DefaultWebSocketServerSession.saveSession(event: WsMessage, subscribeInfo: SubscribeInfo) {
        val sessionSet = sessionStorage.getOrPut(event.destination) {
            Collections.newSetFromMap(ConcurrentHashMap())
        }
        sessionSet.add(SessionData(this, subscribeInfo))
    }

}

@Serializable
data class SubscribeInfo(
    val agentId: String,
    val buildVersion: String? = null
)

data class SessionData(
    val session: DefaultWebSocketServerSession,
    val subscribeInfo: SubscribeInfo
) {
    override fun equals(other: Any?) = other is SessionData && other.session == session

    override fun hashCode() = session.hashCode()
}
