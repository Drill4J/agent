package com.epam.drill.zlib

import com.epam.drill.zstd.*
import kotlin.test.*

class DeflateTest {

    @Test
    fun shouldCompressAndDecompress() {
        val input = ByteArray(100)
        val compressed = Zstd.compress(input)
        assertTrue { compressed.size < input.size }
        val uncompressed = Zstd.decompress(compressed)
        assertEquals(input.contentHashCode(), uncompressed.contentHashCode())
    }

    @Test
    fun shouldCompressAndDecompressLargeContent() {
        val buffSize = 10000000 //~ 10 mb
        val input = ByteArray(buffSize)
        val compressed = Zstd.compress(input)
        assertTrue { compressed.size < input.size }
        val uncompressed = Zstd.decompress(compressed)
        assertEquals(input.contentHashCode(), uncompressed.contentHashCode())
    }

}
