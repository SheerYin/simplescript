package me.yin.simplescript.newscript

import me.yin.simplescript.SimpleScript
import java.nio.file.Path
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.ScriptEvaluator
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm

class SimpleScriptRuntime(
    private val simpleScript: SimpleScript
) {
    private val classLoader: ClassLoader = simpleScript.javaClass.classLoader
    private val compiler: SimpleScriptCompiler = SimpleScriptCompiler(
        classLoader = classLoader,
        logger = simpleScript.slF4JLogger
    )

    // Evaluation runs an already compiled script. The K1/K2 choice is fixed in
    // SimpleScriptCompiler, where the CompiledScript is produced.
    private val evaluator: ScriptEvaluator = BasicJvmScriptEvaluator()

    suspend fun compile(path: Path): ResultWithDiagnostics<CompiledScript> {
        return compiler.compile(path)
    }

    suspend fun evaluate(
        compiledScript: CompiledScript,
        context: SimpleScriptContext
    ): ResultWithDiagnostics<EvaluationResult> {
        val evaluationConfiguration: ScriptEvaluationConfiguration = ScriptEvaluationConfiguration {
            constructorArgs(context)

            jvm {
                baseClassLoader(classLoader)
            }
        }

        val result: ResultWithDiagnostics<EvaluationResult> = evaluator.invoke(
            compiledScript,
            evaluationConfiguration
        )

        return result
    }
}
