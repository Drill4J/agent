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
val kxCollection: String by extra

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
}

kotlin {

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationRuntimeVersion")
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
                    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$serializationRuntimeVersion")
                    implementation("com.epam.drill:transport:$drillTransportLibVerison")
                    implementation("com.epam.drill.interceptor:http:$drillHttpInterceptorVersion")
                    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:$kxCollection")
                    implementation("com.epam.drill:drill-agent-part:$drillApiVersion")
                    implementation("com.epam.drill:common:$drillApiVersion")
                    implementation("com.epam.drill.logger:logger:$drillLoggerVersion")
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core") {
                        version { strictly("$coroutinesVersion-native-mt") }
                    }
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
    jvm {
        val main by compilations
        main.defaultSourceSet {
            dependencies {
                compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationRuntimeVersion")
            }
        }
    }

}

tasks.withType<KotlinNativeCompile> {
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.ExperimentalUnsignedTypes"
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.time.ExperimentalTime"
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi"
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.serialization.InternalSerializationApi"
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.serialization.ExperimentalSerializationApi"
}


fun org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation.addCInterop() {
    cinterops.create("zstd_bindings").includeDirs(rootProject.file("lib").resolve("include"))
}
