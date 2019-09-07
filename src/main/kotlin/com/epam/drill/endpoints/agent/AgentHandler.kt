@file:Suppress("EXPERIMENTAL_API_USAGE", "UNCHECKED_CAST")

package com.epam.drill.endpoints.agent

import com.epam.drill.common.*
import com.epam.drill.common.ws.*
import com.epam.drill.endpoints.*
import com.epam.drill.endpoints.plugin.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*

private val logger = KotlinLogging.logger {}

class AgentHandler(override val kodein: Kodein) : KodeinAware {
    private val app: Application by instance()
    private val agentManager: AgentManager by instance()
    private val pd: PluginDispatcher by kodein.instance()


    init {
        app.routing {
            webSocket("/agent/attach") {

                val agentConfig = Cbor.loads(
                    AgentConfig.serializer(), call.request.headers[AgentConfigParam]!!
                )

                val agentInfo = agentManager.agentConfiguration(agentConfig.id, agentConfig.buildVersion)
                agentInfo.ipAddress = call.request.local.remoteHost

                agentManager.put(agentInfo, this)

                logger.info { "Agent WS is connected. Client's address is ${call.request.local.remoteHost}" }

                if (call.request.headers[NeedSyncParam]!!.toBoolean()) {
                    agentManager.updateAgentConfig(agentInfo)
                }

                val sslPort = app.environment.config
                    .config("ktor")
                    .config("deployment")
                    .property("sslPort")
                    .getString()

                send(
                    Frame.Text(
                        Message.serializer() stringify Message(
                            MessageType.MESSAGE,
                            "/agent/config",
                            ServiceConfig.serializer() stringify ServiceConfig(sslPort)
                        )
                    )
                )

                try {
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            val message = Message.serializer() parse  frame.readText()
                            when (message.type) {
                                MessageType.PLUGIN_DATA -> {
                                    logger.debug(message.message)
                                    pd.processPluginData(message.message, agentInfo)
                                }
                                MessageType.MESSAGE -> {
                                    //TODO spinner hack
                                    if(message.message == "OK") {
                                        agentInfo.status = AgentStatus.READY
                                        agentManager.update()
                                        agentManager.singleUpdate(agentInfo.id)
                                    }

//                                    send(frame)
                                }
                                else -> {
                                }
                            }

                        }
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                } finally {
                    logger.info { "agentDisconnected ${agentInfo.id} disconnected!" }
                    agentManager.remove(agentInfo)
                }

            }
        }
    }
}