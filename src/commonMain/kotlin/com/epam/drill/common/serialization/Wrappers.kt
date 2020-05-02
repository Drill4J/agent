package com.epam.drill.common.serialization

import kotlinx.serialization.*

@Serializable
class ByteArrayWrapper(val bytes: ByteArray)

@Serializable
class ByteArrayListWrapper(val bytesList: List<ByteArray>)
