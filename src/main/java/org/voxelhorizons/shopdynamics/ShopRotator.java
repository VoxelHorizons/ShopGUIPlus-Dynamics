package org.voxelhorizons.shopdynamics;

import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;
import net.brcdev.shopgui.shop.item.ShopItemType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * All changes are made to the live ShopGUI+ Shop objects on the server thread.
 * Original ShopItem instances and their metadata are kept, not rebuilt from YAML.
 */
public final class ShopRotator {
    private final JavaPlugin plugin;
    private final StockState state;
    private final RotationSettings settings;
    private final Random random = new Random();
    private final Map<String, ManagedShop> managed = new LinkedHashMap<>();
    private boolean bound;
    private boolean warnedWorld;

    private record Position(int page, int slot) {}

    private static final class ManagedShop {
        final Shop shop;
        final List<ShopItem> all;
        final List<ShopItem> trade;
        final List<ShopItem> fixed;
        final IdentityHashMap<ShopItem, Position> positions;
        final int size;

        ManagedShop(Shop shop, List<ShopItem> all, List<ShopItem> trade, List<ShopItem> fixed) {
            this.shop = shop;
            this.all = all;
            this.trade = trade;
            this.fixed = fixed;
            this.size = shop.getSize();
            this.positions = new IdentityHashMap<>();
            for (ShopItem entry : all) {
                positions.put(entry, new Position(entry.getPage(), entry.getSlot()));
            }
        }

        void restore() {
            for (Map.Entry<ShopItem, Position> entry : positions.entrySet()) {
                entry.getKey().setPage(entry.getValue().page());
                entry.getKey().setSlot(entry.getValue().slot());
            }
            shop.setShopItems(new ArrayList<>(all));
            shop.setSize(size);
        }
    }

    public ShopRotator(JavaPlugin plugin, StockState state, RotationSettings settings) {
        this.plugin = plugin;
        this.state = state;
        this.settings = settings;
    }

    public boolean isBound() { return bound; }
    public int count() { return managed.size(); }
    public Set<String> shopIds() { return Set.copyOf(managed.keySet()); }

    public boolean canBind() {
        return ShopGuiPlusApi.getPlugin() != null
            && ShopGuiPlusApi.getPlugin().getShopManager().areShopsLoaded();
    }

    /** Rebind after ShopGUI+ has (re)loaded its original shop definitions. */
    public void bind() {
        restore();
        if (!settings.enabled() || !canBind()) return;
        try {
            for (Shop shop : ShopGuiPlusApi.getPlugin().getShopManager().getShops()) {
                List<ShopItem> original = new ArrayList<>(shop.getShopItems());
                List<ShopItem> trade = new ArrayList<>();
                List<ShopItem> fixed = new ArrayList<>();
                for (ShopItem item : original) {
                    if (item.getType() == ShopItemType.ITEM) trade.add(item);
                    else fixed.add(item);
                }
                if (!settings.manages(shop.getId(), trade.size())) continue;

                int size = settings.inventorySize() == -1 ? shop.getSize() : settings.inventorySize();
                if (settings.slots().stream().anyMatch(slot -> slot >= size)) {
                    plugin.getLogger().warning("Skipping " + shop.getId() + ": item slots exceed inventory size " + size);
                    continue;
                }
                if (fixed.stream().anyMatch(i -> i.getPage() == 1 && settings.slots().contains(i.getSlot()))) {
                    plugin.getLogger().warning("Skipping " + shop.getId() + ": permanent non-item entries occupy rotation slots");
                    continue;
                }
                if (fixed.stream().anyMatch(i -> i.getSlot() >= size)) {
                    plugin.getLogger().warning("Skipping " + shop.getId() + ": a permanent entry exceeds new inventory size");
                    continue;
                }
                Set<String> ids = new HashSet<>();
                if (trade.stream().anyMatch(i -> !ids.add(i.getId()))) {
                    plugin.getLogger().warning("Skipping " + shop.getId() + ": duplicate trading item IDs");
                    continue;
                }
                ManagedShop managedShop = new ManagedShop(shop, original, trade, fixed);
                managed.put(shop.getId(), managedShop);
                apply(managedShop, false);
                plugin.getLogger().info("Managing " + shop.getId() + ": " + trade.size()
                    + " original items, " + Math.min(trade.size(), settings.slots().size()) + " active");
            }
            bound = true;
            state.save();
        } catch (Exception e) {
            plugin.getLogger().severe("ShopGUI+ shops could not be inspected: " + e.getMessage());
            restore();
        }
    }

