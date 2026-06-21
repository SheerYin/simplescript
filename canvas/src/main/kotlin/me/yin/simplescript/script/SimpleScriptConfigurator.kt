package me.yin.simplescript.script

import org.slf4j.Logger
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCollectedData
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.ScriptSourceAnnotation
import kotlin.script.experimental.api.collectedAnnotations
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.importScripts
import kotlin.script.experimental.host.FileBasedScriptSource
import kotlin.script.experimental.host.FileScriptSource

class SimpleScriptConfigurator(
    private val logger: Logger
) {
    fun configure(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val annotations = context.collectedFileAnnotations().map { it.annotation }

        val importedScripts = annotations
            .filterIsInstance<Import>()
            .flatMap { it.paths.toList() }
            .map { path -> FileScriptSource(context.resolveImportPath(path).toFile()) }

        val compilerArguments = annotations
            .filterIsInstance<CompilerOptions>()
            .flatMap { it.options.toList() }

        val configuration = ScriptCompilationConfiguration(context.compilationConfiguration) {
            if (importedScripts.isNotEmpty()) {
                importScripts.append(importedScripts)
            }

            if (compilerArguments.isNotEmpty()) {
                compilerOptions.append(compilerArguments)
            }
        }

        if (importedScripts.isNotEmpty()) {
            logger.debug("Imported {} script(s) for {}", importedScripts.size, context.script.name)
        }

        return ResultWithDiagnostics.Success(configuration)
    }

    private fun ScriptConfigurationRefinementContext.collectedFileAnnotations(): List<ScriptSourceAnnotation<*>> {
        return collectedData?.get(ScriptCollectedData.collectedAnnotations).orEmpty()
    }

    private fun ScriptConfigurationRefinementContext.resolveImportPath(path: String): Path {
        val importPath = Path(path)
        if (importPath.isAbsolute) {
            return importPath.normalize()
        }

        val scriptPath = (script as? FileBasedScriptSource)?.file?.toPath()
        val scriptDirectory = scriptPath?.parent ?: Path(".")
        return scriptDirectory.resolve(importPath).normalize()
    }
}
