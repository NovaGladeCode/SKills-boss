package com.novaglade.skillsboss.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class BossListener implements Listener {

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        EntityType type = event.getEntityType();

        if (type == EntityType.WARDEN || type == EntityType.WITHER || type == EntityType.ENDER_DRAGON) {
            String bossName = type.name().replace("_", " ");

            Component broadcast = Component.text("BOSS SLAIN! ", NamedTextColor.RED)
                    .decorate(TextDecoration.BOLD)
                    .append(Component.text(bossName + " has been defeated!", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.BOLD, false));

            event.getEntity().getServer().broadcast(broadcast);
        }
    }
}
