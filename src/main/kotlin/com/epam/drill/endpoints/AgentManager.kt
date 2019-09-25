package com.epam.drill.endpoints

import com.epam.drill.agentmanager.AgentInfoWebSocketSingle
import com.epam.drill.common.*
import com.epam.drill.dataclasses.AgentBuildVersion
import com.epam.drill.endpoints.agent.AgentWsSession
import com.epam.drill.endpoints.agent.sendBinary
import com.epam.drill.plugins.Plugins
import com.epam.drill.plugins.agentPluginPart
import com.epam.drill.service.asyncTransaction
import com.epam.drill.storage.AgentStorage
import io.ktor.application.Application
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.apache.commons.codec.digest.DigestUtils
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.generic.instance

private val logger = KotlinLogging.logger {}

const val INITIAL_BUILD_ALIAS = "Initial build"

class AgentManager(override val kodein: Kodein) : KodeinAware {

    val app: Application by instance()
    val agentStorage: AgentStorage by instance()
    val plugins: Plugins by instance()

    suspend fun agentConfiguration(agentId: String, pBuildVersion: String) = asyncTransaction {
        addLogger(StdOutSqlLogger)
        val agentInfoDb = AgentInfoDb.findById(agentId)
        if (agentInfoDb != null) {
            when (agentInfoDb.status) {
                AgentStatus.ONLINE -> agentInfoDb.apply {
                    val existingVersion = buildVersions.find { it.buildVersion == pBuildVersion }
                    val buildVersion = existingVersion ?: AgentBuildVersion.new {
                        buildVersion = pBuildVersion
                        name = ""
                    }.apply { buildVersions = SizedCollection(buildVersions.toSet() + this) }

                    this.buildVersion = pBuildVersion
                    this.buildAlias = buildVersion.name
                }
                AgentStatus.NOT_REGISTERED -> {
                    //TODO: add some processing for unregistered agents
                }
                AgentStatus.OFFLINE -> {
                    //TODO: add some processing for disabled agents
                }
                else -> Unit
            }
            agentInfoDb.toAgentInfo()
        } else {
            AgentInfoDb.new(agentId) {
                name = ""
                status = AgentStatus.NOT_REGISTERED
                groupName = ""
                description = ""
                this.buildVersion = pBuildVersion
                buildAlias = INITIAL_BUILD_ALIAS
                adminUrl = ""
                plugins = SizedCollection()

            }.apply {
                this.buildVersions =
                    SizedCollection(AgentBuildVersion.new {
                        this.buildVersion = pBuildVersion
                        this.name = INITIAL_BUILD_ALIAS
                    })
            }.toAgentInfo()
        }

    }

    suspend fun updateAgent(agentId: String, au: AgentInfoWebSocketSingle) {
        get(agentId)?.apply {
            name = au.name
            groupName = au.group
            description = au.description
            buildAlias = au.buildVersions.firstOrNull { it.id == this.buildVersion }?.name!!
            buildVersions.replaceAll(au.buildVersions)
            status = au.status
            update(this@AgentManager)
        }

    }

    suspend fun updateAgentPluginConfig(agentId: String, pc: PluginConfig): Boolean = get(agentId)?.let { agentInfo ->
        agentInfo.plugins.find { it.id == pc.id }?.let { plugin ->
            if (plugin.config != pc.data) {
                plugin.config = pc.data
                agentInfo.update(this)
            }
        }
    } != null

    suspend fun resetAgent(agInfo: AgentInfo) {
        val au = AgentInfoWebSocketSingle(
            id = agInfo.id,
            name = "",
            group = "",
            status = AgentStatus.NOT_REGISTERED,
            description = "",
            buildVersion = agInfo.buildVersion,
            buildAlias = INITIAL_BUILD_ALIAS
        )
            .apply { buildVersions.add(AgentBuildVersionJson(agInfo.buildVersion, INITIAL_BUILD_ALIAS)) }
        updateAgent(agInfo.id, au)
    }

    suspend fun update() {
        agentStorage.update()
    }

    suspend fun singleUpdate(agentId: String) {
        agentStorage.singleUpdate(agentId)
    }

    suspend fun put(agentInfo: AgentInfo, session: AgentWsSession) {
        agentStorage.put(agentInfo.id, AgentEntry(agentInfo, session))
    }

    suspend fun remove(agentInfo: AgentInfo) {
        agentStorage.remove(agentInfo.id)
    }

    fun agentSession(k: String) = agentStorage.targetMap[k]?.agentSession

    fun buildVersionByAgentId(agentId: String) = get(agentId)?.buildVersion ?: ""

    operator fun contains(k: String) = k in agentStorage.targetMap

    operator fun get(agentId: String) = agentStorage.targetMap[agentId]?.agent
    fun full(agentId: String) = agentStorage.targetMap[agentId]

    fun getAllAgents() = agentStorage.targetMap.values

    fun getAllInstalledPluginBeanIds(agentId: String) = get(agentId)?.plugins?.map { it.id }

    suspend fun addPluginFromLib(agentId: String, pluginId: String) = asyncTransaction {
        val agentInfoDb = AgentInfoDb.findById(agentId)
        if (agentInfoDb != null) {
            plugins[pluginId]?.pluginBean?.let { plugin ->
                val fillPluginBeanDb: PluginBeanDb.() -> Unit = {
                    this.pluginId = plugin.id
                    this.name = plugin.name
                    this.description = plugin.description
                    this.type = plugin.type
                    this.family = plugin.family
                    this.enabled = plugin.enabled
                    this.config = plugin.config
                }
                val rawPluginNames = agentInfoDb.plugins.toList()
                val existingPluginBeanDb = rawPluginNames.find { it.pluginId == pluginId }
                if (existingPluginBeanDb == null) {
                    val newPluginBeanDb = PluginBeanDb.new(fillPluginBeanDb)
                    agentInfoDb.plugins = SizedCollection(rawPluginNames + newPluginBeanDb)
                    newPluginBeanDb
                } else {
                    existingPluginBeanDb.apply(fillPluginBeanDb)
                }
            }
        } else {
            logger.warn { "Agent with id $agentId not found in your DB." }
            null
        }
    }?.let { pluginBeanDb ->
        val agentInfo = get(agentId)
        agentInfo!!.plugins.add(pluginBeanDb.toPluginBean())
        agentStorage.update()
        agentStorage.singleUpdate(agentId)
        updateAgentConfig(agentInfo)
        logger.info { "Plugin $pluginId successfully added to agent with id $agentId!" }
    }

    suspend fun updateAgentConfig(agentInfo: AgentInfo) = app.launch {
        agentSession(agentInfo.id)?.apply {
            while (agentInfo.status != AgentStatus.ONLINE) {
                delay(300)
            }
            agentInfo.status = AgentStatus.BUSY
            update()
            singleUpdate(agentInfo.id)
            agentInfo.plugins.forEach { pb ->
                val data = plugins[pb.id]?.agentPluginPart!!.readBytes()
                pb.md5Hash = DigestUtils.md5Hex(data)
                sendBinary("/plugins/load", pb, data).await()
            }
            agentInfo.status = AgentStatus.ONLINE
            update()
            singleUpdate(agentInfo.id)
        }
    }
}
