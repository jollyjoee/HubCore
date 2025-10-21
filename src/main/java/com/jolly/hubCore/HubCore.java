package com.jolly.hubCore;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class HubCore extends JavaPlugin implements Listener, CommandExecutor {
    private FileConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("✅ HubCore enabled successfully!");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        this.getCommand(config.getString("command")).setExecutor(this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        World w = Bukkit.getWorld(config.getString("hub-world-name"));
        if (p.getWorld().getName().equalsIgnoreCase(config.getString("hub-world-name"))) {
            Location worldSpawn = w.getSpawnLocation();
            p.teleport(new Location(w, worldSpawn.getX(), worldSpawn.getY(), worldSpawn.getZ()));
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!config.getBoolean("protection-enabled")) return;
        if (event.getEntity() instanceof Player && event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
        }
    }
    @EventHandler
    public void onHungerChange(FoodLevelChangeEvent event) {
        if (!config.getBoolean("protection-enabled")) return;
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
            ((Player) event.getEntity()).setFoodLevel(20);
        }
    }
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!config.getBoolean("protection-enabled")) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("hub.build") && player.getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!config.getBoolean("protection-enabled")) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("hub.build") && player.getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if (!config.getBoolean("protection-enabled")) return;
        if (event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        Material type = event.getClickedBlock().getType();
        if (!player.hasPermission("hub.build") && player.getGameMode() != GameMode.CREATIVE) {
            switch (type) {
                case CHEST:
                case TRAPPED_CHEST:
                case BARREL:
                case ENDER_CHEST:
                case HOPPER:
                case DISPENSER:
                case DROPPER:
                case FURNACE:
                case BLAST_FURNACE:
                case SMOKER:
                case BREWING_STAND:
                case CAMPFIRE:
                case SOUL_CAMPFIRE:
                    event.setCancelled(true);
                    break;
                default:
                    if (type.name().contains("DOOR") || type == Material.LEVER) {
                        event.setCancelled(true);
                    }
                    break;
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        String currentServer = getConfig().getString("this-server-name", "unknown");
        if (currentServer.equalsIgnoreCase(config.getString("hub-name", "hub"))) {
            player.sendActionBar("§eYou're already in the " + config.getString("hub-name", "hub") + "!");
            return true;
        }
        Location startLoc = player.getLocation().clone();
        player.sendActionBar("§aSending you to the " + config.getString("hub-name", "hub") + "...");
        AtomicInteger countdown = new AtomicInteger(5);
        Bukkit.getGlobalRegionScheduler().runDelayed(this, (task) -> {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, (t) -> {
                int timeLeft = countdown.getAndDecrement();
                Location loc = player.getLocation().clone();
                if (!sameBlock(startLoc, loc)) {
                    player.sendActionBar("§cTeleport cancelled — you moved!");
                    t.cancel();
                    return;
                }
                if (timeLeft > 0) {
                    player.sendActionBar("§aTeleporting in " + timeLeft + " seconds...");
                } else {
                    sendToServer(player, config.getString("hub-name", "hub"));
                    t.cancel();
                }
            }, 1L, 20L);
        }, 20L);
        return true;
    }

    private boolean sameBlock(Location a, Location b) {
        return a.getBlockX() == b.getBlockX() &&
                a.getBlockY() == b.getBlockY() &&
                a.getBlockZ() == b.getBlockZ();
    }

    private void sendToServer(Player player, String targetServer) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(targetServer);
            player.sendPluginMessage(this, "BungeeCord", b.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
