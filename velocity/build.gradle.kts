import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

group = "me.yin.simplescript"
version = "1.0.0-SNAPSHOT"

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {
    compileOnly(libs.velocity.api)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.scripting.common)
    implementation(libs.kotlin.scripting.jvm)
    implementation(libs.kotlin.scripting.jvm.host)
    implementation(libs.caffeine)
    implementation(libs.lettuce.core)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.configurate.yaml)
    implementation(libs.configurate.extra.kotlin)
    implementation(libs.sqlite.jdbc)
}

val minecraftPluginName = "SimpleScript"
val minecraftPluginId = minecraftPluginName.lowercase()
val minecraftPluginVersion = project.version.toString()
val minecraftPluginAuthors = listOf("尹")
val minecraftPluginGroup = project.group.toString()
val minecraftPluginMain = "$minecraftPluginGroup.velocity.${minecraftPluginName}Velocity"
val minecraftPluginJarName = "$minecraftPluginName-${project.name}"
val minecraftPluginJarFileName = "$minecraftPluginJarName.jar"
val minecraftPluginShadowJarFileName = "$minecraftPluginJarName-shadow.jar"

val generateVelocityPluginJson by tasks.registering {
    val outputFile = layout.buildDirectory.file("generated/velocity/velocity-plugin.json")

    inputs.property("minecraftPluginId", minecraftPluginId)
    inputs.property("minecraftPluginName", minecraftPluginName)
    inputs.property("minecraftPluginVersion", minecraftPluginVersion)
    inputs.property("minecraftPluginAuthors", minecraftPluginAuthors)
    inputs.property("minecraftPluginMain", minecraftPluginMain)

    outputs.file(outputFile)

    doLast {
        val authorsJson = minecraftPluginAuthors.joinToString(", ") { "\"$it\"" }
        val content = """
            {
              "id": "$minecraftPluginId",
              "name": "$minecraftPluginName",
              "version": "$minecraftPluginVersion",
              "authors": [$authorsJson],
              "main": "$minecraftPluginMain"
            }
        """.trimIndent()

        val outputFilePath = outputFile.get().asFile.toPath()
        Files.createDirectories(outputFilePath.parent)
        Files.writeString(
            outputFilePath,
            content,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
    }
}

tasks.processResources {
    from(generateVelocityPluginJson)
}

tasks.jar {
    archiveFileName.set(minecraftPluginJarFileName)
}

tasks.shadowJar {
    mergeServiceFiles()
    archiveFileName.set(minecraftPluginShadowJarFileName)
}
