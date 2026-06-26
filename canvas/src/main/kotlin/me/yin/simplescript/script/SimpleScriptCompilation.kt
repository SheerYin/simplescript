package me.yin.simplescript.script

import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptJvmCompilerIsolated
import org.slf4j.Logger
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.JvmScriptCompiler
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

object SimpleScriptCompilation {
    fun compiler(hostConfiguration: ScriptingHostConfiguration): JvmScriptCompiler {
        return JvmScriptCompiler(
            baseHostConfiguration = hostConfiguration,
            compilerProxy = ScriptJvmCompilerIsolated(hostConfiguration)
        )
    }

    fun configuration(
        hostConfiguration: ScriptingHostConfiguration,
        classLoader: ClassLoader,
        logger: Logger
    ): ScriptCompilationConfiguration {
        val configurator = SimpleScriptConfigurator(logger)
        return createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate>(hostConfiguration) {
            defaultImports(
                Import::class,
                CompilerOptions::class
            )

            jvm {
                // In Canvas/Paper shadow jars the plugin classloader can already see the plugin
                // classes and shaded scripting dependencies. With the K1 compiler this is enough
                // for @file:Import and the script template; no manual plugin jar classpath is needed.
                dependenciesFromClassloader(
                    classLoader = classLoader,
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
    }
}
