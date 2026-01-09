package com.novaglade.skillsboss.listeners;

import com.novaglade.skillsboss.SkillsBoss;
import com.novaglade.skillsboss.items.ItemManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

public class ProgressionListener implements Listener {

    private static final Set<Material> RESTRICTED_ITEMS = EnumSet.of(
            Material.DIAMOND_HELMET,
            Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_BOOTS,
            Material.DIAMOND_SWORD,
            Material.NETHERITE_HELMET,
            Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS,
            Material.NETHERITE_BOOTS,
            Material.NETHERITE_SWORD,
            Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (SkillsBoss.getProgressionLevel() < 1)
            return;

        Player player = (Player) event.getWhoClicked();
        if (player.isOp())
            return;

        // check current item, cursor, AND everything else locally
        checkAndRemove(player);

        ItemStack item = event.getCurrentItem();
        if (item != null && RESTRICTED_ITEMS.contains(item.getType())) {
            if (!ItemManager.isCustomItem(item)) {
                event.setCurrentItem(null);
                player.sendMessage(net.kyori.adventure.text.Component.text("This gear is forbidden in Phase One!",
                        net.kyori.adventure.text.format.NamedTextColor.RED));
            }
        }

        ItemStack cursor = event.getCursor();
        if (cursor != null && RESTRICTED_ITEMS.contains(cursor.getType())) {
            if (!ItemManager.isCustomItem(cursor)) {
                event.setCursor(null);
                player.sendMessage(net.kyori.adventure.text.Component.text("This gear is forbidden in Phase One!",
                        net.kyori.adventure.text.format.NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (SkillsBoss.getProgressionLevel() < 1)
            return;
        if (!(event.getEntity() instanceof Player player))
            return;
        if (player.isOp())
            return;

        ItemStack item = event.getItem().getItemStack();
        if (RESTRICTED_ITEMS.contains(item.getType())) {
            if (!ItemManager.isCustomItem(item)) {
                event.setCancelled(true);
                event.getItem().remove();
                player.sendMessage(net.kyori.adventure.text.Component.text("You cannot pick up this gear in Phase One!",
                        net.kyori.adventure.text.format.NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onPrepareCraft(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
        if (SkillsBoss.getProgressionLevel() < 1)
            return;

        boolean anyNonOp = event.getViewers().stream().anyMatch(v -> v instanceof Player p && !p.isOp());
        if (!anyNonOp)
            return;

        ItemStack result = event.getInventory().getResult();
        if (result != null && RESTRICTED_ITEMS.contains(result.getType())) {
            if (!ItemManager.isCustomItem(result)) {
                event.getInventory().setResult(null);
            }
        }
    }

    @EventHandler
    public void onPortal(org.bukkit.event.player.PlayerPortalEvent event) {
        if (SkillsBoss.getProgressionLevel() < 1)
            return;
        if (event.getTo() != null && event.getTo().getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(net.kyori.adventure.text.Component.text("The Nether is sealed in Phase One!",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onEnchant(org.bukkit.event.enchantment.EnchantItemEvent event) {
        if (SkillsBoss.getProgressionLevel() < 1)
            return;

        // Block Protection III+ locally
        event.getEnchantsToAdd().entrySet().removeIf(
                entry -> entry.getKey().equals(org.bukkit.enchantments.Enchantment.PROTECTION) && entry.getValue() > 2);
    }

    @EventHandler
    public void onVillagerInteract(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (SkillsBoss.getProgressionLevel() < 1)
            return;
        if (event.getInventory().getType() == org.bukkit.event.inventory.InventoryType.MERCHANT) {
            if (event.getPlayer() instanceof Player p && !p.isOp()) {
                event.setCancelled(true);
                p.sendMessage(
                        net.kyori.adventure.text.Component.text("Villagers are too terrified to trade in Phase One!",
                                net.kyori.adventure.text.format.NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (SkillsBoss.getProgressionLevel() < 1)
            return;
        checkAndRemove(event.getPlayer());
    }

    private void checkAndRemove(Player player) {
        if (player.isOp())
            return;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && RESTRICTED_ITEMS.contains(item.getType())) {
                if (!ItemManager.isCustomItem(item)) {
                    player.getInventory().remove(item);
                }
            }
        }
    }
}
