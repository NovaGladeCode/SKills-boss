package com.novaglade.skillsboss.commands;

import com.novaglade.skillsboss.SkillsBoss;
import com.novaglade.skillsboss.items.ItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;

public class AdminCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

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
            case "reset":
                handleReset(sender);
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
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }
        Player player = (Player) sender;

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
            player.getWorld().getWorldBorder().setSize(19);

            for (Player online : Bukkit.getOnlinePlayers()) {
                online.teleport(player.getLocation());
            }

            sender.sendMessage(Component.text("Progression 0 set: Spawn updated, All players TPed, Border set to 19.",
                    NamedTextColor.GREEN));
        } else if (level == 1) {
            startProgressionOneCountdown(player.getWorld());
        } else {
            sender.sendMessage(Component.text("Unknown progression level.", NamedTextColor.RED));
        }
    }

    private void startProgressionOneCountdown(org.bukkit.World world) {
        Location center = world.getSpawnLocation();

        new BukkitRunnable() {
            int maxTicks = 10 * 20; // 10 seconds
            int ticks = maxTicks;

            @Override
            public void run() {
                try {
                    int seconds = (int) Math.ceil(ticks / 20.0);

                    if (ticks % 20 == 0 && seconds > 0) {
                        Component mainTitle = Component.text(String.valueOf(seconds), NamedTextColor.RED,
                                TextDecoration.BOLD);
                        Component subTitle = Component.text("Starting Progression I: A New Beginning...",
                                NamedTextColor.YELLOW);
                        Title title = Title.title(mainTitle, subTitle,
                                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(800),
                                        Duration.ofMillis(100)));

                        for (Player online : Bukkit.getOnlinePlayers()) {
                            if (online.getWorld().equals(world)) {
                                online.showTitle(title);
                                online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f,
                                        0.5f + (seconds * 0.1f));
                            }
                        }
                    }

                    if (ticks <= 0) {
                        SkillsBoss.setProgressionLevel(1);
                        Component mainTitle = Component.text("PROGRESSION I", NamedTextColor.GREEN,
                                TextDecoration.BOLD);
                        Component subTitle = Component.text("A NEW BEGINNING", NamedTextColor.DARK_AQUA,
                                TextDecoration.BOLD);
                        Title finalTitle = Title.title(mainTitle, subTitle,
                                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3),
                                        Duration.ofSeconds(1)));

                        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                        world.playSound(center, Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
                        world.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

                        world.getWorldBorder().setCenter(center);
                        world.getWorldBorder().setSize(750);
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            if (online.getWorld().equals(world)) {
                                online.sendMessage(Component
                                        .text("PROGRESSION I: ", NamedTextColor.GREEN, TextDecoration.BOLD)
                                        .append(Component
                                                .text("A New Beginning", NamedTextColor.DARK_AQUA,
                                                        TextDecoration.BOLD)));
                                online.showTitle(finalTitle);
                            }
                        }
                        cancel();
                    }
                    ticks--;
                } catch (Exception e) {
                    Bukkit.getLogger().severe("[SkillsBoss] Error in Progression I countdown: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 0, 1);
    }

    private void handleReset(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }
        Player player = (Player) sender;

        SkillsBoss.setProgressionLevel(0);
        player.getWorld().setSpawnLocation(player.getLocation());
        player.getWorld().getWorldBorder().setCenter(player.getLocation());
        player.getWorld().getWorldBorder().setSize(19);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.teleport(player.getLocation());
        }

        sender.sendMessage(
                Component.text("World reset! Progression set to 0, all players teleported to spawn, border set to 19.",
                        NamedTextColor.GREEN));
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }
        Player player = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /admin give <diamondarmor|waveboss|portal>", NamedTextColor.RED));
            return;
        }

        if (args[1].equalsIgnoreCase("diamondarmor")) {
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_HELMET));
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_CHESTPLATE));
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_LEGGINGS));
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_BOOTS));
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_SWORD));
            sender.sendMessage(Component.text("Received custom diamond armor and sword!", NamedTextColor.GREEN));
        } else if (args[1].equalsIgnoreCase("bossspawn1") || args[1].equalsIgnoreCase("waveboss")) {
            player.getInventory().addItem(ItemManager.createBossSpawnItem());
            sender.sendMessage(Component.text("Received Boss Spawn Altar!", NamedTextColor.LIGHT_PURPLE));
        } else if (args[1].equalsIgnoreCase("portal")) {
            player.getInventory().addItem(ItemManager.createPortalObsidian());
            sender.sendMessage(Component.text("Received 16x Portal Obsidian!", NamedTextColor.DARK_PURPLE));
        } else {
            sender.sendMessage(Component.text("Unknown item.", NamedTextColor.RED));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(
                Component.text("--- SkillsBoss Admin ---", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("/admin progression <0|1> ", NamedTextColor.YELLOW)
                .append(Component.text("- Start progression stages", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin give <diamondarmor|waveboss|portal> ", NamedTextColor.YELLOW)
                .append(Component.text("- Give legendary gear, boss altar, or portal obsidian", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin reset ", NamedTextColor.YELLOW)
                .append(Component.text("- Reset world: TP all to spawn, set progression to 0", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin reload ", NamedTextColor.YELLOW)
                .append(Component.text("- Reload plugin config", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin version ", NamedTextColor.YELLOW)
                .append(Component.text("- Show plugin version", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("------------------------", NamedTextColor.GOLD));
    }
}
