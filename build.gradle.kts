import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    id("kotlin-multiplatform")
    id("kotlinx-serialization")
    id("com.epam.drill.cross-compilation")
    id("com.epam.drill.version.plugin")
    `maven-publish`
}
allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        jcenter()
        maven(url = "https://dl.bintray.com/kotlin/kotlinx/")
        maven(url = "https://dl.bintray.com/kotlin/ktor/")
        maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
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
            substitute(module("org.jetbrains.kotlinx:kotlinx-coroutines-core-native:$coroutinesVersion")).with(module("com.epam.drill.fork.coroutines:kotlinx-coroutines-core-native:$coroutinesVersion"))
        }
    }
}

kotlin {
    crossCompilation {
        common {
            defaultSourceSet {
                dependsOn(sourceSets.named("commonMain").get())
                dependencies {
                    implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor-native:$serializationRuntimeVersion")
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
}
