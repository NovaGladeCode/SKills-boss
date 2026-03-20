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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

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
            Material.DIAMOND_PICKAXE,
            Material.DIAMOND_AXE,
            Material.DIAMOND_SHOVEL,
            Material.DIAMOND_HOE,
            Material.NETHERITE_HELMET,
            Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS,
            Material.NETHERITE_BOOTS,
            Material.NETHERITE_SWORD,
            Material.NETHERITE_PICKAXE,
            Material.NETHERITE_AXE,
            Material.NETHERITE_SHOVEL,
            Material.NETHERITE_HOE,
            Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Prevent taking items from the Recipe GUI
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.contains("Custom Recipes")) {
            event.setCancelled(true);
            return;
        }

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
        // Prevent dragging items in the Recipe GUI
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.contains("Custom Recipes")) {
            event.setCancelled(true);
            return;
        }

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

        if (enchants.containsKey(Enchantment.SHARPNESS) && enchants.get(Enchantment.SHARPNESS) > 2) {
            enchants.put(Enchantment.SHARPNESS, 2);
        }

        if (enchants.containsKey(Enchantment.EFFICIENCY) && enchants.get(Enchantment.EFFICIENCY) > 2) {
            enchants.put(Enchantment.EFFICIENCY, 2);
        }

        if (enchants.containsKey(Enchantment.POWER) && enchants.get(Enchantment.POWER) > 2) {
            enchants.put(Enchantment.POWER, 2);
        }

        enchants.remove(Enchantment.FIRE_ASPECT);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        // Restriction removed
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
    public void onBlockBreak(BlockBreakEvent event) {
        if (SkillsBoss.getProgressionLevel() == 0) {
            if (!event.getPlayer().isOp()) {
                event.setCancelled(true);
                event.getPlayer()
                        .sendMessage(Component.text("The world is frozen in Progression 0! You cannot break blocks.",
                                NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (SkillsBoss.getProgressionLevel() == 0) {
            if (!event.getPlayer().isOp()) {
                event.setCancelled(true);
                event.getPlayer()
                        .sendMessage(Component.text("The world is frozen in Progression 0! You cannot place blocks.",
                                NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onVillagerInteract(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (SkillsBoss.getProgressionLevel() < 1)
            return;
        if (event.getInventory().getType() == org.bukkit.event.inventory.InventoryType.MERCHANT) {
            String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
            if (title.contains("Piglin Trader")) {
                return; // Allow the piglin trader interface
            }
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
        int lavaCount = 0;
        int windChargeCount = 0;
        int arrowCount = 0;

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
        }
    }

    private boolean sanitizeItem(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return false;
        boolean changed = false;

        // Protection limit: 3
        if (meta.hasEnchant(Enchantment.PROTECTION) && meta.getEnchantLevel(Enchantment.PROTECTION) > 3) {
            meta.addEnchant(Enchantment.PROTECTION, 3, true);
            changed = true;
        }

        // Sharpness limit: 2
        if (meta.hasEnchant(Enchantment.SHARPNESS) && meta.getEnchantLevel(Enchantment.SHARPNESS) > 2) {
            meta.addEnchant(Enchantment.SHARPNESS, 2, true);
            changed = true;
        }

        // Efficiency limit: 2
        if (meta.hasEnchant(Enchantment.EFFICIENCY) && meta.getEnchantLevel(Enchantment.EFFICIENCY) > 2) {
            meta.addEnchant(Enchantment.EFFICIENCY, 2, true);
            changed = true;
        }

        // Power limit: 2
        if (meta.hasEnchant(Enchantment.POWER) && meta.getEnchantLevel(Enchantment.POWER) > 2) {
            meta.addEnchant(Enchantment.POWER, 2, true);
            changed = true;
        }

        // Remove Fire Aspect if present
        if (meta.hasEnchant(Enchantment.FIRE_ASPECT)) {
            meta.removeEnchant(Enchantment.FIRE_ASPECT);
            changed = true;
        }

        if (changed) {
            item.setItemMeta(meta);
        }
        return changed;
    }

    public static void spawnPiglinTrader(org.bukkit.Location loc) {
        org.bukkit.entity.PiglinBrute trader = (org.bukkit.entity.PiglinBrute) loc.getWorld().spawnEntity(loc, org.bukkit.entity.EntityType.PIGLIN_BRUTE);
        trader.setImmuneToZombification(true);
        trader.setAI(true); // Allow wandering
        trader.setRemoveWhenFarAway(false); // Make sure he doesn't despawn
        trader.customName(net.kyori.adventure.text.Component.text("§6Piglin Trader"));
        trader.setCustomNameVisible(true);
        trader.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(SkillsBoss.getInstance(), "is_piglin_trader"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        trader.getEquipment().clear();
    }

    @EventHandler
    public void onPiglinTraderTarget(org.bukkit.event.entity.EntityTargetEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.PiglinBrute) {
            org.bukkit.entity.PiglinBrute p = (org.bukkit.entity.PiglinBrute) event.getEntity();
            if (p.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(SkillsBoss.getInstance(), "is_piglin_trader"), org.bukkit.persistence.PersistentDataType.BYTE)) {
                // Prevent trader from getting angry at players
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerUseTraderEgg(org.bukkit.event.player.PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        org.bukkit.persistence.PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.has(new org.bukkit.NamespacedKey(SkillsBoss.getInstance(), "trader_spawn_egg"), org.bukkit.persistence.PersistentDataType.BYTE)) {
            event.setCancelled(true);
            org.bukkit.block.Block clicked = event.getClickedBlock();
            if (clicked != null) {
                org.bukkit.Location spawnLoc = clicked.getLocation().add(0.5, 1, 0.5);
                spawnPiglinTrader(spawnLoc);
                if (event.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    item.setAmount(item.getAmount() - 1);
                }
            }
        }
    }

    @EventHandler
    public void onEntityInteract(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof org.bukkit.entity.PiglinBrute) {
            org.bukkit.entity.PiglinBrute p = (org.bukkit.entity.PiglinBrute) event.getRightClicked();
            if (p.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(SkillsBoss.getInstance(), "is_piglin_trader"), org.bukkit.persistence.PersistentDataType.BYTE)) {
                event.setCancelled(true);
                openTraderMenu(event.getPlayer());
            }
        }
    }

    private void openTraderMenu(Player player) {
        org.bukkit.inventory.Merchant merchant = null;
        try {
            java.lang.reflect.Method createMerchantComponent = org.bukkit.Bukkit.class.getMethod("createMerchant", net.kyori.adventure.text.Component.class);
            merchant = (org.bukkit.inventory.Merchant) createMerchantComponent.invoke(null, net.kyori.adventure.text.Component.text("Piglin Trader"));
        } catch (Exception e) {
            merchant = org.bukkit.Bukkit.createMerchant("Piglin Trader");
        }
        
        if (merchant == null) return;

        java.util.List<org.bukkit.inventory.MerchantRecipe> recipes = new java.util.ArrayList<>();
        java.util.Random rand = new java.util.Random();
        
        // Define possible selling items
        ItemStack[] sellableItems = new ItemStack[] {
            new ItemStack(Material.ENDER_PEARL, rand.nextInt(3) + 1),
            new ItemStack(Material.NETHERITE_SCRAP, rand.nextInt(2) + 1),
            new ItemStack(Material.GOLDEN_APPLE, rand.nextInt(2) + 1),
            new ItemStack(Material.EXPERIENCE_BOTTLE, rand.nextInt(10) + 5),
            ItemManager.createCustomItem(Material.DIAMOND_SWORD) // Sometimes sell a custom weapon
        };

        // Add Strength Potion to possibilities
        ItemStack strPotion = new ItemStack(Material.POTION);
        org.bukkit.inventory.meta.PotionMeta pMeta = (org.bukkit.inventory.meta.PotionMeta) strPotion.getItemMeta();
        pMeta.addCustomEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH, 2400, 1), true);
        pMeta.displayName(net.kyori.adventure.text.Component.text("§cBrute's Strength"));
        strPotion.setItemMeta(pMeta);
        ItemStack[] finalItems = new ItemStack[sellableItems.length + 1];
        System.arraycopy(sellableItems, 0, finalItems, 0, sellableItems.length);
        finalItems[sellableItems.length] = strPotion;

        // Currency types
        Material[] currencies = new Material[] { Material.GOLD_INGOT, Material.GOLD_BLOCK, Material.GILDED_BLACKSTONE };
        
        // Generate 3 random trades
        for (int i = 0; i < 3; i++) {
            ItemStack output = finalItems[rand.nextInt(finalItems.length)].clone();
            org.bukkit.inventory.MerchantRecipe recipe = new org.bukkit.inventory.MerchantRecipe(output, 9999);
            
            // Randomly choose 1 or 2 cost inputs
            Material cur1 = currencies[rand.nextInt(currencies.length)];
            int amt1 = rand.nextInt(10) + 1;
            recipe.addIngredient(new ItemStack(cur1, amt1));
            
            if (rand.nextBoolean()) {
                Material cur2 = currencies[rand.nextInt(currencies.length)];
                int amt2 = rand.nextInt(5) + 1;
                recipe.addIngredient(new ItemStack(cur2, amt2));
            }
            
            recipes.add(recipe);
        }

        merchant.setRecipes(recipes);
        player.openMerchant(merchant, true);
    }
}
