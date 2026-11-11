# ShopGUIPlus-Dynamics

Daily rotating inventories for [ShopGUI+](https://www.spigotmc.org/resources/shopgui.6515/), by VoxelHorizons. Designed for shops whose full catalogue is larger than the slots in their resource-pack GUI.

For example, a Blacksmith shop with **53 configured entries** can display **20 randomly selected trading items** at any given time. At the start of each Minecraft day (by default), a new set is drawn. Players return to discover which tools and armour are currently available to buy or sell.

## How it works

- Uses the **ShopGUI+ 3.2.0 public API**, intended for ShopGUI+ 1.111.0 and newer, including 1.113.0.
- Changes loaded `Shop` / `ShopItem` objects **in memory**, not your `plugins/ShopGUIPlus/shops/*.yml` definitions. No ShopGUI+ reload command is repeatedly dispatched.
- Maintains every original ShopItem object, item ID, price, quantity and metadata. Only active entries are exposed to ShopGUI+ until the next rotation.
- Retains non-trading entries (for example shop links) and leaves shops with 20 or fewer trading entries untouched under automatic management.
- Uses a persisted shuffled bag: items are drawn without duplicates, with preference for items that were *not* in the previous rotation. Across rotations this naturally gives all items a turn.
- Saves active item IDs and remaining shuffled bag to `plugins/ShopGUIPlus-Dynamics/state.yml`. Restarts within the same period keep the same stock. Manual restocks are possible.
- On disable, restores the original in-memory catalogue; on a ShopGUI+ shop reload, rebinds to the fresh original catalogue.

## Installation

1. Build with Java 21 and Maven: `mvn clean verify`. Use the JAR in `target/` (or the GitHub Actions artifact).
2. Install **ShopGUI+** and **ShopGUIPlus-Dynamics** on a 1.21+ Paper server (including 26.3, subject to normal ShopGUI+ compatibility).
3. Start once to create `plugins/ShopGUIPlus-Dynamics/config.yml`; restart/reload after configuring.
4. Keep **all** 53 Blacksmith entries in your existing `plugins/ShopGUIPlus/shops/blacksmith.yml`, including page-2 entries. The addon reads the live ShopGUI+ catalogue and displays just the chosen 20.
5. Ensure ShopGUI+'s shop menu buttons and fixed entries do **not** occupy the reserved 20 slots. The default layout is **36 slots** (four rows), using the 5 × 4 grid in the new texture. Customize `shops.slots` if your bitmap uses a different layout.
6. Verify with `/shopdynamics status`, then `/shopdynamics rotate blacksmith` to test. Use `/shopdynamics reload` after editing **the addon's** config. Use `/shop reload` when changing original ShopGUI+ shop definitions.

**Default 20 inventory slot indices:** (zero-based)

```text
 row 1:  2  3  4  5  6
 row 2: 11 12 13 14 15
 row 3: 20 21 22 23 24
 row 4: 29 30 31 32 33
```

The shop's old `slot` and `page` values are used as source data. Active items are assigned these 20 slots on page 1 in memory; unused items remain in the original ShopGUI+ YAML, ready for future rotations.

## Configuration

```yaml
enabled: true
rotation:
  mode: minecraft-day
  world: world
  interval-days: 1
  real-time-minutes: 20
  check-interval-ticks: 100
  avoid-previous: true
  close-open-shops: true
shops:
  auto: true
  include: []
  exclude: []
  slots: [2, 3, 4, 5, 6, 11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33]
  inventory-size: 36
```

- **Minecraft-day mode:** a day is 24,000 **world full-time ticks** (20 real minutes at 20 TPS, with normal daylight cycle). Change `rotation.world` to the relevant Multiverse world. Rotation occurs when this world's full-day number advances by `interval-days`. `/time set` and daylight-cycle changes affect the schedule. All managed shops share the clock.
- **Real-time mode:** set `rotation.mode: real-time`; `real-time-minutes` controls elapsed wall-clock minutes, and deadlines persist across restarts. On restart after a missed deadline, the next check rotates once rather than fast-forwarding through every missed period.
- **Automatic shops:** `auto: true` manages any shop with **more trading entries than configured slots**. Add IDs to `exclude` to opt out. `include` explicitly manages named shops even with a small catalogue. Set `auto: false` to manage only `include`.
- **Layout:** customize `shops.slots` with distinct, valid zero-based indexes. Set `inventory-size: -1` to retain the original ShopGUI+ size instead of applying a four-row size. Fixed shop entries must not overlap these slots.

## Commands

| Command | Action |
| --- | --- |
| `/shopdynamics status` | Managed shops and rotation clock |
| `/shopdynamics rotate [shopId\|all]` | Rotate one shop or every managed shop immediately |
| `/shopdynamics reload` | Reload only this plugin's configuration |

Alias: `/dynamics`. Permission: `shopdynamics.admin` (operators by default). ShopGUI+ keeps its existing economy and permission checks.

## Safety and limitations

- Source shop files are never rewritten. **Do not delete page-2 catalogue entries** to make them fit.
- Rotating trade items are `type: item` entries. Non-item entries stay fixed; colliding slots or invalid layouts cause a shop to be **skipped with a warning** rather than silently overwriting existing controls.
- A rotation closes currently open inventories **only when their visible title matches that shop's title**. Custom title transformations might prevent matching; players should reopen the shop after a rotation.
- Because unavailable items are not in ShopGUI+'s loaded catalogue, the change affects ShopGUI+ trading/lookup behaviour, not just the visual GUI. Check any external plugins that cache shop item lists.
- This uses the documented public API, but in-memory `setShopItems` live rotation on **your exact ShopGUI+ 1.113.0 build** should be integration-tested in-game, including buy, sell, `/sell all`, open GUIs and `/shop reload`, before production rollout.
- No ProtocolLib, PacketEvents, NMS, YAML source rewrites, or server-restart dependency.

## Development

`mvn verify` compiles against Paper API and ShopGUI+ API (provided at runtime), executes JUnit selection tests, and creates the plugin JAR. GitHub Actions repeats this on PRs.

Licence: see [LICENSE](LICENSE).
