package com.epam.drill.zlib

import com.epam.drill.zstd.gen.*
import kotlinx.cinterop.*

object Zstd {

    fun decode(input: ByteArray) = memScoped {
        pinIO(input) { inp ->
            val initialSize = ZSTD_getFrameContentSize(inp, input.size.convert())
            ByteArray(initialSize.toInt()).apply {
                usePinned {
                    ZSTD_decompress(it.addressOf(0), initialSize, inp, input.size.convert())
                }
            }
        }
    }

    fun encode(input: ByteArray) = memScoped {
        val compressedSize = ZSTD_compressBound(input.size.convert())
        pinIO(input) { inp ->
            val output = ByteArray(compressedSize.toInt())
            output.copyOf(pinIO(output) { out ->
                ZSTD_compress(out, compressedSize.convert(), inp, input.size.convert(), 1)
            }.convert())
        }

    }

    private inline fun <R> pinIO(
        input: ByteArray,
        block: (CPointer<ByteVar>) -> R
    ): R = input.usePinned { _inp ->
        block(_inp.addressOf(0))
    }

}
