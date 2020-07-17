package com.epam.drill.zlib

import kotlinx.cinterop.*
import platform.zlib.*

object Deflate {
    private const val CHUNK = 1024

    fun decode(
        input: ByteArray,
        bufferSize: Int = CHUNK,
        nowrap: Boolean = false,
        windowBits: Int = 15
    ) = memScoped<ByteArray> {
        val strm: z_stream = initDeflateStream()
        var outArray = byteArrayOf()
        var hv = 0
        try {
            pinIO(input) { inp ->
                var ret = inflateInit2_(
                    strm.ptr,
                    if (nowrap) -windowBits else windowBits,
                    zlibVersion()?.toKString(),
                    sizeOf<z_stream>().toInt()
                )
                if (ret != Z_OK) error("Invalid inflateInit2_")

                do {
                    strm.avail_in = input.size.convert()
                    if (strm.avail_in == 0u) break
                    strm.next_in = inp.reinterpret()

                    do {
                        val byteArray = ByteArray(bufferSize)
                        byteArray.usePinned {
                            strm.avail_out = bufferSize.convert()
                            strm.next_out = it.addressOf(0).reinterpret()
                            ret = inflate(strm.ptr, Z_NO_FLUSH)
                            assert(ret != Z_STREAM_ERROR)
                            when (ret) {
                                Z_NEED_DICT -> ret = Z_DATA_ERROR
                                Z_DATA_ERROR -> error("data error")
                                Z_MEM_ERROR -> error("mem error")
                            }
                            hv += bufferSize - strm.avail_out.toInt()
                            outArray += byteArray
                        }
                    } while (strm.avail_out == 0u)
                } while (ret != Z_STREAM_END)
                return outArray.copyOf(hv)
            }
        } finally {
            inflateEnd(strm.ptr)
        }
    }


    private fun MemScope.initDeflateStream(): z_stream {
        val strm: z_stream = alloc()
        strm.zalloc = null
        strm.zfree = null
        strm.opaque = null
        strm.avail_in = 0u
        strm.next_in = null
        return strm
    }

    private const val Z_DEFLATED = 8
    private const val MAX_MEM_LEVEL = 9
    private const val Z_DEFAULT_STRATEGY = 0

    fun encode(
        input: ByteArray,
        bufferSize: Int = CHUNK,
        nowrap: Boolean = false,
        windowBits: Int = 15
    ) = memScoped {
        val strm: z_stream = initDeflateStream()
        var outArray = byteArrayOf()
        var hv = 0
        try {
            pinIO(input) { inp ->
                var ret: Int = deflateInit2_(
                    strm.ptr,
                    6,
                    Z_DEFLATED,
                    if (nowrap) -windowBits else windowBits,
                    MAX_MEM_LEVEL,
                    Z_DEFAULT_STRATEGY,
                    zlibVersion()?.toKString(),
                    sizeOf<z_stream>().toInt()
                )
                if (ret != Z_OK) error("Invalid deflateInit2_")

                strm.avail_in = input.size.convert()
                strm.next_in = inp.reinterpret()

                do {
                    val byteArray = ByteArray(bufferSize)
                    byteArray.usePinned {
                        strm.avail_out = bufferSize.convert()
                        strm.next_out = it.addressOf(0).reinterpret()
                        ret = deflate(strm.ptr, Z_FINISH)
                        assert(ret != Z_STREAM_ERROR)
                        when (ret) {
                            Z_NEED_DICT -> ret = Z_DATA_ERROR
                            Z_DATA_ERROR -> error("data error")
                            Z_MEM_ERROR -> error("mem error")
                        }
                        hv += bufferSize - strm.avail_out.toInt()
                        outArray += byteArray
                    }
                } while (strm.avail_out == 0u)
                assert(strm.avail_in == 0u)
                outArray.copyOf(hv)
            }
        } finally {
            deflateEnd(strm.ptr)
        }
    }

    private inline fun <R> pinIO(
        input: ByteArray,
        block: (CPointer<ByteVar>) -> R
    ): R {
        input.usePinned { _inp ->
            return block(_inp.addressOf(0))
        }
    }
}
