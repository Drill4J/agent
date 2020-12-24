package com.epam.drill.plugin

import com.epam.drill.*
import com.epam.drill.common.*
import com.epam.drill.core.plugin.*
import com.epam.drill.plugin.api.processing.*
import kotlinx.collections.immutable.*

val storage: PersistentMap<String, AgentPart<*>>
    get() = pstorage


fun AgentPart<*>.actualPluginConfig() = pluginConfigById(this.id)

object PluginManager {

    fun addPlugin(plugin: AgentPart<*>) {
        addPluginToStorage(plugin)
    }

    operator fun get(id: String) = storage[id]
    operator fun get(id: Family) = storage.values.groupBy { it.actualPluginConfig().family }[id]
}
