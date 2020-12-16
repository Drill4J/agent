package com.epam.drill.ws

import com.epam.drill.transport.*
import kotlin.native.concurrent.*

@SharedImmutable
val ws = AtomicReference<WSClient?>(null).freeze()
