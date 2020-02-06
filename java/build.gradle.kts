import com.github.jengelman.gradle.plugins.shadow.tasks.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.gradle.tasks.*
import org.jetbrains.kotlin.konan.target.*

plugins {
    id("kotlin-multiplatform")
    id("kotlinx-serialization")
    distribution
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "5.1.0"
}

val libName = "drill-agent"

kotlin {
    targets {
        if (isDevMode) {
            currentTarget("nativeAgent") {
                binaries { sharedLib(libName, setOf(DEBUG)) }
                binaries.forEach {
                    if (HostManager.hostIsMingw)
                        it.linkerOpts("-lpsapi", "-lwsock32", "-lws2_32", "-lmswsock")
                }
            }
        } else {
            mingwX64 {
                binaries { sharedLib(libName, setOf(DEBUG)) }
                binaries.forEach {
                    it.linkerOpts("-lpsapi", "-lwsock32", "-lws2_32", "-lmswsock")
                }
            }
            macosX64 { binaries { sharedLib(libName, setOf(DEBUG)) } }
            linuxX64 { binaries { sharedLib(libName, setOf(DEBUG)) } }
        }
        jvm("javaAgent")

    }
    targets.filterIsInstance<KotlinNativeTarget>().forEach {
        val cinterops = it.compilations["test"].cinterops
        cinterops?.create("jvmapiStub")
        cinterops?.create("testSocket")
    }

    sourceSets {
        val commonNativeMain = maybeCreate("nativeAgentMain")
        @Suppress("UNUSED_VARIABLE") val commonNativeTest = maybeCreate("nativeAgentTest")
        if (!isDevMode) {
            targets.filterIsInstance<KotlinNativeTarget>().forEach {
                it.compilations.forEach { knCompilation ->
                    if (knCompilation.name == "main")
                        knCompilation.defaultSourceSet { dependsOn(commonNativeMain) }
                    else
                        knCompilation.defaultSourceSet { dependsOn(commonNativeTest) }

                }
            }
        }
        jvm("javaAgent").compilations["main"].defaultSourceSet {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation(kotlin("reflect")) //TODO jarhell quick fix for kotlin jvm apps
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation("com.epam.drill:common-jvm:$drillApiVersion")
                implementation("com.epam.drill:drill-agent-part-jvm:$drillApiVersion")
                implementation("com.alibaba:transmittable-thread-local:2.11.0")
            }
        }
        jvm("javaAgent").compilations["test"].defaultSourceSet {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }

        named("commonMain") {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib-common")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$serializationRuntimeVersion")
                implementation("com.epam.drill:drill-agent-part:$drillApiVersion")
                implementation("com.epam.drill:common:$drillApiVersion")
            }
        }
        named("commonTest") {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test-common")
                implementation("org.jetbrains.kotlin:kotlin-test-annotations-common")
            }
        }

        named("nativeAgentMain") {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-native:$serializationRuntimeVersion")
                implementation("com.epam.drill:jvmapi-native:$drillJvmApiLibVersion")
                implementation("com.epam.drill.transport:core:$drillTransportLibVerison")
                implementation("com.benasher44:uuid:0.0.6")
                implementation("com.epam.drill.hook:platform:$drillHookVersion")
                implementation("com.epam.drill:drill-agent-part:$drillApiVersion")
                implementation("com.epam.drill:common:$drillApiVersion")
                implementation(project(":core"))
                implementation(project(":util"))
            }
        }
    }

}

tasks.withType<KotlinNativeCompile> {
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.serialization.ImplicitReflectionSerializer"
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.ExperimentalUnsignedTypes"
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.time.ExperimentalTime"
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi"
}

tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest> {
    testLogging.showStandardStreams = true
}

val javaAgentJar by tasks.getting(Jar::class) {
    from(provider {
        kotlin.targets["javaAgent"].compilations["main"].compileDependencyFiles.map {
            if (it.isDirectory) it else zipTree(it)
        }
    })
}


val agentShadow by tasks.registering(ShadowJar::class) {
    mergeServiceFiles()
    isZip64 = true
    relocate("kotlin", "kruntime")
    archiveFileName.set("drillRuntime.jar")
    from(javaAgentJar)
}

afterEvaluate {
    val availableTarget =
        kotlin.targets.filterIsInstance<KotlinNativeTarget>().filter { HostManager().isEnabled(it.konanTarget) }

    distributions {
        availableTarget.forEach {
            val name = it.name
            create(name) {
                baseName = name
                contents {
                    from(tasks.getByPath(":java:proxy-agent:jar"))
                    from(agentShadow)
                    from(tasks.getByPath("link${libName.capitalize()}DebugShared${name.capitalize()}"))
                }
            }
        }
    }
    publishing {
        repositories {
            maven {

                url = uri("http://oss.jfrog.org/oss-release-local")
                credentials {
                    username =
                        if (project.hasProperty("bintrayUser"))
                            project.property("bintrayUser").toString()
                        else System.getenv("BINTRAY_USER")
                    password =
                        if (project.hasProperty("bintrayApiKey"))
                            project.property("bintrayApiKey").toString()
                        else System.getenv("BINTRAY_API_KEY")
                }
            }
        }

        publications {
            availableTarget.forEach {
                create<MavenPublication>("${it.name}Zip") {
                    artifactId = "$libName-${it.name}"
                    artifact(tasks["${it.name}DistZip"])
                }
            }
        }
    }
}