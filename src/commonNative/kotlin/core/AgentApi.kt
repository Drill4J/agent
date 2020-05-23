package com.epam.drill.core

import com.epam.drill.common.*
import com.epam.drill.plugin.*
import com.epam.drill.plugin.api.processing.*
import kotlinx.cinterop.*
import kotlin.native.concurrent.*

typealias sendFun = CPointer<CFunction<(pluginId: CPointer<ByteVar>, content: CPointer<ByteVar>) -> Unit>>

val defaultFun: suspend () -> List<ByteArray> = { listOf() }

private val drillRequestCallback_ = AtomicReference<() -> DrillRequest?>({ null }.freeze()).freeze()
private val sessionStorageCallback = AtomicReference({ _: DrillRequest -> Unit }.freeze()).freeze()
private val loadPluginCallback = AtomicReference({ _: String, _: PluginMetadata -> Unit }.freeze()).freeze()
private val getClassesByConfigCallback = AtomicReference(defaultFun.freeze()).freeze()
private val setPackagesPrefixesCallback = AtomicReference({ _: PackagesPrefixes -> Unit }.freeze()).freeze()
val pluginNativeFunction: (CPointed?, String, sendFun) -> NativePart<*>? = { _, _, _ -> null }
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

var loadPlugin: (String, PluginMetadata) -> Unit
    get() = loadPluginCallback.value
    set(value) {
        loadPluginCallback.value = value.freeze()
    }

var getClassesByConfig: suspend () -> List<ByteArray>
    get() = getClassesByConfigCallback.value
    set(value) {
        getClassesByConfigCallback.value = value.freeze()
    }

var setPackagesPrefixes: (PackagesPrefixes) -> Unit
    get() = setPackagesPrefixesCallback.value
    set(value) {
        setPackagesPrefixesCallback.value = value.freeze()
    }

var nativePlugin: (CPointed?, String, sendFun) -> NativePart<*>?
    get() = nativePluginCallback.value
    set(value) {
        nativePluginCallback.value = value.freeze()
    }