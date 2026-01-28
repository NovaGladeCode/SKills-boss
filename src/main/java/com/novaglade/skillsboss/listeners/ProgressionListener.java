package com.novaglade.skillsboss.listeners;

import com.novaglade.skillsboss.SkillsBoss;
import com.novaglade.skillsboss.items.ItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class ProgressionListener implements Listener {

    public ProgressionListener() {
        // Periodic task to catch command-based changes or items that missed events
        org.bukkit.Bukkit.getScheduler().runTaskTimer(SkillsBoss.getInstance(), () -> {
            if (SkillsBoss.getProgressionLevel() == 1) {
                for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                    sanitizeInventory(player);
                }
            }
        }, 20L, 20L); // Every second
    }

    private static final Set<Material> RESTRICTED_ITEMS = EnumSet.of(
            Material.DIAMOND_HELMET,
            Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_BOOTS,
            Material.DIAMOND_SWORD,
            Material.DIAMOND_AXE,
            Material.DIAMOND_SHOVEL,
            Material.DIAMOND_HOE,
            Material.NETHERITE_HELMET,
            Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS,
            Material.NETHERITE_BOOTS,
            Material.NETHERITE_SWORD,
            Material.NETHERITE_AXE,
            Material.NETHERITE_SHOVEL,
            Material.NETHERITE_HOE,
            Material.NETHERITE_PICKAXE,
            Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (SkillsBoss.getProgressionLevel() < 1)
            return;

        if (!(event.getWhoClicked() instanceof Player))
            return;
        Player player = (Player) event.getWhoClicked();

        // Run sanitization task to handle the inventory after the click
        org.bukkit.Bukkit.getScheduler().runTask(SkillsBoss.getInstance(), () -> sanitizeInventory(player));

        if (player.isOp())
            return;

        ItemStack item = event.getCurrentItem();
        if (item != null && RESTRICTED_ITEMS.contains(item.getType())) {
            if (!ItemManager.isCustomItem(item)) {
                event.setCurrentItem(null);
                player.sendMessage(Component.text("This gear is forbidden in Phase One!",
                        NamedTextColor.RED));
            }
        }

        ItemStack cursor = event.getCursor();
        if (cursor != null && RESTRICTED_ITEMS.contains(cursor.getType())) {
            if (!ItemManager.isCustomItem(cursor)) {
                event.setCursor(null);
                player.sendMessage(Component.text("This gear is forbidden in Phase One!",
                        NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (SkillsBoss.getProgressionLevel() < 1)
            return;
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            org.bukkit.Bukkit.getScheduler().runTask(SkillsBoss.getInstance(), () -> sanitizeInventory(player));
        }
    }

    @EventHandler
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (SkillsBoss.getProgressionLevel() < 1)
            return;
        if (!(event.getEntity() instanceof Player))
            return;
        Player player = (Player) event.getEntity();
        if (player.isOp())
            return;

        ItemStack item = event.getItem().getItemStack();
        if (RESTRICTED_ITEMS.contains(item.getType())) {
            if (!ItemManager.isCustomItem(item)) {
                event.setCancelled(true);
                event.getItem().remove();
                player.sendMessage(Component.text("You cannot pick up this gear in Phase One!",
                        NamedTextColor.RED));
                return;
            }
        }

        // Sanitize the picked up item or the inventory after pickup
        org.bukkit.Bukkit.getScheduler().runTask(SkillsBoss.getInstance(), () -> sanitizeInventory(player));
    }

    @EventHandler
    public void onPrepareCraft(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
        if (SkillsBoss.getProgressionLevel() < 1)
            return;

        boolean anyNonOp = event.getViewers().stream().anyMatch(v -> {
            if (!(v instanceof Player))
                return false;
            Player p = (Player) v;
            return !p.isOp();
        });
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

        if (SkillsBoss.getProgressionLevel() == 1 && event.getTo() != null
                && event.getTo().getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(net.kyori.adventure.text.Component.text("The Nether is sealed in Phase One!",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
        }

        if (SkillsBoss.getProgressionLevel() == 2
                && event.getFrom().getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER) {
            if (event.getTo() != null
                    && event.getTo().getWorld().getEnvironment() != org.bukkit.World.Environment.NETHER) {
                event.setCancelled(true);
                event.getPlayer()
                        .sendMessage(net.kyori.adventure.text.Component.text("There is no escape from the Avernus!",
                                net.kyori.adventure.text.format.NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        if (SkillsBoss.getProgressionLevel() != 1)
            return;

        Map<Enchantment, Integer> enchants = event.getEnchantsToAdd();

        if (enchants.containsKey(Enchantment.PROTECTION) && enchants.get(Enchantment.PROTECTION) > 3) {
            enchants.put(Enchantment.PROTECTION, 3);
        }

        if (enchants.containsKey(Enchantment.SHARPNESS) && enchants.get(Enchantment.SHARPNESS) > 1) {
            enchants.put(Enchantment.SHARPNESS, 1);
        }

        enchants.remove(Enchantment.FIRE_ASPECT);
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        if (SkillsBoss.getProgressionLevel() != 1)
            return;

        ItemStack result = event.getResult();
        if (result == null || result.getType() == Material.AIR)
            return;

        boolean changed = sanitizeItem(result);
        if (changed) {
            event.setResult(result);
        }
    }

    @EventHandler
    public void onVillagerInteract(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (SkillsBoss.getProgressionLevel() < 1)
            return;
        if (event.getInventory().getType() == org.bukkit.event.inventory.InventoryType.MERCHANT) {
            if (event.getPlayer() instanceof Player) {
                Player p = (Player) event.getPlayer();
                if (!p.isOp()) {
                    event.setCancelled(true);
                    p.sendMessage(
                            net.kyori.adventure.text.Component.text(
                                    "Villagers are too terrified to trade in Phase One!",
                                    net.kyori.adventure.text.format.NamedTextColor.RED));
                }
            }
        }
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (SkillsBoss.getProgressionLevel() < 1)
            return;
        sanitizeInventory(event.getPlayer());
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (SkillsBoss.getProgressionLevel() == 1) {
            sanitizeInventory(event.getPlayer());
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (SkillsBoss.getProgressionLevel() == 1) {
            sanitizeInventory(event.getPlayer());
        }
    }

    private void sanitizeInventory(Player player) {
        if (SkillsBoss.getProgressionLevel() != 1)
            return;

        int gapCount = 0;
        int cobwebCount = 0;

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR)
                continue;

            // 1. Restricted items (Diamond/Netherite)
            if (RESTRICTED_ITEMS.contains(item.getType())) {
                if (!ItemManager.isCustomItem(item)) {
                    if (!player.isOp()) { // Still allow OPs to hold forbidden gear if they want, but sanitize enchants
                                          // for everyone
                        player.getInventory().setItem(i, null);
                        continue;
                    }
                }
            }

            // 2. Enchantment limits
            sanitizeItem(item);

            // 3. Gap and Cobweb limits
            // Gaps/Cobwebs are restricted for everyone in Progression 1
            if (item.getType() == Material.GOLDEN_APPLE || item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
                if (gapCount >= 8) {
                    player.getInventory().setItem(i, null);
                } else if (gapCount + item.getAmount() > 8) {
                    item.setAmount(8 - gapCount);
                    gapCount = 8;
                } else {
                    gapCount += item.getAmount();
                }
            } else if (item.getType() == Material.COBWEB) {
                if (cobwebCount >= 8) {
                    player.getInventory().setItem(i, null);
                } else if (cobwebCount + item.getAmount() > 8) {
                    item.setAmount(8 - cobwebCount);
                    cobwebCount = 8;
                } else {
                    cobwebCount += item.getAmount();
                }
            }
        }
    }

    private boolean sanitizeItem(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return false;
        boolean changed = false;

        if (meta.hasEnchant(Enchantment.PROTECTION)) {
            if (meta.getEnchantLevel(Enchantment.PROTECTION) > 3) {
                meta.addEnchant(Enchantment.PROTECTION, 3, true);
                changed = true;
            }
        }

        if (meta.hasEnchant(Enchantment.SHARPNESS)) {
            if (meta.getEnchantLevel(Enchantment.SHARPNESS) > 1) {
                meta.addEnchant(Enchantment.SHARPNESS, 1, true);
                changed = true;
            }
        }

        if (meta.hasEnchant(Enchantment.FIRE_ASPECT)) {
            meta.removeEnchant(Enchantment.FIRE_ASPECT);
            changed = true;
        }

        if (changed) {
            item.setItemMeta(meta);
        }
        return changed;
    }
}