    /** Restore source positions and the complete loaded catalogue on disable/rebind. */
    public void restore() {
        for (ManagedShop m : managed.values()) {
            try { m.restore(); }
            catch (Exception e) {
                plugin.getLogger().warning("Failed to restore " + m.shop.getId() + ": " + e.getMessage());
            }
        }
        managed.clear();
        bound = false;
    }

    public boolean rotate(String shopId) {
        ManagedShop m = managed.get(shopId);
        if (m == null) return false;
        apply(m, true);
        state.save();
        return true;
    }

    public int rotateAll() {
        for (ManagedShop m : managed.values()) apply(m, true);
        state.save();
        return managed.size();
    }

    private void apply(ManagedShop m, boolean fresh) {
        Map<String, ShopItem> byId = new LinkedHashMap<>();
        for (ShopItem item : m.trade) byId.put(item.getId(), item);
        List<String> ids = List.copyOf(byId.keySet());
        int count = Math.min(settings.slots().size(), ids.size());
        List<String> chosen = state.active(m.shop.getId());
        if (fresh || chosen.size() != count || new HashSet<>(chosen).size() != count
                || !ids.containsAll(chosen)) {
            StockSelector.Draw draw = StockSelector.draw(ids, chosen, state.bag(m.shop.getId()),
                count, settings.avoidPrevious(), random);
            chosen = draw.selected();
            state.put(m.shop.getId(), draw);
        }

        if (settings.closeOpenShops()) closeViewers(m.shop);

        // Return all item positions to their initial values before assigning new slots.
        for (Map.Entry<ShopItem, Position> position : m.positions.entrySet()) {
            position.getKey().setPage(position.getValue().page());
            position.getKey().setSlot(position.getValue().slot());
        }

        List<ShopItem> live = new ArrayList<>(m.fixed);
        for (int index = 0; index < chosen.size(); index++) {
            ShopItem item = byId.get(chosen.get(index));
            item.setPage(1);
            item.setSlot(settings.slots().get(index));
            live.add(item);
        }
        if (settings.inventorySize() != -1) m.shop.setSize(settings.inventorySize());
        m.shop.setShopItems(live);
    }

    private void closeViewers(Shop shop) {
        String expected = shop.getName(1);
        if (expected == null) return;
        expected = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', expected));
        for (Player player : Bukkit.getOnlinePlayers()) {
            String visible = ChatColor.stripColor(player.getOpenInventory().getTitle());
            if (expected.equalsIgnoreCase(visible)) player.closeInventory();
        }
    }

    /** Called on the main thread at the configured tick interval. */
    public void checkClock() {
        if (!settings.enabled() || !bound || managed.isEmpty()) return;
        if (settings.mode() == RotationSettings.Mode.MINECRAFT_DAY) {
            World world = Bukkit.getWorld(settings.world());
            if (world == null) {
                if (!warnedWorld) {
                    plugin.getLogger().warning("Clock world '" + settings.world() + "' not found; rotations paused");
                    warnedWorld = true;
                }
                return;
            }
            warnedWorld = false;
            long day = world.getFullTime() / 24000L;
            long previousDay = state.lastDay();
            if (previousDay < 0L || day < previousDay) {
                state.lastDay(day);
                state.save();
            } else if (day - previousDay >= settings.intervalDays()) {
                rotateAll();
                state.lastDay(day);
                state.save();
                plugin.getLogger().info("Minecraft-day stock rotation completed (" + managed.size() + " shops)");
            }
        } else {
            long now = System.currentTimeMillis();
            long next = state.nextRealTime();
            if (next <= 0L) {
                state.nextRealTime(now + settings.realTimeMillis());
                state.save();
            } else if (now >= next) {
                rotateAll();
                state.nextRealTime(now + settings.realTimeMillis());
                state.save();
                plugin.getLogger().info("Real-time stock rotation completed (" + managed.size() + " shops)");
            }
        }
    }

    public String clockStatus() {
        if (settings.mode() == RotationSettings.Mode.MINECRAFT_DAY) {
            World world = Bukkit.getWorld(settings.world());
            return world == null ? "missing world " + settings.world()
                : "day " + world.getFullTime() / 24000L + " (last rotation day " + state.lastDay() + ")";
        }
        return "next rotation epoch " + state.nextRealTime() + " ms";
    }
}
