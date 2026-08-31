package com.islandwarfare.commands;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.gui.ShopGUI;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShopCommand implements CommandExecutor {

    private final IslandWarfare plugin;

    public ShopCommand(IslandWarfare plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, plugin.getConfigManager().getPrefix(), plugin.getConfigManager().getMessage("player-only"));
            return true;
        }
        ShopGUI.open(plugin, player);
        return true;
    }
}
