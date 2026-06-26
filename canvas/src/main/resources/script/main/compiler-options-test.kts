@file:CompilerOptions("-nowarn")

context.logger.info("compiler options test script {} loaded", context.scriptId)

val scriptName: String = context.scriptId

check(scriptName.isNotBlank()) {
    "script id should not be blank"
}
