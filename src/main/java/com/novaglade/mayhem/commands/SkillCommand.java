package com.novaglade.mayhem.commands;

import com.novaglade.mayhem.MayhemCore;
import com.novaglade.mayhem.skills.PlayerData;
import com.novaglade.mayhem.skills.SkillType;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SkillCommand implements CommandExecutor {

    private final MayhemCore plugin;

    public SkillCommand(MayhemCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is for players.");
            return true;
        }

        PlayerData data = plugin.getSkillManager().getPlayerData(player.getUniqueId());

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "--- YOUR SKILLS ---");
        for (SkillType type : SkillType.values()) {
            int level = data.getLevel(type);
            double exp = data.getExp(type);
            double needed = data.getNeededExp(level);
            double percent = (exp / needed) * 100;

            player.sendMessage(ChatColor.YELLOW + type.getDisplayName() + ": " + ChatColor.WHITE + "Level " + level);
            player.sendMessage(ChatColor.GRAY + "Progress: " + String.format("%.1f", percent) + "% (" + (int) exp + "/"
                    + (int) needed + ")");
        }
        player.sendMessage("");

        return true;
    }
}
