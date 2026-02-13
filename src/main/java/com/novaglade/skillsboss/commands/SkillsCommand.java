package com.novaglade.skillsboss.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class SkillsCommand implements CommandExecutor {

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (args.length == 0) {
                        sendHelp(sender);
                        return true;
                }

                String subCommand = args[0].toLowerCase();

                switch (subCommand) {
                        case "recipe":
                                if (!(sender instanceof Player)) {
                                        sender.sendMessage(Component.text("Only players can use this command!",
                                                        NamedTextColor.RED));
                                        return true;
                                }
                                openRecipeGUI((Player) sender);
                                break;
                        default:
                                sendHelp(sender);
                                break;
                }
                return true;
        }

        private void openRecipeGUI(Player player) {
                Inventory gui = Bukkit.createInventory(null, 45,
                                Component.text("✦ Custom Recipes ✦", NamedTextColor.GOLD)
                                                .decorate(TextDecoration.BOLD));

                // Fill border with purple stained glass panes
                ItemStack border = createGuiItem(Material.PURPLE_STAINED_GLASS_PANE, " ");
                for (int i = 0; i < 45; i++) {
                        gui.setItem(i, border);
                }

                // Title item in top center
                ItemStack titleItem = createGuiItem(Material.ENCHANTING_TABLE,
                                "§6§lEnchanting Table Recipe",
                                "§7A custom recipe for the", "§7Enchanting Table", "", "§eCraft with Amethyst Shards!");
                gui.setItem(4, titleItem);

                // Crafting grid (3x3) - slots 19,20,21 / 28,29,30 / 37,38,39
                // Row 1
                gui.setItem(19, createGuiItem(Material.AMETHYST_SHARD, "§dAmethyst Shard"));
                gui.setItem(20, createGuiItem(Material.AMETHYST_SHARD, "§dAmethyst Shard"));
                gui.setItem(21, createGuiItem(Material.AMETHYST_SHARD, "§dAmethyst Shard"));
                // Row 2
                gui.setItem(28, createGuiItem(Material.AMETHYST_SHARD, "§dAmethyst Shard"));
                gui.setItem(29, createGuiItem(Material.BOOK, "§fBook"));
                gui.setItem(30, createGuiItem(Material.AMETHYST_SHARD, "§dAmethyst Shard"));
                // Row 3
                gui.setItem(37, createGuiItem(Material.AMETHYST_SHARD, "§dAmethyst Shard"));
                gui.setItem(38, createGuiItem(Material.AMETHYST_SHARD, "§dAmethyst Shard"));
                gui.setItem(39, createGuiItem(Material.AMETHYST_SHARD, "§dAmethyst Shard"));

                // Arrow pointing to result
                gui.setItem(23, createGuiItem(Material.ARROW, "§e§l➜ Result"));
                gui.setItem(32, createGuiItem(Material.ARROW, "§e§l➜ Result"));

                // Result item
                ItemStack result = createGuiItem(Material.ENCHANTING_TABLE,
                                "§5§lEnchanting Table",
                                "§7The result of this recipe!", "", "§8Place in a crafting table to craft.");
                gui.setItem(25, result);

                player.openInventory(gui);
        }

        private ItemStack createGuiItem(Material material, String name, String... loreLines) {
                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                        meta.displayName(Component.text(name));
                        if (loreLines.length > 0) {
                                java.util.ArrayList<Component> lore = new java.util.ArrayList<>();
                                for (String line : loreLines) {
                                        lore.add(Component.text(line));
                                }
                                meta.lore(lore);
                        }
                        item.setItemMeta(meta);
                }
                return item;
        }

        private void sendHelp(CommandSender sender) {
                sender.sendMessage(Component.text("--- Skills ---", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
                sender.sendMessage(Component.text("/skills recipe ", NamedTextColor.YELLOW)
                                .append(Component.text("- View custom crafting recipes", NamedTextColor.GRAY)));
                sender.sendMessage(Component.text("--------------", NamedTextColor.GOLD));
        }
}
