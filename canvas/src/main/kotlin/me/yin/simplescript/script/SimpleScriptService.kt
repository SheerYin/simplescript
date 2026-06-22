package me.yin.simplescript.script

import me.yin.simplescript.SimpleScript
import java.nio.file.Path
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.JvmScriptCompiler
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptJvmCompilerIsolated

const val SIMPLE_SCRIPT_EXTENSION = "kts"

class SimpleScriptService(
    private val simpleScript: SimpleScript
) {
    private val pluginClassLoader = simpleScript.javaClass.classLoader
    private val configurator = SimpleScriptConfigurator(simpleScript.slF4JLogger)
    private val hostConfiguration = ScriptingHostConfiguration {}

    // Kotlin 2.4 defaults the JVM scripting host to the K2 compiler. At the moment K2
    // crashes when importScripts exposes callables with parameters from an imported script,
    // so use the legacy K1 scripting compiler until that path is fixed upstream.
    private val scriptingHost = BasicJvmScriptingHost(
        baseHostConfiguration = hostConfiguration,
        compiler = JvmScriptCompiler(
            baseHostConfiguration = hostConfiguration,
            compilerProxy = ScriptJvmCompilerIsolated(hostConfiguration)
        )
    )

    private val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate>(hostConfiguration) {
        defaultImports(
            Import::class,
            CompilerOptions::class
        )

        jvm {
            // In Canvas/Paper shadow jars the plugin classloader can already see the plugin
            // classes and shaded scripting dependencies. With the K1 compiler this is enough
            // for @file:Import and the script template; no manual plugin jar classpath is needed.
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
