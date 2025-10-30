package me.turtle.foodSpiker;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.persistence.PersistentDataType;

public class FoodSpiker extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private NamespacedKey spikedKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        spikedKey = new NamespacedKey(this, "spiked_item");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("spike")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use this command!");
                return true;
            }

            ItemStack item = player.getInventory().getItemInMainHand();

            if (!isConsumable(item)) {
                player.sendMessage("§cYou must hold a food or potion item!");
                return true;
            }

            int required = config.getInt("spider-eyes-required", 1);
            if (!player.getInventory().containsAtLeast(new ItemStack(Material.SPIDER_EYE), required)) {
                player.sendMessage("§cYou need " + required + " spider eyes to spike this!");
                return true;
            }

            player.getInventory().removeItem(new ItemStack(Material.SPIDER_EYE, required));

            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(spikedKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);

            player.sendMessage("§aYou spiked the item successfully!");
            return true;
        }

        if (command.getName().equalsIgnoreCase("reloadspike")) {
            if (!sender.hasPermission("foodspiker.reload")) {
                sender.sendMessage("§cYou don't have permission to reload this plugin.");
                return true;
            }

            reloadConfig();
            config = getConfig();
            sender.sendMessage("§aFoodSpiker config reloaded!");
            return true;
        }

        return false;
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();

        int required = config.getInt("spider-eyes-required", 1);

        int spiderEyes = 0;
        int consumables = 0;
        ItemStack consumable = null;

        for (ItemStack item : matrix) {
            if (item == null || item.getType() == Material.AIR) continue;

            if (item.getType() == Material.SPIDER_EYE) {
                spiderEyes += item.getAmount();
            } else if (isConsumable(item)) {
                consumables++;
                consumable = item;
            } else {
                // Invalid item in recipe — cancel
                inv.setResult(null);
                return;
            }
        }

        // Only allow exactly one consumable and exact spider eyes required
        if (consumables == 1 && spiderEyes == required) {
            ItemStack result = consumable.clone(); // preserve potion data
            ItemMeta meta = result.getItemMeta();
            meta.getPersistentDataContainer().set(spikedKey, PersistentDataType.BYTE, (byte) 1);
            result.setItemMeta(meta);
            inv.setResult(result);
        } else {
            inv.setResult(null); // no valid recipe
        }
    }

    private boolean isConsumable(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type.isEdible() || isPotion(type);
    }

    private boolean isPotion(Material type) {
        return type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION;
    }

    // When a player eats or drinks a spiked item
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getItemMeta() == null) return;

        if (!item.getItemMeta().getPersistentDataContainer().has(spikedKey, PersistentDataType.BYTE)) return;

        int seconds = config.getInt("poison-duration", 10);
        event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.POISON, seconds * 20, 0));
        event.getPlayer().sendMessage("§aYou feel sick... the item was spiked!");
    }

    // When a spiked splash or lingering potion is thrown
    @EventHandler
    public void onPotionSplash(PotionSplashEvent event) {
        ItemStack item = event.getPotion().getItem();
        if (item == null || item.getItemMeta() == null) return;
        if (!item.getItemMeta().getPersistentDataContainer().has(spikedKey, PersistentDataType.BYTE)) return;

        int seconds = config.getInt("poison-duration", 10);

        for (LivingEntity entity : event.getAffectedEntities()) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.POISON, seconds * 20, 0));
        }
    }
}