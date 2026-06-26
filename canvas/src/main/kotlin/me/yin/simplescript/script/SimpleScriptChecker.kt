package me.yin.simplescript.script

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.toScriptSource

object SimpleScriptChecker {
    private val logger = LoggerFactory.getLogger(SimpleScriptChecker::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val scriptDirectory = args.firstOrNull()
            ?.let { Path(it).toAbsolutePath().normalize() }
            ?: error("Usage: SimpleScriptChecker <script-directory>")

        if (!Files.isDirectory(scriptDirectory)) {
            println("Script directory does not exist: $scriptDirectory")
            return
        }

        val scripts = Files.walk(scriptDirectory).use { paths ->
            paths
                .iterator()
                .asSequence()
                .filter { path -> Files.isRegularFile(path) }
                .filter { path -> path.fileName.toString().endsWith(".$SIMPLE_SCRIPT_EXTENSION") }
                .sorted()
                .toList()
        }

        if (scripts.isEmpty()) {
            println("No scripts found in $scriptDirectory")
            return
        }

        val hostConfiguration = ScriptingHostConfiguration {}
        val compiler = SimpleScriptCompilation.compiler(hostConfiguration)
        val compilationConfiguration = SimpleScriptCompilation.configuration(
            hostConfiguration = hostConfiguration,
            classLoader = SimpleScriptChecker::class.java.classLoader,
            logger = logger
        )

        val failed = mutableListOf<Path>()
        for (script in scripts) {
            val result = runBlocking {
                compiler(script.toFile().toScriptSource(), compilationConfiguration)
            }
            logReports(scriptDirectory, script, result.reports)
            if (result is ResultWithDiagnostics.Failure) {
                failed.add(script)
            }
        }

        if (failed.isNotEmpty()) {
            val failedScripts = failed.joinToString { scriptDirectory.relativize(it).toString() }
            error("Failed to compile ${failed.size} script(s): $failedScripts")
        }

        println("Checked ${scripts.size} script(s) in $scriptDirectory")
    }

    private fun logReports(
        scriptDirectory: Path,
        script: Path,
        reports: List<ScriptDiagnostic>
    ) {
        val scriptId = scriptDirectory.relativize(script).toString()
        for (report in reports) {
            val message = "[${report.severity}] $scriptId ${report.location ?: ""} ${report.message}"
            when (report.severity) {
                ScriptDiagnostic.Severity.ERROR,
                ScriptDiagnostic.Severity.FATAL -> System.err.println(message)

                ScriptDiagnostic.Severity.WARNING -> System.err.println(message)
                ScriptDiagnostic.Severity.INFO -> println(message)
                ScriptDiagnostic.Severity.DEBUG -> Unit
            }
        }
    }
}
