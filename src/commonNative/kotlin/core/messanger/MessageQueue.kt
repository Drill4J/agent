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
package com.epam.drill.core.messanger

import com.epam.drill.common.*
import com.epam.drill.core.ws.*
import com.epam.drill.plugin.api.message.*
import kotlinx.cinterop.*

fun sendNativeMessage(pluginId: CPointer<ByteVar>, content: CPointer<ByteVar>) {
    sendMessage(pluginId.toKString(), content.toKString())
}

fun sendMessage(pluginId: String, content: String) {
    Sender.send(
        Message(
            type = MessageType.PLUGIN_DATA,
            destination = "",
            data = (MessageWrapper.serializer() stringify MessageWrapper(
                pluginId,
                DrillMessage(content = content)
            )).encodeToByteArray()
        )
    )
}
