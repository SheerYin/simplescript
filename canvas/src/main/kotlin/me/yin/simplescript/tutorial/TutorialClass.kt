package me.yin.simplescript.tutorial

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptJvmCompilerIsolated
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCollectedData
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.ScriptSourceAnnotation
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.collectedAnnotations
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.importScripts
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.host.FileBasedScriptSource
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.JvmScriptCompiler

class TutorialClass(
    private val classLoader: ClassLoader = TutorialClass::class.java.classLoader
) {
    private val logger = LoggerFactory.getLogger(TutorialClass::class.java)

    /**
     * Stage 1 only: read a .kts file and ask Kotlin to compile it.
     *
     * This checks syntax, types, imports, @file:Import, @file:CompilerOptions, and
     * whether the script can see the plugin classes. It does not run the script's
     * top-level statements.
     */
    fun compileOnly(scriptPath: Path): CompileOnlyResult {
        val hostConfiguration: ScriptingHostConfiguration = createHostConfiguration()
        val compiler: JvmScriptCompiler = createK1Compiler(hostConfiguration)
        val compilationConfiguration: ScriptCompilationConfiguration = createCompilationConfiguration(hostConfiguration)
        val scriptSource: kotlin.script.experimental.api.SourceCode = scriptPath.toFile().toScriptSource()

        val result: ResultWithDiagnostics<CompiledScript> = runBlocking {
            compiler.invoke(scriptSource, compilationConfiguration)
        }

        return result.toCompileOnlyResult()
    }

    /**
     * Stage 1 + Stage 2: compile the .kts file, then evaluate it with a real
     * SimpleScriptContext.
     *
     * This is the part that actually executes top-level script code. SimpleScriptService
     * uses the same kind of host.eval(...) call in the real plugin.
     */
    fun evaluate(scriptPath: Path, context: SimpleScriptContext): EvaluateResult {
        val hostConfiguration: ScriptingHostConfiguration = createHostConfiguration()
        val compiler: JvmScriptCompiler = createK1Compiler(hostConfiguration)
        val scriptingHost: BasicJvmScriptingHost = BasicJvmScriptingHost(
            baseHostConfiguration = hostConfiguration,
            compiler = compiler
        )

        val compilationConfiguration: ScriptCompilationConfiguration = createCompilationConfiguration(hostConfiguration)
        val evaluationConfiguration: ScriptEvaluationConfiguration = ScriptEvaluationConfiguration {
            // SimpleScriptTemplate has a primary constructor:
            // abstract class SimpleScriptTemplate(val context: SimpleScriptContext)
            // This argument is passed into that constructor, which is why .kts code
            // can access `context`.
            constructorArgs(context)

            jvm {
                // Runtime classloader used while executing compiled script bytecode.
                baseClassLoader(classLoader)
            }
        }

        val result: ResultWithDiagnostics<EvaluationResult> = scriptingHost.eval(
            scriptPath.toFile().toScriptSource(),
            compilationConfiguration,
            evaluationConfiguration
        )

        return when (result) {
            is ResultWithDiagnostics.Success -> EvaluateResult(
                ok = true,
                returnValue = result.value.returnValue.toString(),
                diagnostics = result.reports.toCompileOnlyDiagnostics()
            )

            is ResultWithDiagnostics.Failure -> EvaluateResult(
                ok = false,
                returnValue = null,
                diagnostics = result.reports.toCompileOnlyDiagnostics()
            )
        }
    }

    private fun createHostConfiguration(): ScriptingHostConfiguration {
        // Host configuration describes the environment that owns scripting.
        // This simple host has no custom services; the important pieces are in
        // compilation/evaluation configuration below.
        return ScriptingHostConfiguration {}
    }

    private fun createK1Compiler(hostConfiguration: ScriptingHostConfiguration): JvmScriptCompiler {
        // BasicJvmScriptingHost may use Kotlin's newer K2 scripting path by default.
        // SimpleScript deliberately constructs this compiler, the same way as
        // SimpleScriptCompilation, to stay on the legacy K1 scripting compiler path.
        //
        // JvmScriptCompiler turns .kts source into a compiled JVM script object.
        // ScriptJvmCompilerIsolated runs that compiler through an isolated proxy,
        // which is useful inside plugin/classloader-heavy environments.
        return JvmScriptCompiler(
            baseHostConfiguration = hostConfiguration,
            compilerProxy = ScriptJvmCompilerIsolated(hostConfiguration)
        )
    }

    private fun createCompilationConfiguration(
        hostConfiguration: ScriptingHostConfiguration
    ): ScriptCompilationConfiguration {
        return ScriptCompilationConfiguration(TutorialScriptCompilationConfiguration) {
            // createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate>(...)
            // is a Kotlin scripting helper that reads the @KotlinScript annotation on
            // SimpleScriptTemplate and creates a JVM compilation configuration from it.
            //
            // Expanded manually, the most important part is this: tell the compiler
            // that every .kts file should be compiled as a subclass of
            // SimpleScriptTemplate. Because SimpleScriptTemplate has
            // `val context: SimpleScriptContext`, script code can access `context`.
            baseClass(SimpleScriptTemplate::class)

            // These are SimpleScript's two file-level annotations.
            //
            // Import:
            //   lets a script include another .kts file before compilation.
            //
            // CompilerOptions:
            //   lets a script append Kotlin compiler arguments for this script.
            //
            // defaultImports makes these annotations available to scripts without
            // writing normal Kotlin imports first.
            //
            // A script can write:
            // @file:Import("common.kts")
            // @file:CompilerOptions("-Xcontext-parameters")
            defaultImports(
                Import::class,
                CompilerOptions::class
            )

            jvm {
                // Compile-time classpath. This is why scripts can reference Bukkit,
                // Adventure, your plugin classes, and shaded Kotlin scripting classes.
                dependenciesFromClassloader(
                    classLoader = classLoader,
                    wholeClasspath = true
                )
            }

            refineConfiguration {
                // Before final compilation, Kotlin collects the two SimpleScript file
                // annotations above and calls this handler. SimpleScriptConfigurator
                // resolves @file:Import paths and appends @file:CompilerOptions args.
                onAnnotations(
                    Import::class,
                    CompilerOptions::class,
                    handler = ::configureBySimpleScriptAnnotations
                )
            }
        }
    }

    private fun configureBySimpleScriptAnnotations(
        context: ScriptConfigurationRefinementContext
    ): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val collectedFileAnnotations: List<ScriptSourceAnnotation<*>> = context.collectedFileAnnotations()
        val annotations: List<Annotation> = collectedFileAnnotations.map { sourceAnnotation: ScriptSourceAnnotation<*> ->
            sourceAnnotation.annotation
        }

        val importedScripts: List<FileScriptSource> = annotations
            .filterIsInstance<Import>()
            .flatMap { annotation: Import -> annotation.paths.toList() }
            .map { importPath: String ->
                val resolvedImportPath: Path = context.resolveImportPath(importPath)
                FileScriptSource(resolvedImportPath.toFile())
            }

        val compilerArguments: List<String> = annotations
            .filterIsInstance<CompilerOptions>()
            .flatMap { annotation: CompilerOptions -> annotation.options.toList() }

        val refinedCompilationConfiguration: ScriptCompilationConfiguration = ScriptCompilationConfiguration(
            context.compilationConfiguration
        ) {
            if (importedScripts.isNotEmpty()) {
                // @file:Import("common.kts") becomes an imported script source here.
                importScripts.append(importedScripts)
            }

            if (compilerArguments.isNotEmpty()) {
                // @file:CompilerOptions("-X...") becomes extra compiler arguments here.
                compilerOptions.append(compilerArguments)
            }
        }

        if (importedScripts.isNotEmpty()) {
            logger.debug("Imported {} script(s) for {}", importedScripts.size, context.script.name)
        }

        return ResultWithDiagnostics.Success(refinedCompilationConfiguration)
    }

    private fun ScriptConfigurationRefinementContext.collectedFileAnnotations(): List<ScriptSourceAnnotation<*>> {
        val collectedData: ScriptCollectedData? = collectedData
        return collectedData?.get(ScriptCollectedData.collectedAnnotations).orEmpty()
    }

    private fun ScriptConfigurationRefinementContext.resolveImportPath(importPathText: String): Path {
        val importPath: Path = Path.of(importPathText)
        if (importPath.isAbsolute) {
            return importPath.normalize()
        }

        val scriptPath: Path? = (script as? FileBasedScriptSource)?.file?.toPath()
        val scriptDirectory: Path = scriptPath?.parent ?: Path.of(".")
        return scriptDirectory.resolve(importPath).normalize()
    }

    private fun ResultWithDiagnostics<*>.toCompileOnlyResult(): CompileOnlyResult {
        return when (this) {
            is ResultWithDiagnostics.Success -> CompileOnlyResult(
                ok = true,
                diagnostics = reports.toCompileOnlyDiagnostics()
            )

            is ResultWithDiagnostics.Failure -> CompileOnlyResult(
                ok = false,
                diagnostics = reports.toCompileOnlyDiagnostics()
            )
        }
    }

    private fun List<ScriptDiagnostic>.toCompileOnlyDiagnostics(): List<CompileOnlyDiagnostic> {
        return map { report ->
            CompileOnlyDiagnostic(
                severity = report.severity.name,
                message = report.message,
                location = report.location?.toString()
            )
        }
    }
}

@KotlinScript(
    fileExtension = TUTORIAL_SCRIPT_EXTENSION,
    compilationConfiguration = TutorialScriptCompilationConfiguration::class
)
abstract class SimpleScriptTemplate(
    val context: SimpleScriptContext
)

object TutorialScriptCompilationConfiguration : ScriptCompilationConfiguration({})

class SimpleScriptContext(
    val scriptId: String
)

@Repeatable
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class Import(vararg val paths: String)

@Repeatable
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class CompilerOptions(vararg val options: String)

const val TUTORIAL_SCRIPT_EXTENSION = "kts"

data class CompileOnlyResult(
    val ok: Boolean,
    val diagnostics: List<CompileOnlyDiagnostic>
)

data class CompileOnlyDiagnostic(
    val severity: String,
    val message: String,
    val location: String?
)

data class EvaluateResult(
    val ok: Boolean,
    val returnValue: String?,
    val diagnostics: List<CompileOnlyDiagnostic>
)
