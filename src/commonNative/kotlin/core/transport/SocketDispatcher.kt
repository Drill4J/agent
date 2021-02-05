/**
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.core.transport

import com.epam.drill.*
import com.epam.drill.core.*
import com.epam.drill.hook.io.tcp.*
import com.epam.drill.interceptor.*
import com.epam.drill.logger.*
import com.epam.drill.logger.api.*
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
    readHeaders.value = { rawHeaders: Map<ByteArray, ByteArray> ->
        val headers = rawHeaders.entries.associate { (k, v) ->
            k.decodeToString().toLowerCase() to v.decodeToString()
        }
        if (Logging.logLevel <= LogLevel.DEBUG) {
            val drillHeaders = headers.filterKeys { it.startsWith("drill-") }
            if (drillHeaders.any()) {
                logger.debug { "Drill headers: $drillHeaders" }
            }
        }
        val sessionId = headers[requestPattern] ?: headers["drill-session-id"]
        sessionId?.let { DrillRequest(it, headers) }?.also {
            drillRequest = it
            sessionStorage(it)
        }
        Unit
    }.freeze()
    writeCallback.value = { _: ByteArray ->
        closeSession()
        drillRequest = null
    }.freeze()

}

fun idHeaderPairFromConfig(): Pair<String, String> =
    when (val groupId = agentConfig.serviceGroupId) {
        "" -> "drill-agent-id" to agentConfig.id
        else -> "drill-group-id" to groupId
    }


@ThreadLocal
var drillRequest: DrillRequest? = null

fun retrieveAdminUrl(): String {
    return if (secureAdminAddress != null) {
        secureAdminAddress?.toUrlString(false).toString()
    } else adminAddress?.toUrlString(false).toString()

}
