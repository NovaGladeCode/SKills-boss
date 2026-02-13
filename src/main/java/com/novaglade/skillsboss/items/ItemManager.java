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
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

public class ItemManager {

    private static final NamespacedKey CUSTOM_ITEM_KEY = new NamespacedKey(SkillsBoss.getInstance(),
            "custom_legendary_item");
    private static final NamespacedKey BOSS_SPAWN_KEY = new NamespacedKey(SkillsBoss.getInstance(),
            "boss_spawn_item");
    private static final NamespacedKey PORTAL_FRAME_KEY = new NamespacedKey(SkillsBoss.getInstance(),
            "portal_frame");
    private static final NamespacedKey PORTAL_CORE_KEY = new NamespacedKey(SkillsBoss.getInstance(),
            "portal_core");
    private static final NamespacedKey BOSS_SPAWNER_KEY = new NamespacedKey(SkillsBoss.getInstance(),
            "boss_spawner_item");
    private static final NamespacedKey PROGRESSION_1_KEY = new NamespacedKey(SkillsBoss.getInstance(),
            "progression_1_item");

    public static ItemStack createBossSpawnerItem() {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    Component.text("Avernus Ritual Spawner", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("The gateway through which guards emerge.", NamedTextColor.LIGHT_PURPLE));
            lore.add(Component.empty());
            lore.add(Component.text("Phase 1: Place this where enemies should spawn.", NamedTextColor.GRAY));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(BOSS_SPAWNER_KEY, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isBossSpawnerItem(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(BOSS_SPAWNER_KEY, PersistentDataType.BYTE);
    }

    public static ItemStack createBossSpawnItem() {
        ItemStack item = new ItemStack(Material.RESPAWN_ANCHOR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    Component.text("Avernus Ritual Core", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("A pulsing core that anchors the ritual site.", NamedTextColor.RED));
            lore.add(Component.empty());
            lore.add(Component.text("Place this to begin the Manifestation.", NamedTextColor.GRAY,
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
            meta.lore(lore);
            meta.setUnbreakable(false);

            if (meta instanceof ArmorMeta) {
                ArmorMeta armorMeta = (ArmorMeta) meta;
                armorMeta.setTrim(new ArmorTrim(TrimMaterial.NETHERITE, TrimPattern.SILENCE));
            }

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

    public static ItemStack createPortalFrameBlock() {
        ItemStack item = new ItemStack(Material.BARRIER, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    Component.text("Portal Frame Block", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("A dimensional anchor used to shape the portal.", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("Build your custom portal shape with these.", NamedTextColor.DARK_PURPLE,
                    TextDecoration.ITALIC));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(PORTAL_FRAME_KEY, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isPortalFrameBlock(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(PORTAL_FRAME_KEY, PersistentDataType.BYTE);
    }

    public static ItemStack createPortalCoreBlock() {
        ItemStack item = new ItemStack(Material.CRYING_OBSIDIAN, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    Component.text("Portal Ignition Core", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Place this as the heart of your portal.", NamedTextColor.YELLOW));
            lore.add(Component.text("Ignite this block to activate the frame.", NamedTextColor.GRAY));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(PORTAL_CORE_KEY, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isPortalCoreBlock(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(PORTAL_CORE_KEY, PersistentDataType.BYTE);
    }

    public static ItemStack createProgression1Item() {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    Component.text("Progression I Catalyst", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("A beacon of hope that signals a new era.", NamedTextColor.YELLOW));
            lore.add(Component.empty());
            lore.add(Component.text("Place this to trigger the Progression I ritual.", NamedTextColor.GRAY,
                    TextDecoration.ITALIC));
            lore.add(Component.text("This location becomes the center of the ceremony.", NamedTextColor.GRAY,
                    TextDecoration.ITALIC));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(PROGRESSION_1_KEY, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isProgression1Item(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(PROGRESSION_1_KEY, PersistentDataType.BYTE);
    }
}
