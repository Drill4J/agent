package com.epam.drill.plugin.api.processing

const val initPlugin = "initPlugin"

//TODO move native plugin into a separate lib
abstract class NativePart : Switchable, Lifecycle
