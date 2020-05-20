rootProject.name = "agent"

val scriptUrl: String by extra
apply(from = "$scriptUrl/maven-repo.settings.gradle.kts")

pluginManagement {
    val kotlinVersion: String by extra
    val drillGradlePluginVersion: String by extra
    plugins {
        kotlin("multiplatform") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("com.epam.drill.cross-compilation") version drillGradlePluginVersion
    }
}
