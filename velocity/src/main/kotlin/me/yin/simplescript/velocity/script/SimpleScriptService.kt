package me.yin.simplescript.velocity.script

import me.yin.simplescript.velocity.SimpleScriptVelocity
import java.nio.file.Path
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

const val SIMPLE_SCRIPT_EXTENSION = "kts"

class SimpleScriptService(
    private val simpleScriptVelocity: SimpleScriptVelocity
) {
    private val pluginClassLoader = simpleScriptVelocity.javaClass.classLoader
    private val configurator = SimpleScriptConfigurator()
    private val scriptingHost = BasicJvmScriptingHost()
    private val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate> {
        defaultImports(
            Import::class,
            CompilerOptions::class
        )

        jvm {
            dependenciesFromClassloader(
                classLoader = pluginClassLoader,
                wholeClasspath = true
            )
        }

        refineConfiguration {
            onAnnotations(
                Import::class,
                CompilerOptions::class,
                handler = configurator::configure
            )
        }
    }

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
