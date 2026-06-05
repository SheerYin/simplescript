package me.yin.simplescript.velocity.script

import me.yin.simplescript.velocity.SimpleScriptVelocity
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
    private val simpleScriptVelocity: SimpleScriptVelocity
) {
    private val scriptingHost = BasicJvmScriptingHost()
    private val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<SimpleScriptFile> {
        jvm {
            dependenciesFromClassloader(
                classLoader = simpleScriptVelocity.javaClass.classLoader,
                wholeClasspath = true
            )
        }
    }

    fun evaluate(scriptPath: Path, scope: SimpleScriptScope): ResultWithDiagnostics<EvaluationResult> {
        val evaluationConfiguration = ScriptEvaluationConfiguration {
            constructorArgs(scope)

            jvm {
                baseClassLoader(simpleScriptVelocity.javaClass.classLoader)
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
abstract class SimpleScriptFile(
    private val scope: SimpleScriptScope
) {
    val id: String
        get() = scope.id

    val simpleScriptVelocity: SimpleScriptVelocity
        get() = scope.simpleScriptVelocity

    val proxy
        get() = scope.proxy

    val dataDirectory: Path
        get() = scope.dataDirectory

    val logger
        get() = scope.logger

    fun onClose(block: () -> Unit) {
        scope.onClose(block)
    }
}

object SimpleScriptCompilationConfiguration : ScriptCompilationConfiguration({})
