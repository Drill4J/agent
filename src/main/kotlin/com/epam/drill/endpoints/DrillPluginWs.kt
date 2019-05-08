@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.epam.drill.endpoints

import com.epam.drill.agentmanager.AgentStorage
import com.epam.drill.agentmanager.self
import com.epam.drill.common.AgentInfo
import com.epam.drill.common.Message
import com.epam.drill.common.MessageType
import com.epam.drill.plugin.api.end.WsService
import com.epam.drill.storage.MongoClient
import com.google.gson.Gson
import io.ktor.application.Application
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.close
import io.ktor.http.cio.websocket.readText
import io.ktor.routing.routing
import io.ktor.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.webSocket
import kotlinx.coroutines.channels.consumeEach
import org.bson.BsonMaximumSizeExceededException
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.generic.instance
import org.litote.kmongo.deleteMany
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap


class DrillPluginWs(override val kodein: Kodein) : KodeinAware, WsService {
    private val logger = LoggerFactory.getLogger(DrillPluginWs::class.java)
    private val app: Application by instance()
    private val mc: MongoClient by instance()
    private val sessionStorage: ConcurrentMap<String, MutableSet<DefaultWebSocketServerSession>> = ConcurrentHashMap()
    private val agentStorage: AgentStorage by instance()

    override fun getPlWsSession(): Set<String> {
        return sessionStorage.keys
    }

    override suspend fun convertAndSend(agentInfo: AgentInfo, destination: String, message: String) {
        val messageForSend = Message(MessageType.MESSAGE, destination, message)

        val collection = mc.storage<Message>(
            agentInfo.id,
            destination + ":" + agentInfo.buildVersion
        )
        collection.deleteMany()
        try {
            collection.insertOne(messageForSend)
        } catch (e: BsonMaximumSizeExceededException) {
            println("payload is too long")
        }
        sessionStorage[destination]?.let { sessionSet -> 
            for (session in sessionSet) {
                try {
                    session.send(Frame.Text(Gson().toJson(messageForSend)))
                } catch (ex: Exception) {
                    sessionSet.remove(session)
                }
            }
        }
    }

    init {
        app.routing {
            webSocket("/ws/drill-plugin-socket") {
                incoming.consumeEach { frame ->
                    when (frame) {
                        is Frame.Text -> {
                            val event = Message::class fromJson frame.readText()
                                ?: closeConnection(CloseReason.Codes.PROTOCOL_ERROR, "wrong input")
                            when (event.type) {
                                MessageType.SUBSCRIBE -> {
                                    val subscribeInfo = SubscribeInfo::class fromJson event.message ?: closeConnection(
                                        CloseReason.Codes.PROTOCOL_ERROR,
                                        "wrong subs info"
                                    )
                                    saveSession(event)
                                    val objects = mc.storage<Message>(
                                        subscribeInfo.agentId,
                                        event.destination + ":" + (subscribeInfo.buildVersion ?: agentStorage.self(
                                            subscribeInfo.agentId
                                        )?.buildVersion)
                                    )
                                        .find()
                                        .iterator()
                                    if (objects.hasNext())
                                        for (ogs in objects)
                                            this.send(ogs.textFrame())
                                    else {
                                        this.send(
                                            Message(
                                                MessageType.MESSAGE,
                                                event.destination,
                                                ""
                                            ).textFrame()
                                        )
                                    }

                                }
                                MessageType.UNSUBSCRIBE -> {
                                    sessionStorage[event.destination]?.let {
                                        it.removeIf { ses -> ses == this }
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

    private suspend fun DefaultWebSocketServerSession.closeConnection(
        reason: CloseReason.Codes,
        message: String
    ): Nothing {
        this.close(CloseReason(reason, message))
        throw java.lang.RuntimeException()
    }

    private fun DefaultWebSocketServerSession.saveSession(event: Message) {
        val sessionSet = sessionStorage.getOrPut(event.destination) { 
            Collections.newSetFromMap(ConcurrentHashMap())
        }
        sessionSet.add(this)
    }

}

data class SubscribeInfo(val agentId: String, val buildVersion: String? = null)
