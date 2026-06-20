package me.yin.simplescript.script

import me.yin.simplescript.SimpleScript
import java.nio.file.Path
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.experimental.host.toScriptSource

const val SIMPLE_SCRIPT_EXTENSION = "kts"

class SimpleScriptService(
    private val simpleScript: SimpleScript
) {
    private val scriptingHost = BasicJvmScriptingHost()
    private val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate> {
        jvm {
            dependenciesFromClassloader(
                classLoader = simpleScript.javaClass.classLoader,
                wholeClasspath = true
            )
        }
    }

    fun evaluate(scriptPath: Path, context: SimpleScriptContext): ResultWithDiagnostics<EvaluationResult> {
        val evaluationConfiguration = ScriptEvaluationConfiguration {
            constructorArgs(context)

            jvm {
                baseClassLoader(simpleScript.javaClass.classLoader)
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
