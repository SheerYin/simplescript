import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

fun greenMessage(message: String): Component {
    return Component.text(message, NamedTextColor.GREEN)
}

