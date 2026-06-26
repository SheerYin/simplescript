package me.yin.simplescript.newscript

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic

object SimpleScriptCompileChecker {
    @JvmStatic
    fun main(args: Array<String>) {
        val compiler: SimpleScriptCompiler = SimpleScriptCompiler(
            classLoader = SimpleScriptCompileChecker::class.java.classLoader,
            logger = LoggerFactory.getLogger(SimpleScriptCompileChecker::class.java)
        )

        val failed: MutableList<Path> = mutableListOf()
        for (argument: String in args) {
            val script: Path = Path.of(argument).toAbsolutePath().normalize()
            val result: ResultWithDiagnostics<CompiledScript> = runBlocking {
                compiler.compile(script)
            }

            for (report: ScriptDiagnostic in result.reports) {
                val message: String = "[${report.severity}] $script ${report.location ?: ""} ${report.message}"
                when (report.severity) {
                    ScriptDiagnostic.Severity.ERROR,
                    ScriptDiagnostic.Severity.FATAL -> System.err.println(message)

                    ScriptDiagnostic.Severity.WARNING -> System.err.println(message)
                    ScriptDiagnostic.Severity.INFO -> println(message)
                    ScriptDiagnostic.Severity.DEBUG -> Unit
                }
            }

            if (result is ResultWithDiagnostics.Failure) {
                failed.add(script)
            }
        }

        if (failed.isNotEmpty()) {
            val failedScripts: String = failed.joinToString { script: Path -> script.toString() }
            error("Failed to compile ${failed.size} script(s): $failedScripts")
        }

        println("Checked ${args.size} script(s)")
    }
}
