package org.voxelhorizons.shopdynamics;

import net.brcdev.shopgui.ShopGuiPlugin;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.ShopManager;
import net.brcdev.shopgui.shop.item.ShopItem;
import net.brcdev.shopgui.shop.item.ShopItemType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Regression for GUI resizing: a 20-item rotating grid must never overwrite
 * ShopGUI+'s configured menu size, even after rotations or shutdown.
 */
class ShopRotatorTest {
    private static final List<Integer> SLOTS = List.of(
        2, 3, 4, 5, 6, 11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33);

    @Test
    void preservesFiftyFourSlotShopDuringBindRotationAndRestore() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ShopRotatorTest"));
        StockState state = mock(StockState.class);
        when(state.active("miner")).thenReturn(List.of());
        when(state.bag("miner")).thenReturn(List.of());
        Shop shop = mock(Shop.class);
        when(shop.getId()).thenReturn("miner");
        when(shop.getSize()).thenReturn(54); // ShopGUI+ owns the 6-row inventory.
        ShopItem item = mock(ShopItem.class);
        when(item.getType()).thenReturn(ShopItemType.ITEM);
        when(item.getId()).thenReturn("iron_ingot");
        when(item.getPage()).thenReturn(1);
        when(item.getSlot()).thenReturn(10);
        when(shop.getShopItems()).thenReturn(List.of(item));

        ShopManager manager = mock(ShopManager.class);
        when(manager.areShopsLoaded()).thenReturn(true);
        when(manager.getShops()).thenReturn(Set.of(shop));
        ShopGuiPlugin shopGui = mock(ShopGuiPlugin.class);
        when(shopGui.getShopManager()).thenReturn(manager);

        RotationSettings settings = new RotationSettings(
            true, RotationSettings.Mode.MINECRAFT_DAY, "world", 1, 1200000, 100,
            true, false, false, Set.of("miner"), Set.of(), SLOTS);

        try (MockedStatic<ShopGuiPlusApi> api = mockStatic(ShopGuiPlusApi.class)) {
            api.when(ShopGuiPlusApi::getPlugin).thenReturn(shopGui);
            ShopRotator rotator = new ShopRotator(plugin, state, settings);
            rotator.bind();
            assertEquals(1, rotator.count());
            rotator.rotate("miner");
            rotator.restore();

            verify(shop, never()).setSize(anyInt());
            verify(shop, times(3)).setShopItems(anyList());
        }
    }

    @Test
    void skipsIncompatibleSlotsRatherThanExpandingMenu() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ShopRotatorTest"));
        Shop shop = mock(Shop.class);
        when(shop.getId()).thenReturn("miner");
        when(shop.getSize()).thenReturn(27); // slots 29-33 do not fit.
        ShopItem item = mock(ShopItem.class);
        when(item.getType()).thenReturn(ShopItemType.ITEM);
        when(shop.getShopItems()).thenReturn(List.of(item));
        ShopManager manager = mock(ShopManager.class);
        when(manager.areShopsLoaded()).thenReturn(true);
        when(manager.getShops()).thenReturn(Set.of(shop));
        ShopGuiPlugin shopGui = mock(ShopGuiPlugin.class);
        when(shopGui.getShopManager()).thenReturn(manager);
        RotationSettings settings = new RotationSettings(
            true, RotationSettings.Mode.MINECRAFT_DAY, "world", 1, 1200000, 100,
            true, false, false, Set.of("miner"), Set.of(), SLOTS);

        try (MockedStatic<ShopGuiPlusApi> api = mockStatic(ShopGuiPlusApi.class)) {
            api.when(ShopGuiPlusApi::getPlugin).thenReturn(shopGui);
            ShopRotator rotator = new ShopRotator(plugin, mock(StockState.class), settings);
            rotator.bind();
            assertEquals(0, rotator.count());
            verify(shop, never()).setSize(anyInt());
            verify(shop, never()).setShopItems(anyList());
        }
    }
}
