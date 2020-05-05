package com.epam.drill.core.ws

import com.epam.drill.*
import com.epam.drill.api.dto.*
import com.epam.drill.common.*
import com.epam.drill.common.serialization.*
import com.epam.drill.common.ws.*
import com.epam.drill.core.*
import com.epam.drill.core.messanger.*
import com.epam.drill.logger.*
import com.epam.drill.plugin.*
import com.epam.drill.plugin.api.processing.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.serialization.protobuf.*
import mu.*
import kotlin.collections.set
import kotlin.native.concurrent.*

@SharedImmutable
private val tempTopicLogger = KotlinLogging.logger("tempTopicLogger")

@SharedImmutable
private val loader = Worker.start(true)

fun topicRegister() =
    WsRouter {
        WsRouter.inners("/agent/load").withPluginTopic { pluginMeta, file ->
            if (exec { pstorage[pluginMeta.id] } != null) {
                pluginMeta.sendPluginLoaded()
                tempTopicLogger.info { "Plugin '${pluginMeta.id}' is already loaded" }
                return@withPluginTopic
            }
            val pluginId = pluginMeta.id
            exec { pl[pluginId] = pluginMeta }
            loader.execute(
                TransferMode.UNSAFE,
                { pluginMeta to file }) { (plugMessage, file) ->
                tempTopicLogger.info { "try to load ${plugMessage.id} plugin" }
                val id = plugMessage.id
                exec { agentConfig.needSync = false }
                if (!plugMessage.isNative) runBlocking {
                    val path = generatePluginPath(id)
                    writeFileAsync(path, file)
                    loadPlugin(path, plugMessage)
                } else {
                    val natPlugin = generateNativePluginPath(id)

                    val dynamicLibrary = injectDynamicLibrary(natPlugin) as CPointed?

                    val loadedNativePlugin = nativePlugin(dynamicLibrary, id, staticCFunction(::sendNativeMessage))


                    loadedNativePlugin?.initPlugin()
                    loadedNativePlugin?.on()
                }
                plugMessage.sendPluginLoaded()
                tempTopicLogger.info { "$id plugin loaded" }
            }

        }

        rawTopic<LoggingConfig>("/agent/logging/update-config") { lc ->
            tempTopicLogger.info { "Agent got a logging config: $lc" }
            logConfig.value = LoggerConfig(lc.trace, lc.debug, lc.info, lc.warn).freeze()
        }

        rawTopic<ServiceConfig>("/agent/update-config") { sc ->
            tempTopicLogger.info { "Agent got a system config: $sc" }
            exec { secureAdminAddress = adminAddress.copy(scheme = "https", defaultPort = sc.sslPort.toInt()) }
        }

        rawTopic("/agent/change-header-name") { headerName ->
            tempTopicLogger.info { "Agent got a new headerMapping: $headerName" }
            exec { requestPattern = if (headerName.isEmpty()) null else headerName }
        }

        rawTopic<PackagesPrefixes>("/agent/set-packages-prefixes") { payload ->
            setPackagesPrefixes(payload)
            tempTopicLogger.info { "Agent packages prefixes have been changed" }
        }

        rawTopic("/agent/unload") { pluginId ->
            tempTopicLogger.warn { "Unload event. Plugin id is $pluginId" }
            PluginManager[pluginId]?.unload(UnloadReason.ACTION_FROM_ADMIN)
            println(
                """
                    |________________________________________________________
                    |Physical Deletion is not implemented yet.
                    |We should unload all resource e.g. classes, jars, .so/.dll
                    |Try to create custom classLoader. After this full GC.
                    |________________________________________________________
                """.trimMargin()
            )
        }
        rawTopic("/agent/load-classes-data") {
            val rawClassFiles = getClassesByConfig()
            Sender.send(Message(MessageType.START_CLASSES_TRANSFER, ""))
            rawClassFiles.chunked(150).forEach {
                Sender.send(
                    Message(
                        MessageType.CLASSES_DATA,
                        "",
                        ProtoBuf.dump(ByteArrayListWrapper.serializer(), ByteArrayListWrapper(it))
                    )
                )
            }
            Sender.send(Message(MessageType.FINISH_CLASSES_TRANSFER, ""))
            tempTopicLogger.info { "Agent's application classes processing by config triggered" }
        }

        rawTopic<PluginConfig>("/plugin/updatePluginConfig") { config ->
            tempTopicLogger.warn { "UpdatePluginConfig event: message is $config " }
            val agentPluginPart = PluginManager[config.id]
            if (agentPluginPart != null) {
                agentPluginPart.setEnabled(false)
                agentPluginPart.off()
                agentPluginPart.updateRawConfig(config)
                agentPluginPart.np?.updateRawConfig(config)
                agentPluginPart.setEnabled(true)
                agentPluginPart.on()
                tempTopicLogger.warn { "New settings for ${config.id} saved to file" }
            } else
                tempTopicLogger.warn { "Plugin ${config.id} not loaded to agent" }
        }

        rawTopic<PluginAction>("/plugin/action") { m ->
            tempTopicLogger.warn { "actionPluign event: message is ${m.message} " }
            val agentPluginPart = PluginManager[m.id]
            agentPluginPart?.doRawAction(m.message)

        }

        rawTopic<TogglePayload>("/plugin/togglePlugin") { (pluginId, forceValue) ->
            val agentPluginPart = PluginManager[pluginId]
            if (agentPluginPart == null) {
                tempTopicLogger.warn { "Plugin $pluginId not loaded to agent" }
            } else {
                tempTopicLogger.warn { "togglePlugin event: PluginId is $pluginId" }
                val newValue = forceValue ?: !agentPluginPart.isEnabled()
                agentPluginPart.setEnabled(newValue)
                if (newValue) agentPluginPart.on() else agentPluginPart.off()
            }
        }
    }

private fun PluginMetadata.sendPluginLoaded() {
    Sender.send(Message(MessageType.MESSAGE_DELIVERED, "/agent/plugin/$id/loaded"))
}

private fun generateNativePluginPath(id: String): String {
    //fixme do generate Native path
    return "$id/native_plugin.os_lib"
}

private fun generatePluginPath(id: String): String {
    val ajar = "agent-part.jar"
    val pluginsDir = "${if (tempPath.isEmpty()) exec { drillInstallationDir } else tempPath}/drill-plugins"
    doMkdir(pluginsDir)
    var pluginDir = "$pluginsDir/$id"
    doMkdir(pluginDir)
    pluginDir = "$pluginDir/${exec { agentConfig.id }}"
    doMkdir(pluginDir)
    val path = "$pluginDir/$ajar"
    return path
}
