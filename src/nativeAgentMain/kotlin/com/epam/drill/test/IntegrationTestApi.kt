@file:Suppress("unused")

package com.epam.drill.test

import com.epam.drill.common.AgentConfig
import com.epam.drill.common.PluginBean
import com.epam.drill.core.exec
import com.epam.drill.core.plugin.loader.loadPlugin
import com.epam.drill.jvmapi.toKString
import com.soywiz.korio.file.std.localVfs
import jvmapi.JNIEnv
import jvmapi.jobject
import jvmapi.jstring
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.runBlocking


@Suppress("UNUSED_PARAMETER")//this only for integrationTests
@CName("Java_com_epam_drill_test_IntegrationTestApi_LoadPlugin")
fun LoadPug(env: JNIEnv, thiz: jobject, path: jstring) = memScoped {
    runBlocking {
        exec {
            pl["coverage"] = PluginBean(
                id = "coverage",
                config = "{\"pathPrefixes\": [\"org\"], \"message\": \"hello from default plugin config... This is 'plugin_config.json file\"}"
            )
        }

        loadPlugin(localVfs(path.toKString()!!))
    }
}

@Suppress("UNUSED_PARAMETER")//this only for integrationTests
@CName("Java_com_epam_drill_test_IntegrationTestApi_setAdminUrl")
fun setAdminUrl(env: JNIEnv, thiz: jobject, path: jstring) {
    exec {
        agentConfig = AgentConfig("test", path.toKString()!!, "")
    }
}