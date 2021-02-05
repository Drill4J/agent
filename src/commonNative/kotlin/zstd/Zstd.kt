/**
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.zstd

import com.epam.drill.zstd.gen.*
import kotlinx.cinterop.*

object Zstd {

    fun decompress(input: ByteArray) = memScoped {
        pinIO(input) { inp ->
            val initialSize = ZSTD_getFrameContentSize(inp, input.size.convert())
            ByteArray(initialSize.toInt()).apply {
                usePinned {
                    ZSTD_decompress(it.addressOf(0), initialSize, inp, input.size.convert())
                }
            }
        }
    }

    fun compress(input: ByteArray) = memScoped {
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
