package org.voxelhorizons.shopdynamics;

import net.brcdev.shopgui.event.ShopsPostLoadEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DynamicsPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private StockState state;
    private ShopRotator rotator;
    private RotationSettings settings;
    private BukkitTask monitor;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        state = new StockState(new File(getDataFolder(), "state.yml"), getLogger());
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("shopdynamics") != null) {
            getCommand("shopdynamics").setExecutor(this);
            getCommand("shopdynamics").setTabCompleter(this);
        }
        configure(false);
    }

    @Override
    public void onDisable() {
        if (monitor != null) monitor.cancel();
        if (rotator != null) rotator.restore();
        if (state != null) state.save();
    }

    @EventHandler
    public void onShopReload(ShopsPostLoadEvent event) {
        Bukkit.getScheduler().runTask(this, this::tryBind);
    }

    private void configure(boolean reload) {
        if (monitor != null) {
            monitor.cancel();
            monitor = null;
        }
        if (rotator != null) rotator.restore();
        if (reload) reloadConfig();
        try {
            settings = RotationSettings.from(getConfig());
        } catch (IllegalArgumentException error) {
            getLogger().severe("Invalid config.yml: " + error.getMessage());
            getLogger().severe("Rotation disabled until the configuration is corrected and reloaded.");
            rotator = null;
            return;
        }
        rotator = new ShopRotator(this, state, settings);
        // ShopGUI+ is already enabled due to depend:, but its shops may load later.
        Bukkit.getScheduler().runTask(this, this::tryBind);
        monitor = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (rotator == null) return;
            if (!rotator.isBound()) tryBind();
            rotator.checkClock();
        }, settings.checkTicks(), settings.checkTicks());
    }

    private void tryBind() {
        if (rotator != null && settings.enabled() && rotator.canBind()) rotator.bind();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shopdynamics.admin")) {
            sender.sendMessage("§cYou do not have permission.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> {
                sender.sendMessage("§6ShopGUIPlus-Dynamics §7| §f" +
                    (rotator == null ? "invalid configuration" : (rotator.isBound() ? "active" : "waiting for shops")));
                if (rotator != null) {
                    sender.sendMessage("§7Managed shops: §f" + rotator.count() + " " + rotator.shopIds());
                    sender.sendMessage("§7Clock: §f" + rotator.clockStatus());
                }
            }
            case "rotate" -> {
                if (rotator == null || !rotator.isBound()) {
                    sender.sendMessage("§cShopGUI+ shops are not ready.");
                } else if (args.length < 2 || args[1].equalsIgnoreCase("all")) {
                    sender.sendMessage("§aRotated " + rotator.rotateAll() + " managed shops.");
                } else if (rotator.rotate(args[1])) {
                    sender.sendMessage("§aRotated " + args[1] + ".");
                } else {
                    sender.sendMessage("§cShop is not managed: " + args[1]);
                }
            }
            case "reload" -> {
                configure(true);
                sender.sendMessage(rotator == null ? "§cInvalid config; see console." : "§aDynamics configuration reloaded.");
            }
            default -> sender.sendMessage("§eUsage: /" + label + " <status|rotate [shop|all]|reload>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("shopdynamics.admin")) return List.of();
        if (args.length == 1) return List.of("status", "rotate", "reload").stream()
            .filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("rotate")) {
            List<String> options = new ArrayList<>();
            options.add("all");
            if (rotator != null) options.addAll(rotator.shopIds());
            return options.stream().filter(x -> x.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
