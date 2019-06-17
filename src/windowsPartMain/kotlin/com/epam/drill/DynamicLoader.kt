package com.epam.drill

import com.epam.drill.plugin.api.processing.NativePart
import com.epam.drill.plugin.api.processing.initPlugin
import jvmapi.JavaVMVar
import jvmapi.gdata
import jvmapi.gjavaVMGlob
import jvmapi.jvmtiEventCallbacks
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import platform.windows.GetProcAddress
import platform.windows.LoadLibrary


fun loadNativePlugin(
    pluginId: String,
    path: String,
    sender: CPointer<CFunction<(pluginId: CPointer<ByteVar>, message: CPointer<ByteVar>) -> Unit>>
) = memScoped {
    var pluginInstance: NativePart<*>? = null
    val allocArray = path.replace("/", "\\").toLPCWSTR(this)
    val hModule = LoadLibrary!!(allocArray.pointed.ptr)
    if (hModule != null) {
        val initPlugin = GetProcAddress(hModule, initPlugin)

        val callbacks: jvmtiEventCallbacks? = gjavaVMGlob?.pointed?.callbackss
        val reinterpret =
            initPlugin?.reinterpret<CFunction<(CPointer<ByteVar>, CPointer<jvmapi.jvmtiEnvVar>?, CPointer<JavaVMVar>?, CPointer<jvmtiEventCallbacks>?, CPointer<CFunction<(pluginId: CPointer<ByteVar>, message: CPointer<ByteVar>) -> Unit>>) -> COpaquePointer>>()
        val id = pluginId.cstr.getPointer(this)
        val jvmti = gdata?.pointed?.jvmti
        val jvm = gjavaVMGlob?.pointed?.jvm
        val clb = callbacks?.ptr
        pluginInstance =
            reinterpret?.invoke(
                id,
                jvmti,
                jvm,
                clb,
                sender
            )?.asStableRef<NativePart<*>>()?.get()
    }
    pluginInstance
}

private fun String.toLPCWSTR(ms: MemScope): CArrayPointer<UShortVar> {
    val length = this.length
    val allocArray = ms.allocArray<UShortVar>(length.toLong())
    for (i in 0 until length) {
        allocArray[i] = this[i].toShort().toUShort()
    }
    return allocArray
}