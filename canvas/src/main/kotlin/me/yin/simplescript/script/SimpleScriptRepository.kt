package me.yin.simplescript.script

import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path

class SimpleScriptRepository(
    val directory: Path,
    private val logger: Logger
) {
    private val scriptIdPattern: Regex = Regex("""[\p{L}\p{N}_.-]+(?:/[\p{L}\p{N}_.-]+)*""")

    fun listIfExists(): List<ScriptFile> {
        if (!Files.isDirectory(directory)) {
            return emptyList()
        }

        return list()
    }

    fun list(): List<ScriptFile> {
        Files.createDirectories(directory)

        return Files.walk(directory).use { paths ->
            paths
                .iterator()
                .asSequence()
                .filter { path: Path -> Files.isRegularFile(path) }
                .filter { path: Path -> path.fileName.toString().endsWith(".$SIMPLE_SCRIPT_EXTENSION") }
                .mapNotNull { path: Path -> createScriptFile(path) }
                .sortedBy { scriptFile: ScriptFile -> scriptFile.id }
                .toList()
        }
    }

    fun resolveScriptFile(scriptId: String): ScriptFile? {
        if (!isValidScriptId(scriptId)) {
            return null
        }

        val scriptPath: Path = directory.resolve("$scriptId.$SIMPLE_SCRIPT_EXTENSION")
        if (!Files.isRegularFile(scriptPath)) {
            return null
        }

        return ScriptFile(
            id = scriptId,
            path = scriptPath
        )
    }

    private fun createScriptFile(scriptPath: Path): ScriptFile? {
        val scriptId: String = directory
            .relativize(scriptPath)
            .joinToString("/") { path: Path -> path.toString() }
            .removeSuffix(".$SIMPLE_SCRIPT_EXTENSION")

        if (!isValidScriptId(scriptId)) {
            logger.warn("Ignored script with invalid id {} from {}", scriptId, scriptPath)
            return null
        }

        return ScriptFile(
            id = scriptId,
            path = scriptPath
        )
    }

    private fun isValidScriptId(scriptId: String): Boolean {
        return scriptIdPattern.matches(scriptId) &&
            scriptId.split('/').none { path: String -> path == "." || path == ".." }
    }
}

data class ScriptFile(
    val id: String,
    val path: Path
)

