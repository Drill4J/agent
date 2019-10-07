package com.epam.drill.admindata

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import kotlinx.atomicfu.*
import org.jacoco.core.analysis.*
import org.jacoco.core.data.*
import java.util.concurrent.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class AgentBuildManager(val agentId: String) : BuildManager {
    private val _lastBuild = atomic("")

    override val buildInfos: MutableMap<String, BuildInfo> = ConcurrentHashMap()

    private val _jsonClasses = atomic(mapOf<String, String>())

    private var jsonClasses: Map<String, String>
        get() = _jsonClasses.value
        private set(value) {
            _jsonClasses.value = value
        }

    private var lastBuild: String
        get() = _lastBuild.value
        private set(value) {
            _lastBuild.value = value
        }

    override val summaries: List<BuildSummary>
        get() = buildInfos.values.map { it.buildSummary }

    override operator fun get(buildVersion: String) = buildInfos[buildVersion]

    fun addClass(rawData: String) {
        val rawJsonClasses = (JsonClasses.serializer() parse rawData).classes
        jsonClasses = jsonClasses + rawJsonClasses
    }

    fun fillClassesData(buildVersion: String) {
        val buildVersionIsNew = buildInfos[buildVersion] == null
        val buildInfo = buildInfos[buildVersion] ?: BuildInfo()
        val prevBuild = buildInfo.prevBuild
        buildInfos[buildVersion] = buildInfo.copy(
            buildVersion = buildVersion,
            prevBuild = if (buildVersionIsNew) lastBuild else prevBuild,
            classesBytes = jsonClasses.mapValues { decode(it.value) }
        )
        if (buildVersionIsNew) {
            lastBuild = buildVersion
        }
        compareToPrev(buildVersion)
        jsonClasses = mapOf()
    }

    private fun compareToPrev(buildVersion: String) {
        val currentMethods = buildInfos[buildVersion]?.classesBytes?.mapValues { (className, bytes) ->
            BcelClassParser(bytes, className).parseToJavaMethods()
        } ?: emptyMap()
        buildInfos[buildVersion] = buildInfos[buildVersion]?.copy(javaMethods = currentMethods) ?: BuildInfo()
        val buildInfo = buildInfos[buildVersion] ?: BuildInfo()
        val prevMethods = buildInfos[buildInfo.prevBuild]?.javaMethods ?: mapOf()

        val coverageBuilder = CoverageBuilder()
        val analyzer = Analyzer(ExecutionDataStore(), coverageBuilder)
        buildInfos[buildVersion]?.classesBytes?.map { (className, bytes) -> analyzer.analyzeClass(bytes, className) }
        val bundleCoverage = coverageBuilder.getBundle("")

        buildInfos[buildVersion] = buildInfos[buildVersion]?.copy(
            methodChanges = MethodsComparator(bundleCoverage).compareClasses(prevMethods, currentMethods)
        ) ?: BuildInfo()

        val changes = buildInfos[buildVersion]?.methodChanges?.map ?: emptyMap()
        buildInfos[buildVersion] = buildInfos[buildVersion]?.copy(
            buildSummary = BuildSummary(
                name = buildVersion,
                addedDate = System.currentTimeMillis(),
                totalMethods = changes.values.flatten().count(),
                newMethods = changes[DiffType.NEW]?.count() ?: 0,
                modifiedMethods = (changes[DiffType.MODIFIED_NAME]?.count() ?: 0) +
                    (changes[DiffType.MODIFIED_BODY]?.count() ?: 0) +
                    (changes[DiffType.MODIFIED_DESC]?.count() ?: 0),
                unaffectedMethods = changes[DiffType.UNAFFECTED]?.count() ?: 0,
                deletedMethods = changes[DiffType.DELETED]?.count() ?: 0
            )
        ) ?: BuildInfo()
    }

}

fun decode(source: String): ByteArray = java.util.Base64.getDecoder().decode(source)