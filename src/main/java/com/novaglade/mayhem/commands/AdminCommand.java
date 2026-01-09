package com.novaglade.mayhem.commands;

import com.novaglade.mayhem.MayhemCore;
import com.novaglade.mayhem.boss.BossManager;
import com.novaglade.mayhem.skills.PlayerData;
import com.novaglade.mayhem.skills.SkillType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AdminCommand implements CommandExecutor {

    private final MayhemCore plugin;

    public AdminCommand(MayhemCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("mayhem.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn":
                if (sender instanceof Player player) {
                    plugin.getBossManager().spawnBoss(player.getLocation());
                    sender.sendMessage(ChatColor.GREEN + "Boss spawned!");
                }
                break;
            case "stop":
                plugin.getBossManager().clearBosses();
                sender.sendMessage(ChatColor.YELLOW + "Bosses cleared.");
                break;
            case "give":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /admin give <player> <titan_slayer|chaos_core>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }
                if (args[2].equalsIgnoreCase("titan_slayer")) {
                    target.getInventory().addItem(plugin.getItemManager().createTitanSlayer());
                    sender.sendMessage(ChatColor.GREEN + "Gave Titan Slayer to " + target.getName());
                } else if (args[2].equalsIgnoreCase("chaos_core")) {
                    target.getInventory().addItem(plugin.getItemManager().createChaosCore());
                    sender.sendMessage(ChatColor.GREEN + "Gave Chaos Core to " + target.getName());
                }
                break;
            case "setskill":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /admin setskill <player> <skill> <level>");
                    return true;
                }
                Player skillTarget = Bukkit.getPlayer(args[1]);
                if (skillTarget == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }
                try {
                    SkillType type = SkillType.valueOf(args[2].toUpperCase());
                    int level = Integer.parseInt(args[3]);
                    PlayerData data = plugin.getSkillManager().getPlayerData(skillTarget.getUniqueId());
                    data.getSkillLevels().put(type, level);
                    sender.sendMessage(ChatColor.GREEN + "Set " + skillTarget.getName() + "'s " + type.name()
                            + " to level " + level);
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Invalid skill or level.");
                }
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "--- Mayhem Admin Help ---");
        sender.sendMessage(ChatColor.YELLOW + "/admin spawn" + ChatColor.WHITE + " - Spawn Mayhem Boss");
        sender.sendMessage(ChatColor.YELLOW + "/admin stop" + ChatColor.WHITE + " - Clear Bosses");
        sender.sendMessage(ChatColor.YELLOW + "/admin give <player> <item>" + ChatColor.WHITE + " - Give custom items");
        sender.sendMessage(
                ChatColor.YELLOW + "/admin setskill <player> <skill> <level>" + ChatColor.WHITE + " - Set skill level");
    }
}
