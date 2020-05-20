import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.epam.drill.cross-compilation")
    `maven-publish`
}

val scriptUrl: String by extra

val serializationRuntimeVersion = "0.20.0"
val coroutinesVersion = "1.3.5"
val drillHttpInterceptorVersion = "0.1.3"
val drillApiVersion = "0.5.0"
val drillLogger = "0.1.3"
val drillTransportLibVerison = "0.2.5"

allprojects {
    apply(from = "$scriptUrl/git-version.gradle.kts")

    repositories {
        mavenLocal()
        apply(from = "$scriptUrl/maven-repo.gradle.kts")
        jcenter()
        maven(url = "https://dl.bintray.com/kotlin/kotlinx/")
        maven(url = "https://dl.bintray.com/kotlin/ktor/")
        maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
        maven(url = "https://dl.bintray.com/korlibs/korlibs/")
    }

    apply(plugin = "com.epam.drill.version.plugin")
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
                    implementation("com.epam.drill.logger:logger:$drillLogger")
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

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib-common")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$serializationRuntimeVersion")
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
