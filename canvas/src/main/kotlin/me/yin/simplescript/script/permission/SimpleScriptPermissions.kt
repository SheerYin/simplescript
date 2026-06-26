package me.yin.simplescript.script.permission

class SimpleScriptPermissions {

    @Volatile var simpleScriptCommand: String = "simplescript.command"

    @Volatile var reloadCommand: String = "simplescript.command.reload"
    @Volatile var loadCommand: String = "simplescript.command.load"
    @Volatile var unloadCommand: String = "simplescript.command.unload"
    @Volatile var listCommand: String = "simplescript.command.list"

}
