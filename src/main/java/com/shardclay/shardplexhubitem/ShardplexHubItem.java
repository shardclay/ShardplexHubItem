package com.shardclay.shardplexhubitem;

import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ShardplexHubItem extends JavaPlugin implements Listener {

    private ItemStack customItem;
    private Sound interactionSound;
    private int itemSlot;
    private final Pattern hexPattern = Pattern.compile("<#([A-Fa-f0-9]{6})>");

    // Configuration fields
    private boolean commandEnabled;
    private String command;

    @Override
    public void onEnable() {
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        loadConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ShardplexHubItem has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ShardplexHubItem has been disabled!");
    }

    private String translateHexColorCodes(String message) {
        Matcher matcher = hexPattern.matcher(message);
        StringBuffer buffer = new StringBuffer(message.length() + 4 * 8);
        while (matcher.find()) {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.of("#" + group).toString());
        }
        return ChatColor.translateAlternateColorCodes('&', matcher.appendTail(buffer).toString());
    }

    private void loadConfig() {
        reloadConfig();

        // Load item configuration
        String itemId = getConfig().getString("item-id", "RECOVERY_COMPASS");
        Material material = Material.matchMaterial(itemId);
        if (material == null) {
            material = Material.RECOVERY_COMPASS;
            getLogger().warning("Invalid item ID in config.yml. Defaulting to RECOVERY_COMPASS.");
        }

        itemSlot = getConfig().getInt("item-slot", 4);

        customItem = new ItemStack(material, 1);
        ItemMeta meta = customItem.getItemMeta();
        if (meta != null) {
            String name = getConfig().getString("item-name", "<#8a7d93>Server Selector");
            meta.setDisplayName(translateHexColorCodes(name));

            List<String> lore = getConfig().getStringList("item-lore");
            List<String> coloredLore = lore.stream()
                    .map(this::translateHexColorCodes)
                    .collect(Collectors.toList());
            meta.setLore(coloredLore);

            int customModelData = getConfig().getInt("custom-model-data", 0);
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }

            customItem.setItemMeta(meta);
        }

        // Load sound configuration
        try {
            interactionSound = Sound.valueOf(getConfig().getString("sound", "BLOCK_AMETHYST_BLOCK_PLACE"));
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid sound in config.yml. Defaulting to BLOCK_AMETHYST_BLOCK_PLACE.");
            interactionSound = Sound.BLOCK_AMETHYST_BLOCK_PLACE;
        }

        // Load command configuration
        commandEnabled = getConfig().getBoolean("command_enabled", true);
        command = getConfig().getString("command", "say Test");
    }

    private void givePlayerCustomItem(Player player) {
        player.getInventory().setItem(itemSlot, customItem);
    }

    private void updateAllPlayersItems() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            givePlayerCustomItem(player);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("shi")) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.RED + "Usage: /shi [reload|give]");
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("shardplex.shi.reload")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                loadConfig();
                updateAllPlayersItems();
                sender.sendMessage(ChatColor.GREEN + "ShardplexHubItem reloaded.");
                return true;
            }

            if (args[0].equalsIgnoreCase("give")) {
                if (!sender.hasPermission("shardplex.shi.give")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /shi give <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }
                givePlayerCustomItem(target);
                sender.sendMessage(ChatColor.GREEN + "Item given to " + target.getName());
                return true;
            }
        }
        return false;
    }

    private void performActions(Player player) {
        if (commandEnabled && command != null && !command.isEmpty()) {
            String commandToExecute = command;
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                commandToExecute = PlaceholderAPI.setPlaceholders(player, commandToExecute);
            }
            
            // Execute command as player
            Bukkit.dispatchCommand(player, commandToExecute);

            player.playSound(player.getLocation(), interactionSound, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        givePlayerCustomItem(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack currentItem = event.getCurrentItem();
        if (currentItem != null && currentItem.isSimilar(customItem)) {
             if (event.getSlot() == itemSlot || event.isShiftClick()) {
                event.setCancelled(true);
                performActions((Player) event.getWhoClicked());
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (customItem.isSimilar(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            performActions(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (customItem.isSimilar(event.getOffHandItem()) || customItem.isSimilar(event.getMainHandItem())) {
            event.setCancelled(true);
            performActions(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (customItem.isSimilar(heldItem)) {
            event.setCancelled(true);
            performActions(player);
        }
    }
}
