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
package com.epam.drill.core

import com.epam.drill.common.*
import com.epam.drill.plugin.*
import com.epam.drill.plugin.api.processing.*
import kotlinx.cinterop.*
import kotlin.native.concurrent.*

typealias sendFun = CPointer<CFunction<(pluginId: CPointer<ByteVar>, content: CPointer<ByteVar>) -> Unit>>

val defaultFun: suspend () -> ByteArray = { byteArrayOf() }

private val drillRequestCallback_ = AtomicReference<() -> DrillRequest?>({ null }.freeze()).freeze()
private val sessionStorageCallback = AtomicReference({ _: DrillRequest -> Unit }.freeze()).freeze()
private val closeSessionCallback = AtomicReference({ }.freeze()).freeze()
private val loadPluginCallback = AtomicReference({ _: String, _: PluginMetadata -> Unit }.freeze()).freeze()
private val getClassesByConfigCallback = AtomicReference(defaultFun.freeze()).freeze()
private val setPackagesPrefixesCallback = AtomicReference({ _: PackagesPrefixes -> Unit }.freeze()).freeze()
val pluginNativeFunction: (CPointed?, String, sendFun) -> NativePart? = { _, _, _ -> null }
private val nativePluginCallback = AtomicReference(pluginNativeFunction.freeze()).freeze()

var drillRequest: () -> DrillRequest?
    get() = drillRequestCallback_.value
    set(value) {
        drillRequestCallback_.value = value.freeze()
    }

var sessionStorage: (DrillRequest) -> Unit
    get() = sessionStorageCallback.value
    set(value) {
        sessionStorageCallback.value = value.freeze()
    }

var closeSession: () -> Unit
    get() = closeSessionCallback.value
    set(value) {
        closeSessionCallback.value = value.freeze()
    }

var loadPlugin: (String, PluginMetadata) -> Unit
    get() = loadPluginCallback.value
    set(value) {
        loadPluginCallback.value = value.freeze()
    }

var getClassesByConfig: suspend () -> ByteArray
    get() = getClassesByConfigCallback.value
    set(value) {
        getClassesByConfigCallback.value = value.freeze()
    }

var setPackagesPrefixes: (PackagesPrefixes) -> Unit
    get() = setPackagesPrefixesCallback.value
    set(value) {
        setPackagesPrefixesCallback.value = value.freeze()
    }

var nativePlugin: (CPointed?, String, sendFun) -> NativePart?
    get() = nativePluginCallback.value
    set(value) {
        nativePluginCallback.value = value.freeze()
    }
