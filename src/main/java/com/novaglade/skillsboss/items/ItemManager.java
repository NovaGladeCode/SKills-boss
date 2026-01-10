package com.novaglade.skillsboss.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import com.novaglade.skillsboss.SkillsBoss;

import java.util.ArrayList;
import java.util.List;

public class ItemManager {

    private static final NamespacedKey CUSTOM_ITEM_KEY = new NamespacedKey(SkillsBoss.getInstance(),
            "custom_legendary_item");
    private static final NamespacedKey BOSS_SPAWN_KEY = new NamespacedKey(SkillsBoss.getInstance(),
            "boss_spawn_item");
    private static final NamespacedKey PORTAL_OBSIDIAN_KEY = new NamespacedKey(SkillsBoss.getInstance(),
            "portal_obsidian");

    public static ItemStack createBossSpawnItem() {
        ItemStack item = new ItemStack(Material.GHAST_SPAWN_EGG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    Component.text("Avernus Ritual Egg", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Use this to anchor the ritual site.", NamedTextColor.RED));
            lore.add(Component.empty());
            lore.add(Component.text("Requires Legendary Diamond Gear to begin the ritual.", NamedTextColor.GRAY,
                    TextDecoration.ITALIC));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(BOSS_SPAWN_KEY, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createCustomItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String name = material.name().replace("_", " ").toLowerCase();
            name = name.substring(0, 1).toUpperCase() + name.substring(1);

            meta.displayName(Component.text("Legendary " + name, NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("A powerful relic of unknown origin.", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("UNBREAKABLE", NamedTextColor.RED).decorate(TextDecoration.BOLD));

            meta.lore(lore);
            meta.setUnbreakable(true);

            // Mark as custom item using PersistentDataContainer
            meta.getPersistentDataContainer().set(CUSTOM_ITEM_KEY, PersistentDataType.BYTE, (byte) 1);

            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isCustomItem(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(CUSTOM_ITEM_KEY, PersistentDataType.BYTE);
    }

    public static boolean isBossSpawnItem(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(BOSS_SPAWN_KEY, PersistentDataType.BYTE);
    }

    public static ItemStack createPortalObsidian() {
        ItemStack item = new ItemStack(Material.CRYING_OBSIDIAN, 16);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    Component.text("Portal Obsidian", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Ancient obsidian imbued with dimensional energy.", NamedTextColor.LIGHT_PURPLE));
            lore.add(Component.empty());
            lore.add(Component.text("Build a 5-high, 3-wide frame to create a portal.", NamedTextColor.GRAY,
                    TextDecoration.ITALIC));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(PORTAL_OBSIDIAN_KEY, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isPortalObsidian(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(PORTAL_OBSIDIAN_KEY, PersistentDataType.BYTE);
    }

    private static final NamespacedKey PORTAL_IGNITER_KEY = new NamespacedKey(SkillsBoss.getInstance(),
            "portal_igniter");

    public static ItemStack createPortalIgniter() {
        ItemStack item = new ItemStack(Material.FLINT_AND_STEEL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    Component.text("Portal Igniter", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("A mystical lighter pulsing with dimensional energy.", NamedTextColor.DARK_PURPLE));
            lore.add(Component.empty());
            lore.add(Component.text("Right-click on Portal Obsidian to activate the portal.", NamedTextColor.GRAY,
                    TextDecoration.ITALIC));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(PORTAL_IGNITER_KEY, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isPortalIgniter(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(PORTAL_IGNITER_KEY, PersistentDataType.BYTE);
    }
}
