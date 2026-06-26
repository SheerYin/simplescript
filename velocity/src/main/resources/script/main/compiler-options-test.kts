@file:CompilerOptions("-nowarn")

context.logger.info("velocity compiler options test script {} loaded", context.scriptId)

val scriptName: String = context.scriptId
check(scriptName.isNotBlank()) {
    "script id should not be blank"
}

