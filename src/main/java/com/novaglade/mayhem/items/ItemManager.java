package com.novaglade.mayhem.items;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import com.novaglade.mayhem.MayhemCore;

import java.util.ArrayList;
import java.util.List;

public class ItemManager {

    private final MayhemCore plugin;
    public static final NamespacedKey CUSTOM_ITEM_KEY = new NamespacedKey("mayhem", "custom_id");

    public ItemManager(MayhemCore plugin) {
        this.plugin = plugin;
    }

    public ItemStack createTitanSlayer() {
        ItemStack item = new ItemStack(Material.TRIDENT);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Titan Slayer Spear");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "A legendary weapon forged from");
            lore.add(ChatColor.GRAY + "the remains of a Mayhem Titan.");
            lore.add("");
            lore.add(ChatColor.RED + "Passive: Giant Slayer");
            lore.add(ChatColor.DARK_GRAY + "Deals 2.0x damage to bosses.");
            lore.add("");
            lore.add(ChatColor.GOLD + "RARITY: LEGENDARY");
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(CUSTOM_ITEM_KEY, PersistentDataType.STRING, "titan_slayer");
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createChaosCore() {
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Chaos Core");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Vibrating with raw energy.");
            lore.add(ChatColor.GRAY + "Used to summon Titans or");
            lore.add(ChatColor.GRAY + "power up your Mayhem skill.");
            lore.add("");
            lore.add(ChatColor.LIGHT_PURPLE + "RARITY: EPIC");
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(CUSTOM_ITEM_KEY, PersistentDataType.STRING, "chaos_core");
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isCustomItem(ItemStack item, String id) {
        if (item == null || !item.hasItemMeta())
            return false;
        String itemId = item.getItemMeta().getPersistentDataContainer().get(CUSTOM_ITEM_KEY, PersistentDataType.STRING);
        return id.equals(itemId);
    }
}
