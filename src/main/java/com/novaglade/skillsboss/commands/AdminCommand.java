package com.novaglade.skillsboss.commands;

import com.novaglade.skillsboss.SkillsBoss;
import com.novaglade.skillsboss.items.ItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public class AdminCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        if (!sender.isOp()) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "progression":
                handleProgression(sender, args);
                break;
            case "give":
                handleGive(sender, args);
                break;
            case "reload":
                sender.sendMessage(Component.text("SkillsBoss configuration reloaded!", NamedTextColor.GREEN));
                break;
            case "version":
                sender.sendMessage(
                        Component.text("SkillsBoss Version: 1.0-SNAPSHOT (Paper 1.21.11)", NamedTextColor.AQUA));
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleProgression(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /admin progression <0|1>", NamedTextColor.RED));
            return;
        }

        int level;
        try {
            level = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid level.", NamedTextColor.RED));
            return;
        }

        if (level == 0) {
            SkillsBoss.setProgressionLevel(0);
            player.getWorld().setSpawnLocation(player.getLocation());
            player.getWorld().getWorldBorder().setCenter(player.getLocation());
            player.getWorld().getWorldBorder().setSize(10);

            for (Player online : Bukkit.getOnlinePlayers()) {
                online.teleport(player.getLocation());
            }

            sender.sendMessage(Component.text("Progression 0 set: Spawn updated, All players TPed, Border set to 10.",
                    NamedTextColor.GREEN));
        } else if (level == 1) {
            startProgressionOneCountdown();
        } else {
            sender.sendMessage(Component.text("Unknown progression level.", NamedTextColor.RED));
        }
    }

    private void startProgressionOneCountdown() {
        new BukkitRunnable() {
            int count = 5;

            @Override
            public void run() {
                if (count > 0) {
                    Component mainTitle = Component.text(String.valueOf(count), NamedTextColor.RED,
                            TextDecoration.BOLD);
                    Component subTitle = Component.text("Starting Progression 1...", NamedTextColor.YELLOW);
                    Title title = Title.title(mainTitle, subTitle,
                            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(600), Duration.ofMillis(200)));

                    for (Player online : Bukkit.getOnlinePlayers()) {
                        online.showTitle(title);
                    }
                    count--;
                } else {
                    SkillsBoss.setProgressionLevel(1);
                    Component startMsg = Component.text("PROGRESSION 1 STARTED!", NamedTextColor.GREEN,
                            TextDecoration.BOLD);
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        online.sendMessage(startMsg);
                        online.showTitle(Title.title(startMsg,
                                Component.text("Diamond armor is now restricted!", NamedTextColor.RED)));
                    }
                    cancel();
                }
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 0, 20);
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /admin give <diamondarmor>", NamedTextColor.RED));
            return;
        }

        if (args[1].equalsIgnoreCase("diamondarmor")) {
            player.getInventory().addItem(ItemManager.createCustomDiamondArmor(Material.DIAMOND_HELMET));
            player.getInventory().addItem(ItemManager.createCustomDiamondArmor(Material.DIAMOND_CHESTPLATE));
            player.getInventory().addItem(ItemManager.createCustomDiamondArmor(Material.DIAMOND_LEGGINGS));
            player.getInventory().addItem(ItemManager.createCustomDiamondArmor(Material.DIAMOND_BOOTS));
            sender.sendMessage(Component.text("Received custom diamond armor!", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Unknown item.", NamedTextColor.RED));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(
                Component.text("--- SkillsBoss Admin ---", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("/admin progression <0|1> ", NamedTextColor.YELLOW)
                .append(Component.text("- Start progression stages", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin give diamondarmor ", NamedTextColor.YELLOW)
                .append(Component.text("- Give legendary armor", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin reload ", NamedTextColor.YELLOW)
                .append(Component.text("- Reload plugin config", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin version ", NamedTextColor.YELLOW)
                .append(Component.text("- Show plugin version", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("------------------------", NamedTextColor.GOLD));
    }
}
