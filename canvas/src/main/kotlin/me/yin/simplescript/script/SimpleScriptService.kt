package me.yin.simplescript.script

import me.yin.simplescript.SimpleScript
import java.nio.file.Path
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

const val SIMPLE_SCRIPT_EXTENSION = "kts"

class SimpleScriptService(
    private val simpleScript: SimpleScript
) {
    private val pluginClassLoader = simpleScript.javaClass.classLoader
    private val hostConfiguration = ScriptingHostConfiguration {}

    // Kotlin 2.4 defaults the JVM scripting host to the K2 compiler. At the moment K2
    // crashes when importScripts exposes callables with parameters from an imported script,
    // so use the legacy K1 scripting compiler until that path is fixed upstream.
    private val scriptingHost = BasicJvmScriptingHost(
        baseHostConfiguration = hostConfiguration,
        compiler = SimpleScriptCompilation.compiler(hostConfiguration)
    )

    private val compilationConfiguration = SimpleScriptCompilation.configuration(
        hostConfiguration = hostConfiguration,
        classLoader = pluginClassLoader,
        logger = simpleScript.slF4JLogger
    )

    fun evaluate(scriptPath: Path, context: SimpleScriptContext): ResultWithDiagnostics<EvaluationResult> {
        val evaluationConfiguration = ScriptEvaluationConfiguration {
            constructorArgs(context)

            jvm {
                baseClassLoader(pluginClassLoader)
            }
        }

        return scriptingHost.eval(
            scriptPath.toFile().toScriptSource(),
            compilationConfiguration,
            evaluationConfiguration
        )
    }
}

@KotlinScript(
    fileExtension = SIMPLE_SCRIPT_EXTENSION,
    compilationConfiguration = SimpleScriptCompilationConfiguration::class
)
abstract class SimpleScriptTemplate(
    val context: SimpleScriptContext
)

object SimpleScriptCompilationConfiguration : ScriptCompilationConfiguration({})
