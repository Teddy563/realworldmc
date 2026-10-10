package dev.realworldmc;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.util.regex.Pattern;

/** Shared chat/action-bar style. Provider errors must never include raw URLs or credentials. */
public final class RWMessages {
    public static final int GREEN = 0x55C98B;
    private static final Pattern COMMAND = Pattern.compile("/(?:earth|rw)(?: (?:help|setup|goto|where|back|setlocation|streetview)(?: (?:here|browser|setup|legacy|height|back|exit))?)?\\b");
    private RWMessages() {}

    public static MutableComponent prefix() {
        return Component.literal("RW").withStyle(s -> s.withColor(GREEN).withBold(true))
            .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY).withStyle(s -> s.withBold(false)));
    }

    public static MutableComponent text(String value) {
        var result = Component.empty().withStyle(ChatFormatting.WHITE);
        var matcher = COMMAND.matcher(value);
        int start = 0;
        while (matcher.find()) {
            result.append(Component.literal(value.substring(start, matcher.start())));
            result.append(Component.literal(matcher.group()).withStyle(ChatFormatting.YELLOW));
            start = matcher.end();
        }
        return result.append(Component.literal(value.substring(start)));
    }

    public static MutableComponent message(String value) { return wrap(text(value)); }
    public static MutableComponent wrap(Component value) {
        return prefix().append(Component.empty().withStyle(ChatFormatting.WHITE).withStyle(s -> s.withBold(false)).append(value));
    }

    public static MutableComponent helpLine(String command, String description) {
        String suggestion = command.contains(" <") ? command.substring(0, command.indexOf(" <")) + " " : command;
        return Component.literal("\n  ").append(Component.literal(command).withStyle(ChatFormatting.YELLOW)
            .withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND, suggestion))))
            .append(Component.literal("  " + description).withStyle(ChatFormatting.WHITE));
    }
}
