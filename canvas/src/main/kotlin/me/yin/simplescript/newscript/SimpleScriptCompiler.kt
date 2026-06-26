package me.yin.simplescript.newscript

import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptJvmCompilerIsolated
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.JvmScriptCompiler

class SimpleScriptCompiler(
    private val classLoader: ClassLoader,
    logger: Logger
) {
    private val hostConfiguration: ScriptingHostConfiguration = ScriptingHostConfiguration {}

    // Kotlin 2.4's JVM scripting host may choose the newer K2 scripting path by default.
    // At the moment K2 can fail when imported scripts expose callables with parameters,
    // so SimpleScript constructs JvmScriptCompiler directly with ScriptJvmCompilerIsolated
    // to stay on the legacy K1 scripting compiler path for compilation.
    private val compiler: JvmScriptCompiler = JvmScriptCompiler(
        baseHostConfiguration = hostConfiguration,
        compilerProxy = ScriptJvmCompilerIsolated(hostConfiguration)
    )
    private val configurationRefiner: SimpleScriptConfigurationRefiner = SimpleScriptConfigurationRefiner(logger)
    private val compilationConfiguration: ScriptCompilationConfiguration =
        ScriptCompilationConfiguration(SimpleScriptCompilationConfiguration) {
            baseClass(SimpleScriptTemplate::class)

            defaultImports(
                Import::class,
                CompilerOptions::class
            )

            jvm {
                dependenciesFromClassloader(
                    classLoader = classLoader,
                    wholeClasspath = true
                )
            }

            refineConfiguration {
                onAnnotations(
                    Import::class,
                    CompilerOptions::class,
                    handler = configurationRefiner::handle
                )
            }
        }

    suspend fun compile(path: Path): ResultWithDiagnostics<CompiledScript> {
        val sourceCode: SourceCode = path.toFile().toScriptSource()

        val result: ResultWithDiagnostics<CompiledScript> = compiler.invoke(
            sourceCode,
            compilationConfiguration
        )

        return result
    }
}
