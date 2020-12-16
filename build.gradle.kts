import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.epam.drill.cross-compilation")
    `maven-publish`
}

val scriptUrl: String by extra

val serializationRuntimeVersion: String by extra
val coroutinesVersion: String by extra
val drillHttpInterceptorVersion: String by extra
val drillApiVersion: String by extra
val drillLoggerVersion: String by extra
val drillLoggerApiVersion: String by extra
val drillTransportLibVerison: String by extra

allprojects {
    apply(from = "$scriptUrl/git-version.gradle.kts")

    repositories {
        mavenLocal()
        apply(from = "$scriptUrl/maven-repo.gradle.kts")
        jcenter()
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.allWarningsAsErrors = true
    }
    tasks.withType<KotlinNativeCompile> {
        kotlinOptions.allWarningsAsErrors = true
    }
    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.jetbrains.kotlinx:kotlinx-coroutines-core-native:$coroutinesVersion")).with(module("org.jetbrains.kotlinx:kotlinx-coroutines-core-native:$coroutinesVersion-native-mt"))
        }
    }
}

kotlin {

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib-common")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$serializationRuntimeVersion")
                implementation("com.epam.drill.logger:logger-api:$drillLoggerApiVersion")
            }
        }
    }

    crossCompilation {
        common {
            addCInterop()
            defaultSourceSet {
                dependsOn(sourceSets.named("commonMain").get())
                dependencies {
                    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf-native:$serializationRuntimeVersion")
                    implementation("com.epam.drill:transport:$drillTransportLibVerison")
                    implementation("com.epam.drill.interceptor:http:$drillHttpInterceptorVersion")
                    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.2")
                    implementation("com.epam.drill:drill-agent-part:$drillApiVersion")
                    implementation("com.epam.drill:common:$drillApiVersion")
                    implementation("com.epam.drill.logger:logger:$drillLoggerVersion")
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-native:$coroutinesVersion")
                }
            }
        }
        posix {
            defaultSourceSet {
                dependsOn(sourceSets.named("commonMain").get())
            }
        }
    }

    setOf(
        mingwX64 { binaries.all { linkerOpts("-lpsapi", "-lwsock32", "-lws2_32", "-lmswsock") } },
        macosX64(),
        linuxX64()
    ).forEach {
        it.compilations["main"].addCInterop()
    }

}

tasks.withType<KotlinNativeCompile> {
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.serialization.ImplicitReflectionSerializer"
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.ExperimentalUnsignedTypes"
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.time.ExperimentalTime"
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi"
}


fun org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation.addCInterop() {
    cinterops.create("zstd_bindings").includeDirs(rootProject.file("lib").resolve("include"))
}
