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
            defaultSourceSet {
                dependsOn(sourceSets.named("commonMain").get())
                dependencies {
                    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf-native:$serializationRuntimeVersion")
                    implementation("com.epam.drill.transport:core:$drillTransportLibVerison")
                    implementation("com.epam.drill.interceptor:http:$drillHttpInterceptorVersion")
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

    mingwX64()
    linuxX64()
    macosX64()
}

tasks.withType<KotlinNativeCompile> {
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.serialization.ImplicitReflectionSerializer"
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.ExperimentalUnsignedTypes"
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.time.ExperimentalTime"
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi"
}
