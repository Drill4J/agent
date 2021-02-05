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
