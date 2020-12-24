package com.epam.drill.plugin

import kotlinx.serialization.*

@Serializable
class DrillRequest(
    val drillSessionId: String,
    val headers: Map<String, String> = emptyMap()
)
