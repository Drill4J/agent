package com.epam.drill.core.ws

import kotlinx.coroutines.channels.*
import kotlin.native.concurrent.*

private val messageChannel = AtomicReference(Channel<ByteArray>().freeze())

val msChannel: Channel<ByteArray>
    get() = messageChannel.value
