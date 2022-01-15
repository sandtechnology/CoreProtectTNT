package com.mcsunnyside.coreprotecttnt;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class Util {
    public static void broadcastNearPlayers(Location location, String message) {
        if (message == null || message.isEmpty()) {
            return; // Do not send empty message
        }
        String msg = ChatColor.translateAlternateColorCodes('&', message);
        for (Entity around : location.getWorld().getNearbyEntities(location, 15, 15, 15, (entity) -> entity instanceof Player)) {
            around.sendMessage(msg);
        }

    }

    public static ConfigurationSection bakeConfigSection(Configuration configuration, String path) {
        ConfigurationSection section = configuration.getConfigurationSection(path);
        if (section == null) {
            // Create default section with default values
            section = configuration.createSection(path);
            section.set("enable", true);
            section.set("disable-unknown", true);
            section.set("alert", ChatColor.RED + "Failed to read translation, configuration section missing!");
        }
        return section;
    }
}
