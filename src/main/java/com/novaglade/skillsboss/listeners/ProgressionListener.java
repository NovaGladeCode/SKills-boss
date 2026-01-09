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
            Material.DIAMOND_SWORD);

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (SkillsBoss.getProgressionLevel() < 1)
            return;

        Player player = (Player) event.getWhoClicked();
        if (player.isOp())
            return;

        ItemStack item = event.getCurrentItem();
        if (item != null && RESTRICTED_ITEMS.contains(item.getType())) {
            if (!ItemManager.isCustomItem(item)) {
                event.setCurrentItem(null);
                player.sendMessage(net.kyori.adventure.text.Component.text("Regular diamond gear is forbidden!",
                        net.kyori.adventure.text.format.NamedTextColor.RED));
            }
        }

        ItemStack cursor = event.getCursor();
        if (cursor != null && RESTRICTED_ITEMS.contains(cursor.getType())) {
            if (!ItemManager.isCustomItem(cursor)) {
                event.setCursor(null);
                player.sendMessage(net.kyori.adventure.text.Component.text("Regular diamond gear is forbidden!",
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
                player.sendMessage(net.kyori.adventure.text.Component.text("You cannot pick up regular diamond gear!",
                        net.kyori.adventure.text.format.NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onPrepareCraft(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
        if (SkillsBoss.getProgressionLevel() < 1)
            return;

        // PrepareItemCraftEvent doesn't easily give the player until they click,
        // but we can check the viewers.
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
}
