import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    alias(libs.plugins.canvas.weaver.userdev)
    alias(libs.plugins.resource.factory.paper)
}

group = "me.yin.simplescript"
version = "1.0.0-SNAPSHOT"

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.canvasmc.io/releases")
}

dependencies {
    paperweight.canvasDevBundle("26.1.2.build.+")

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
val minecraftPluginApiVersion = "26.1.2"
val minecraftPluginVersion = project.version.toString()
val minecraftPluginAuthors = listOf("尹")
val minecraftPluginPrefix = "简单脚本"
val minecraftPluginGroup = project.group.toString()
val minecraftPluginMain = "$minecraftPluginGroup.$minecraftPluginName"
val minecraftPluginLoader = "$minecraftPluginGroup.${minecraftPluginName}Loader"
val minecraftPluginJarName = "$minecraftPluginName-${project.name}"
val minecraftPluginJarFileName = "$minecraftPluginJarName.jar"
val minecraftPluginShadowJarFileName = "$minecraftPluginJarName-shadow.jar"

paperPluginYaml {
    name = minecraftPluginName
    apiVersion = minecraftPluginApiVersion
    version = minecraftPluginVersion
    main = minecraftPluginMain
    authors = minecraftPluginAuthors
    prefix = minecraftPluginPrefix
    loader = minecraftPluginLoader
    foliaSupported = true
}

val generatePaperLibraries = tasks.register("generatePaperLibraries") {
    val outputFile = layout.buildDirectory.file("generated/paper/libraries.text")
    outputs.file(outputFile)
    inputs.files(configurations.runtimeClasspath)

    doLast {
        val libraries = configurations.runtimeClasspath.get()
            .resolvedConfiguration
            .firstLevelModuleDependencies

        val outputFilePath = outputFile.get().asFile.toPath()
        Files.createDirectories(outputFilePath.parent)

        Files.newBufferedWriter(outputFilePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { writer ->
            libraries.forEach {
                writer.write("${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}")
                writer.newLine()
            }
        }
    }
}

tasks.processResources {
    from(generatePaperLibraries)
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
    mainClass.set("me.yin.simplescript.script.SimpleScriptCompileChecker")
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

class LibrariesTextLibrariesRemover : ResourceTransformer {
    private var transformed = false

    override fun canTransformResource(element: FileTreeElement): Boolean {
        val isTarget = element.relativePath.pathString == "libraries.text"
        if (isTarget) {
            transformed = true
        }
        return isTarget
    }

    override fun transform(context: TransformerContext) {
    }

    override fun hasTransformedResource(): Boolean {
        return transformed
    }

    override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
        val entry = ZipEntry("libraries.text")
        entry.time = System.currentTimeMillis()
        os.putNextEntry(entry)
        os.write(ByteArray(0))
        os.closeEntry()
    }
}

tasks.shadowJar {
    mergeServiceFiles()
    archiveFileName.set(minecraftPluginShadowJarFileName)
    transform(LibrariesTextLibrariesRemover())
}
