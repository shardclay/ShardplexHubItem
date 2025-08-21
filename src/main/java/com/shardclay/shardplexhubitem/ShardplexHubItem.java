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
    private final int ITEM_SLOT = 4; // Slot 5 in inventory is index 4
    private final Pattern hexPattern = Pattern.compile("<#([A-Fa-f0-9]{6})>");

    // Configuration fields
    private boolean consoleMode;
    private String consoleCommand;
    private boolean playerMode;
    private String playerCommand;

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

        String itemId = getConfig().getString("item-id", "COMPASS");
        Material material = Material.matchMaterial(itemId);
        if (material == null) {
            material = Material.COMPASS;
            getLogger().warning("Неверный ID предмета config.yml. Вернул к ID по умолчанию COMPASS.");
        }

        customItem = new ItemStack(material, 1);
        ItemMeta meta = customItem.getItemMeta();
        if (meta != null) {
            String name = getConfig().getString("item-name", "<#8a7d93>Выбрать сервер");
            meta.setDisplayName(translateHexColorCodes(name));

            List<String> lore = getConfig().getStringList("item-lore");
            List<String> coloredLore = lore.stream()
                    .map(this::translateHexColorCodes)
                    .collect(Collectors.toList());
            meta.setLore(coloredLore);
            customItem.setItemMeta(meta);
        }

        try {
            interactionSound = Sound.valueOf(getConfig().getString("sound", "BLOCK_NOTE_BLOCK_PLING"));
        } catch (IllegalArgumentException e) {
            getLogger().warning("Неверный звук в config.yml. В верунл к звуку по умолчанию BLOCK_NOTE_BLOCK_PLING.");
            interactionSound = Sound.BLOCK_NOTE_BLOCK_PLING;
        }

        // Load new execution modes
        consoleMode = getConfig().getBoolean("console_mode", false);
        consoleCommand = getConfig().getString("console_command", "");
        playerMode = getConfig().getBoolean("player_mode", false);
        playerCommand = getConfig().getString("player_command", "");
    }

    private void givePlayerCustomItem(Player player) {
        player.getInventory().setItem(ITEM_SLOT, customItem);
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
                sender.sendMessage(ChatColor.RED + "Использование: /shi [reload|give]");
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("shardplex.shi.reload")) {
                    sender.sendMessage(ChatColor.RED + "У вас нет прав на использование данной команды.");
                    return true;
                }
                loadConfig();
                updateAllPlayersItems();
                sender.sendMessage(ChatColor.GREEN + "ShardplexHubItem reloaded.");
                return true;
            }

            if (args[0].equalsIgnoreCase("give")) {
                if (!sender.hasPermission("shardplex.shi.give")) {
                    sender.sendMessage(ChatColor.RED + "У вас нет прав на использование данной команды.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Использование: /shi give <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Игрок не найден.");
                    return true;
                }
                givePlayerCustomItem(target);
                sender.sendMessage(ChatColor.GREEN + "Предмет выдан игроку " + target.getName());
                return true;
            }
        }
        return false;
    }

    private void performActions(Player player) {
        boolean commandWasRun = false;

        if (consoleMode && consoleCommand != null && !consoleCommand.isEmpty()) {
            String commandToExecute = consoleCommand;
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                commandToExecute = PlaceholderAPI.setPlaceholders(player, commandToExecute);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToExecute);
            commandWasRun = true;
        }

        if (playerMode && playerCommand != null && !playerCommand.isEmpty()) {
            String commandToExecute = playerCommand;
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                commandToExecute = PlaceholderAPI.setPlaceholders(player, commandToExecute);
            }
            // Ensure the command starts with a / for chat
            if (!commandToExecute.startsWith("/")) {
                commandToExecute = "/" + commandToExecute;
            }
            player.chat(commandToExecute);
            commandWasRun = true;
        }

        if (commandWasRun) {
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
             if (event.getSlot() == ITEM_SLOT || event.isShiftClick()) {
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