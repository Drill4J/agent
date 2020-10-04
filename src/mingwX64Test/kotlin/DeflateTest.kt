package com.epam.drill.zlib

import kotlin.test.*

class DeflateTest {

    @Test
    fun shouldCompressAndDecompress() {
        val input = ByteArray(100)
        val compressed = Zstd.encode(input)
        assertTrue { compressed.size < input.size }
        val uncompressed = Zstd.decode(compressed)
        assertEquals(input.contentHashCode(), uncompressed.contentHashCode())
    }

    @Test
    fun shouldCompressAndDecompressLargeContent() {
        val buffSize = 10000000 //~ 10 mb
        val input = ByteArray(buffSize)
        val compressed = Zstd.encode(input)
        assertTrue { compressed.size < input.size }
        val uncompressed = Zstd.decode(compressed)
        assertEquals(input.contentHashCode(), uncompressed.contentHashCode())
    }

}
