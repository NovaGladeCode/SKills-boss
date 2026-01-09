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

    private static final NamespacedKey CUSTOM_ARMOR_KEY = new NamespacedKey(SkillsBoss.getInstance(),
            "custom_diamond_armor");

    public static ItemStack createCustomDiamondArmor(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String name = material.name().replace("_", " ").toLowerCase();
            name = name.substring(0, 1).toUpperCase() + name.substring(1);

            meta.displayName(Component.text("Legendary " + name, NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("A specialized suit of armor", NamedTextColor.GRAY));
            lore.add(Component.text("given by the admins.", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("UNBREAKABLE", NamedTextColor.RED).decorate(TextDecoration.BOLD));

            meta.lore(lore);
            meta.setUnbreakable(true);

            // Mark as custom armor using PersistentDataContainer
            meta.getPersistentDataContainer().set(CUSTOM_ARMOR_KEY, PersistentDataType.BYTE, (byte) 1);

            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isCustomDiamondArmor(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(CUSTOM_ARMOR_KEY, PersistentDataType.BYTE);
    }
}
