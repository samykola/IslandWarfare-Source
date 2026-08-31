package com.islandwarfare.utils;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Small helper for colorizing and formatting messages coming from config.yml.
 */
public final class MessageUtil {

    private MessageUtil() {
    }

    public static String color(String input) {
        if (input == null) return "";
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    public static String placeholders(String input, Map<String, String> map) {
        String result = input;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public static void send(CommandSender sender, String prefix, String message) {
        if (message == null || message.isEmpty()) return;
        sender.sendMessage(color(prefix + message));
    }
}
