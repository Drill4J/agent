package com.epam.drill.e2e

import com.epam.drill.common.*
import com.epam.drill.testdata.*
import io.kotlintest.*
import io.ktor.http.*

class PackagesPrefixesSettingTest : E2ETest() {

    private val agentId = "ag02"

    @org.junit.jupiter.api.Test
    fun `Packages prefixes changing Test`() {
        createSimpleAppWithUIConnection(agentStreamDebug = true) {
            connectAgent(AgentWrap(agentId)) { ui, agent ->
                ui.getAgent()?.status shouldBe AgentStatus.NOT_REGISTERED
                agent.getServiceConfig()?.sslPort shouldBe sslPort
                register(agentId).first shouldBe HttpStatusCode.OK
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                ui.getAgent()?.status shouldBe AgentStatus.ONLINE

                changePackages(
                    agentId = agentId,
                    payload = PackagesPrefixes(listOf("testPrefix"))
                ).first shouldBe HttpStatusCode.OK
                ui.getAgent()?.status shouldBe AgentStatus.BUSY
                agent.`get-set-packages-prefixes`()
                agent.`get-load-classes-datas`()
                val agent2 = ui.getAgent()
                agent2?.status shouldBe AgentStatus.ONLINE
                agent2?.packagesPrefixes?.first() shouldBe "testPrefix"
            }
        }
    }
}