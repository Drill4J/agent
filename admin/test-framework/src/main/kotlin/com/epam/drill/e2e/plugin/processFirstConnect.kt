package com.epam.drill.e2e.plugin

import com.epam.drill.builds.*
import com.epam.drill.common.*
import com.epam.drill.e2e.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.apache.bcel.classfile.*
import java.io.*
import kotlin.test.*

inline fun <reified PS : PluginStreams> AdminTest.processFirstConnect(
    build: Build,
    ui: AdminUiChannels,
    ag: AgentWrap,
    pluginId: String,
    uiStreamDebug: Boolean,
    agentStreamDebug: Boolean,
    pluginMeta: PluginMetadata,
    noinline connect: suspend PluginTestContext.(Any, Any) -> Unit,
    globLaunch: Job
) {
    engine.handleWebSocketConversation("/ws/drill-plugin-socket?token=${globToken}") { uiIncoming, ut ->
        val classes = File("./build/classes/java/${build.name}")
            .walkTopDown()
            .filter { it.extension == "class" }
            .toList()
            .toTypedArray()
        ui.getAgent()

        val st = PS::class.java.constructors.single().newInstance() as PluginStreams
        val pluginTestInfo = PluginTestContext(
            ag.id,
            pluginId,
            ag.buildVersion,
            globToken,
            classes.size,
            engine
        )
        st.info = pluginTestInfo
        st.app = engine.application
        with(st) { queued(uiIncoming, ut, uiStreamDebug) }
        delay(50)

        engine.handleWebSocketConversation(
            "/agent/attach",
            wsRequestRequiredParams(ag)
        ) { inp, out ->
            val apply =
                Agent(
                    engine.application,
                    ag.id,
                    inp,
                    OutsSock(out, agentStreamDebug),
                    agentStreamDebug
                ).apply { queued() }
            apply.getServiceConfig()?.sslPort
            register(ag.id)
            ui.getAgent()
            ui.getAgent()
            apply.`get-set-packages-prefixes`()
            val bcelClasses = classes.map {
                it.inputStream().use { fs -> ClassParser(fs, "").parse() }
            }
            apply.`get-load-classes-data`(*bcelClasses.toTypedArray())
            val classMap: Map<String, ByteArray> = bcelClasses.associate {
                it.className.replace(".", "/") to it.bytes
            }
            assertEquals(ui.getAgent()?.status, AgentStatus.ONLINE)

            application.launch(Dispatchers.IO) {
                assertEquals(
                    HttpStatusCode.OK,
                    addPlugin(ag.id, PluginId(pluginId)).first,
                    "CAN'T ADD THE PLUGIN"
                )
            }
            loadPlugin(
                apply,
                ag,
                classMap,
                pluginId,
                agentStreamDebug,
                out,
                st,
                pluginTestInfo,
                pluginMeta,
                build
            )
            ui.getAgent()
            ui.getAgent()
            connect(pluginTestInfo, st, build)
            while (globLaunch.isActive)
                delay(100)
        }
    }
}