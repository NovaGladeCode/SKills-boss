package com.novaglade.skillsboss.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

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
                showRecipes(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void showRecipes(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("═══════ Custom Recipes ═══════", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.empty());

        // Enchanting Table Recipe
        sender.sendMessage(Component.text("  ✦ Enchanting Table", NamedTextColor.LIGHT_PURPLE)
                .decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("  [A] [A] [A]", NamedTextColor.DARK_PURPLE)
                .append(Component.text("   A = Amethyst Shard", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  [A] [B] [A]", NamedTextColor.DARK_PURPLE)
                .append(Component.text("   B = Book", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  [A] [A] [A]", NamedTextColor.DARK_PURPLE));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("  Result: ", NamedTextColor.GRAY)
                .append(Component.text("Enchanting Table", NamedTextColor.LIGHT_PURPLE)
                        .decorate(TextDecoration.BOLD)));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("══════════════════════════════", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("--- Skills ---", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("/skills recipe ", NamedTextColor.YELLOW)
                .append(Component.text("- View custom crafting recipes", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("--------------", NamedTextColor.GOLD));
    }
}
