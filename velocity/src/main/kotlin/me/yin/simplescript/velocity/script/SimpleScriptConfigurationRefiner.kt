package me.yin.simplescript.velocity.script

import org.slf4j.Logger
import java.nio.file.Path
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

class SimpleScriptConfigurationRefiner(
    private val logger: Logger
) {
    fun handle(
        context: ScriptConfigurationRefinementContext
    ): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val collectedData: ScriptCollectedData? = context.collectedData
        val collectedFileAnnotations: List<ScriptSourceAnnotation<*>> =
            collectedData?.get(ScriptCollectedData.collectedAnnotations).orEmpty()

        val annotations: List<Annotation> = collectedFileAnnotations.map { sourceAnnotation: ScriptSourceAnnotation<*> ->
            sourceAnnotation.annotation
        }

        val importedScripts: List<FileScriptSource> = annotations
            .filterIsInstance<Import>()
            .flatMap { annotation: Import -> annotation.paths.toList() }
            .map { importPathText: String ->
                val importPath: Path = Path.of(importPathText)
                val resolvedImportPath: Path = if (importPath.isAbsolute) {
                    importPath.normalize()
                } else {
                    val scriptPath: Path? = (context.script as? FileBasedScriptSource)?.file?.toPath()
                    val scriptDirectory: Path = scriptPath?.parent ?: Path.of(".")
                    scriptDirectory.resolve(importPath).normalize()
                }
                FileScriptSource(resolvedImportPath.toFile())
            }

        val compilerArguments: List<String> = annotations
            .filterIsInstance<CompilerOptions>()
            .flatMap { annotation: CompilerOptions -> annotation.options.toList() }

        val refinedCompilationConfiguration: ScriptCompilationConfiguration = ScriptCompilationConfiguration(
            context.compilationConfiguration
        ) {
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

        return ResultWithDiagnostics.Success(refinedCompilationConfiguration)
    }
}

