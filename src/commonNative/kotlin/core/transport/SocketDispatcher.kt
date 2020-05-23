package com.epam.drill.core.transport

import com.epam.drill.*
import com.epam.drill.core.*
import com.epam.drill.hook.io.tcp.*
import com.epam.drill.interceptor.*
import com.epam.drill.plugin.*
import kotlin.native.concurrent.*

fun configureHttp() {
    configureHttpInterceptor()
    injectedHeaders.value = {
        val idHeaderPair = idHeaderPairFromConfig()
        val adminUrl = retrieveAdminUrl()
        mapOf(
            idHeaderPair,
            "drill-admin-url" to adminUrl
        ) + (drillRequest()?.headers?.filterKeys { it.startsWith("drill-") } ?: mapOf())

    }.freeze()
    readHeaders.value = { it: Map<ByteArray, ByteArray> ->
        val headers = it.entries.associate { it.key.decodeToString() to it.value.decodeToString() }
        headers[exec { requestPattern }] ?: headers["drill-session-id"]?.let {
            val lDrillRequest = DrillRequest(it, headers)
            drillRequest = lDrillRequest
            sessionStorage(lDrillRequest)
        }
        Unit
    }.freeze()
    writeCallback.value = { _: ByteArray -> }.freeze()

}

private fun idHeaderPairFromConfig(): Pair<String, String> = exec {
    when (val groupId = agentConfig.serviceGroupId) {
        "" -> "drill-agent-id" to agentConfig.id
        else -> "drill-group-id" to groupId
    }
}

@ThreadLocal
var drillRequest: DrillRequest? = null

private fun retrieveAdminUrl(): String {
    return exec {
        if (::secureAdminAddress.isInitialized) {
            secureAdminAddress.toUrlString(false)
        } else adminAddress.toUrlString(false)
    }.toString()
}