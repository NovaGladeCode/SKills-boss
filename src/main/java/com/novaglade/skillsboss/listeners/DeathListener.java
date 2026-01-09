package com.novaglade.skillsboss.listeners;

import com.novaglade.skillsboss.items.ItemManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DeathListener implements Listener {

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        List<ItemStack> customItems = new ArrayList<>();

        // Filter out legendary items from drops
        Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            if (ItemManager.isCustomItem(drop)) {
                customItems.add(new ItemStack(drop)); // Copy item
                iterator.remove(); // Don't drop it on the ground
            }
        }

        if (!customItems.isEmpty()) {
            Location deathLoc = player.getLocation();

            // Search upwards for first non-liquid, non-solid block to spawn safely (e.g. if
            // in lava)
            while (deathLoc.getBlock().isLiquid() || deathLoc.getBlock().getType().isSolid()) {
                deathLoc.add(0, 1, 0);
                if (deathLoc.getY() > 319)
                    break; // Ceiling check
            }

            // Place glass block at feet
            deathLoc.getBlock().setType(Material.GLASS);

            // Spawn armor stand on top of glass
            Location standLoc = deathLoc.clone().add(0, 1, 0);
            ArmorStand stand = (ArmorStand) deathLoc.getWorld().spawnEntity(standLoc, EntityType.ARMOR_STAND);

            stand.setBasePlate(false);
            stand.setArms(true);
            stand.setCustomNameVisible(false);

            for (ItemStack item : customItems) {
                Material type = item.getType();
                if (type == Material.DIAMOND_HELMET)
                    stand.getEquipment().setHelmet(item);
                else if (type == Material.DIAMOND_CHESTPLATE)
                    stand.getEquipment().setChestplate(item);
                else if (type == Material.DIAMOND_LEGGINGS)
                    stand.getEquipment().setLeggings(item);
                else if (type == Material.DIAMOND_BOOTS)
                    stand.getEquipment().setBoots(item);
                else if (type == Material.DIAMOND_SWORD)
                    stand.getEquipment().setItemInMainHand(item);
            }
        }
    }
}
