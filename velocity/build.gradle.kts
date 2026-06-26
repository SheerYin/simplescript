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
    compileOnly("com.velocitypowered:velocity-api:3.4.0")

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.scripting.common)
    implementation(libs.kotlin.scripting.jvm)
    implementation(libs.kotlin.scripting.jvm.host)
    implementation(libs.kotlin.scripting.compiler.embeddable)
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

val generateVelocityPluginJson = tasks.register("generateVelocityPluginJson") {
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

val checkScripts = tasks.register<JavaExec>("checkScripts") {
    group = "verification"
    description = "Compiles bundled SimpleScript scripts without evaluating them."

    val mainSourceSet = sourceSets.main.get()
    val scriptDirectoryPath = layout.projectDirectory.dir("src/main/resources/script/main")
    val scriptFiles = fileTree(scriptDirectoryPath) {
        include("**/*.kts")
    }
    val checkScriptsWorkingDirectory = layout.buildDirectory.dir("checkScripts")
    classpath = mainSourceSet.output + mainSourceSet.compileClasspath + mainSourceSet.runtimeClasspath
    mainClass.set("me.yin.simplescript.velocity.script.SimpleScriptCompileChecker")
    val scriptPaths = scriptFiles.files
        .map { file -> file.toPath().toAbsolutePath() }
        .sortedBy { path -> path.toString() }
    scriptPaths.forEach { path ->
        require(Files.isRegularFile(path)) { "Script path is not a regular file: $path" }
        require(path.fileName.toString().endsWith(".kts")) { "Script path is not a .kts file: $path" }
    }
    args(scriptPaths.map { path -> path.toString() })
    inputs.files(scriptFiles)
    workingDir = checkScriptsWorkingDirectory.get().asFile

    doFirst {
        checkScriptsWorkingDirectory.get().asFile.mkdirs()
    }
}

tasks.check {
    dependsOn(checkScripts)
}

tasks.jar {
    archiveFileName.set(minecraftPluginJarFileName)
}

tasks.shadowJar {
    mergeServiceFiles()
    archiveFileName.set(minecraftPluginShadowJarFileName)
}
