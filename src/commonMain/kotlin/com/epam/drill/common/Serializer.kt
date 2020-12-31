package com.epam.drill.common

import kotlinx.serialization.*
import kotlinx.serialization.json.*

infix fun <T> KSerializer<T>.parse(rawData: String) = Json.decodeFromString(this, rawData)

infix fun <T> KSerializer<T>.stringify(rawData: T) = Json.encodeToString(this, rawData)
