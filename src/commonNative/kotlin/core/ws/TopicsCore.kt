package com.epam.drill.core.ws

import com.epam.drill.api.*
import com.epam.drill.common.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*
import kotlin.native.concurrent.*


@SharedImmutable
private val topicContext = newSingleThreadContext("topic's processor")


@SharedImmutable
val mapper = atomic(mapOf<String, Topic>().freeze()).freeze()

@ThreadLocal
object WsRouter {

    operator fun invoke(alotoftopics: WsRouter.() -> Unit) {
        alotoftopics(this)
    }


    @Suppress("ClassName")
    open class inners(open val destination: String) {

        @Suppress("unused")
        fun withPluginTopic(block: suspend (message: PluginMetadata, file: ByteArray) -> Unit): PluginTopic {
            val fileTopic = PluginTopic(destination, block)
            val mapping: Pair<String, Topic> = destination to fileTopic
            mapper.update { it + mapping }
            return fileTopic
        }

    }

    operator fun get(topic: String): Topic? {
        return mapper.value[topic]
    }

}

@Suppress("unused")
inline fun <reified TopicUrl : Any> WsRouter.topic(): WsRouter.inners {
    val serializer = TopicUrl::class.topicUrl()
    return WsRouter.inners(serializer)
}

@Suppress("unused")
fun WsRouter.rawTopic(path: String): WsRouter.inners {
    return WsRouter.inners(path)
}

@Suppress("unused")
inline fun <reified TopicUrl : Any, reified Generic : Any> WsRouter.topic(noinline block: suspend (Generic) -> Unit) {
    val destination = TopicUrl::class.topicUrl()
    val infoTopic = GenericTopic(destination, Generic::class.serializer(), block)
    val mapping: Pair<String, Topic> = destination to infoTopic
    mapper.update { it + mapping }
}

@Suppress("unused")
inline fun <reified Generic : Any> WsRouter.rawTopic(destination: String, noinline block: suspend (Generic) -> Unit) {
    val infoTopic = GenericTopic(destination, Generic::class.serializer(), block)
    val mapping: Pair<String, Topic> = destination to infoTopic
    mapper.update { it + mapping }
}

@Suppress("unused")
inline fun <reified TopicUrl : Any> WsRouter.topic(noinline block: suspend (String) -> Unit) {
    if (TopicUrl::class.serializer()
            .descriptor
            .annotations
            .filterIsInstance<com.epam.drill.api.Topic>().isEmpty()
    ) return

    val destination = TopicUrl::class.topicUrl()
    val infoTopic = InfoTopic(destination, block)
    val mapping: Pair<String, Topic> = destination to infoTopic
    mapper.update { it + mapping }
}

@Suppress("unused")
fun WsRouter.rawTopic(destination: String, block: suspend (String) -> Unit) {
    val infoTopic = InfoTopic(destination, block)
    val mapping: Pair<String, Topic> = destination to infoTopic
    mapper.update { it + mapping }
}

open class Topic(open val destination: String)

class GenericTopic<T>(
    override val destination: String,
    private val deserializer: KSerializer<T>,
    val block: suspend (T) -> Unit
) : Topic(destination) {
    suspend fun deserializeAndRun(message: ByteArray) = withContext(topicContext) {
        block(ProtoBuf.decodeFromByteArray(deserializer, message))
    }
}

class InfoTopic(
    override val destination: String,
    private val block: suspend (String) -> Unit
) : Topic(destination) {

    suspend fun run(message: ByteArray) = withContext(topicContext) {
        block(message.decodeToString())
    }
}


open class PluginTopic(
    override val destination: String,
    @Suppress("unused") open val block: suspend (message: PluginMetadata, file: ByteArray) -> Unit
) : Topic(destination)

