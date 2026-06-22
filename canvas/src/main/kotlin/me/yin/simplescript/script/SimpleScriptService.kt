package me.yin.simplescript.script

import me.yin.simplescript.SimpleScript
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.configurationDependencies
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.GetScriptingClassByClassLoader
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.JvmGetScriptingClass
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.reflect.KClass

const val SIMPLE_SCRIPT_EXTENSION = "kts"

class SimpleScriptService(
    private val simpleScript: SimpleScript
) {
    private val pluginClassLoader = simpleScript.javaClass.classLoader
    private val pluginClasspath = createPluginClasspath()
    private val configurator = SimpleScriptConfigurator(simpleScript.slF4JLogger)
    private val hostConfiguration = ScriptingHostConfiguration {
        if (pluginClasspath.isNotEmpty()) {
            configurationDependencies.append(JvmDependency(pluginClasspath))
        }

        getScriptingClass(PluginFirstGetScriptingClass(pluginClassLoader))
    }
    private val scriptingHost = BasicJvmScriptingHost(hostConfiguration)

    private val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate>(hostConfiguration) {
        defaultImports(
            Import::class,
            CompilerOptions::class
        )

        jvm {
            dependenciesFromClassloader(
                classLoader = pluginClassLoader,
                wholeClasspath = true
            )
            updateClasspath(pluginClasspath)
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

    private fun createPluginClasspath(): List<File> {
        val classLoaderUrls = (pluginClassLoader as? URLClassLoader)
            ?.urLs
            .orEmpty()
            .mapNotNull { url -> url.toClasspathFileOrNull() }

        val codeSourceUrls = listOfNotNull(
            simpleScript.javaClass.protectionDomain?.codeSource?.location,
            Import::class.java.protectionDomain?.codeSource?.location,
            SimpleScriptTemplate::class.java.protectionDomain?.codeSource?.location
        ).mapNotNull { url -> url.toClasspathFileOrNull() }

        return (classLoaderUrls + codeSourceUrls)
            .map { file -> file.absoluteFile }
            .distinct()
    }

    private fun URL.toClasspathFileOrNull(): File? {
        if (protocol != "file") {
            return null
        }

        return runCatching { Path.of(toURI()).toFile() }.getOrNull()
    }
}

private class PluginFirstGetScriptingClass(
    private val pluginClassLoader: ClassLoader
) : GetScriptingClassByClassLoader {
    private val fallback = JvmGetScriptingClass()

    override fun invoke(
        classType: KotlinType,
        contextClass: KClass<*>,
        hostConfiguration: ScriptingHostConfiguration
    ): KClass<*> {
        return invoke(classType, contextClass.java.classLoader, hostConfiguration)
    }

    override fun invoke(
        classType: KotlinType,
        contextClassLoader: ClassLoader?,
        hostConfiguration: ScriptingHostConfiguration
    ): KClass<*> {
        val loadedClass = runCatching {
            pluginClassLoader.loadClass(classType.typeName).kotlin
        }.getOrNull()

        return loadedClass ?: fallback.invoke(classType, contextClassLoader, hostConfiguration)
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
