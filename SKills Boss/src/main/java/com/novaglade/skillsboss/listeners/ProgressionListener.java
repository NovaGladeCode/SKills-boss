package com.novaglade.skillsboss.listeners;

import com.novaglade.skillsboss.SkillsBoss;
import com.novaglade.skillsboss.items.ItemManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

public class ProgressionListener implements Listener {

    private static final Set<Material> DIAMOND_ARMOR = EnumSet.of(
            Material.DIAMOND_HELMET,
            Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_BOOTS);

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (SkillsBoss.getProgressionLevel() < 1)
            return;

        Player player = (Player) event.getWhoClicked();
        if (player.isOp())
            return; // Allow OPs to have whatever

        ItemStack item = event.getCurrentItem();
        if (item != null && DIAMOND_ARMOR.contains(item.getType())) {
            if (!ItemManager.isCustomDiamondArmor(item)) {
                event.setCurrentItem(null);
                player.sendMessage(net.kyori.adventure.text.Component.text("Regular diamond armor is forbidden!",
                        net.kyori.adventure.text.format.NamedTextColor.RED));
            }
        }

        ItemStack cursor = event.getCursor();
        if (cursor != null && DIAMOND_ARMOR.contains(cursor.getType())) {
            if (!ItemManager.isCustomDiamondArmor(cursor)) {
                event.setCursor(null);
                player.sendMessage(net.kyori.adventure.text.Component.text("Regular diamond armor is forbidden!",
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
        if (DIAMOND_ARMOR.contains(item.getType())) {
            if (!ItemManager.isCustomDiamondArmor(item)) {
                event.setCancelled(true);
                event.getItem().remove();
                player.sendMessage(net.kyori.adventure.text.Component.text("You cannot pick up regular diamond armor!",
                        net.kyori.adventure.text.format.NamedTextColor.RED));
            }
        }
    }
}
