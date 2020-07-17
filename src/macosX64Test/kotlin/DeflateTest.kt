package com.epam.drill.zlib

import kotlin.test.*

class DeflateTest {

    @Test
    fun shouldCompressAndDecompress() {
        val input = ByteArray(100)
        val compressed = Deflate.encode(input)
        assertTrue { compressed.size < input.size }
        val uncompressed = Deflate.decode(compressed)
        assertEquals(input.contentHashCode(), uncompressed.contentHashCode())
    }

    @Test
    fun shouldCompressAndDecompressLargeContent() {
        val buffSize = 10000000 //~ 10 mb
        val input = ByteArray(buffSize)
        val compressed = Deflate.encode(input, buffSize)
        assertTrue { compressed.size < input.size }
        val uncompressed = Deflate.decode(compressed, buffSize)
        assertEquals(input.contentHashCode(), uncompressed.contentHashCode())
    }

}
