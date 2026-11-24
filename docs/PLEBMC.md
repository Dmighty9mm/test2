# PlebMc: Apollo server API and client mods (EaglercraftX 1.8)

PlebMc is this EaglercraftX 1.8 client with two additions:

1. The client side of the **Apollo** server API, so a Bukkit/Spigot/Paper server
   running the Apollo plugin (or speaking its JSON protocol) can drive waypoints,
   notifications, titles, cooldowns, borders, nametags, glow and the other modules
   below, and can force-disable client mods.
2. PvP/QoL client mods with a mod menu, a draggable HUD editor and an Apollo debug
   screen.

> **Identity.** `lunar:apollo` and `apollo:json` are protocol identifiers needed
> for compatibility, and they are the only places those names appear. The client
> never presents itself as another client: the Apollo handshake carries only the
> Minecraft version (`v1_8`), with no client brand, client version or installed-mod
> list. `MC|Brand` stays Eaglercraft's own, and the UI says "PlebMc" /
> "Server API". No third-party names, logos or assets ship in the UI.

> **No cheats.** Nothing here gives an unfair advantage: no reach, killaura, xray,
> ESP or similar. Apollo's only "staff mod" is X-ray, so the client refuses it.
> Glow outlines, nametags and markers are drawn only when the server sends them.

## Layout

All PlebMc code lives in its own package, `sources/main/java/net/plebmc/client/`.
Vanilla classes only get one-line hooks (in `patches/minecraft/...`).

| Package | What it does |
|---|---|
| `network/PluginChannels` | 1.8 plugin channels: `REGISTER`/`UNREGISTER`, dispatch, payload caps (32767 B out, 1 MiB in), channel names up to 20 chars, per-connection reset |
| `apollo/proto` | hand-written protobuf reader/writer, `google.protobuf.Any`, proto3 JSON (`"@type"`, snake/camel keys, durations, Value/Struct, timestamps). Pure `java.util`, TeaVM-safe |
| `apollo/Apollo` | channels, handshake on join, message router, debug log (last 100 messages), `OverrideConfigurableSettingsMessage` (module enable + options) |
| `apollo/module` | one class per module: state, expiry, flood caps, `reset()` on disconnect or when the server disables it |
| `mods` | FPS, CPS, keystrokes, coordinates, armor, potion effects, ping, clock, toggle sprint, zoom, fullbright; saved toggles + HUD positions |
| `hud` | drawing (vanilla `Gui.drawRect` + `FontRenderer` only), Apollo overlays, entity hooks, mod menu, HUD editor, debug screen |

### Vanilla hooks

Every hook calls `net.plebmc.client.hud.PlebClient` or `PlebEntities`:

| Vanilla class | Hook | Purpose |
|---|---|---|
| `Minecraft.startGame` | `PlebClient.init` | load mods + Apollo |
| `Minecraft.runTick` (key press, no screen) | `PlebClient.onKeyDown` | Right Shift menu, toggle sprint |
| `Minecraft.clickMouse` / `rightClickMouse` | `PlebClient.onWorldClick` | CPS counter |
| `Minecraft.clickMouse` (miss) | `PlebClient.missPenalty` | Apollo combat `disable-miss-penalty` |
| `Minecraft.loadWorld(null)` | `PlebClient.onLeave` | reset every module on disconnect |
| `S01PacketJoinGame.processPacket` | `PlebClient.onJoin` | send `REGISTER` + Apollo handshake |
| `S3FPacketCustomPayload.processPacket` | `PlebClient.onPayload` | incoming plugin messages |
| `GuiIngame.renderGameOverlay` | `PlebClient.renderHud` | HUD mods + Apollo overlays |
| `EntityRenderer.getFOVModifier` | `PlebClient.zoomAllowed` | the built-in C zoom follows the Zoom mod / server block |
| `EntityRenderer.updateLightmap` | `PlebClient.gamma` | fullbright + server brightness rule |
| `EntityRenderer.renderWorldPass` | `PlebEntities.renderGlow` | glow outlines |
| `EntityPlayerSP` | `sprintHeld`, `borderBlocks` | toggle sprint, Apollo border cancel |
| `FontRenderer` | `PlebClient.filterText` | nick hider |
| `RendererLivingEntity` / `Render` | `nametag`, `nametagRange`, `flipped`, `applyFireColor` | nametags, server nametag distance, flipped mobs, colored fire |
| `RenderPlayer` / `LayerArmorBase` | `applyLimbs`, `armorHidden` | hidden body parts and armor |
| `RenderTNTPrimed` | `tntLabel` | TNT countdown |
| `LayerSheepWool` | `rainbowSheep` | rainbow sheep |

### Safety rules

- Every payload is decoded defensively. Malformed data, unknown message types and
  messages for disabled modules are logged in the debug screen and dropped. A bad
  payload never throws into the game loop or disconnects you (fuzzed with 25k
  mutated payloads in `ApolloProtoTest`).
- Unknown fields from newer protocol versions are skipped and counted.
- Every module keeps bounded state (for example 256 waypoints, 1024 nametag overrides).
- Disconnecting or switching server clears every module, all server rules and
  every mod override.

## Modules

| Tier | Module | Shown in game | Notes |
|---|---|---|---|
| 1 | waypoint | yes | beam + label + distance, through walls |
| 1 | notification | yes | top-right toasts |
| 1 | title | yes | title/subtitle with server scale and fades |
| 1 | cooldown | yes | countdown tiles above the hotbar |
| 1 | team | yes | colored marker + name above teammates |
| 1 | nametag | yes | server lines replace the vanilla nametag |
| 1 | border | yes | wall edges near you, distance warning, blocks movement when cancelled |
| 1 | server_rule | yes | brightness override, nametag render distance |
| 1 | mod_setting | yes | `"<mod>.enabled" = false` force-disables a client mod |
| 1 | rich_presence | yes | mod menu status line and debug screen |
| 2 | beam | yes | colored column |
| 2 | glow | yes | colored outline around the player, through walls |
| 2 | hologram | yes | floating text lines (64-block range) |
| 2 | colored_fire | yes | tinted fire on burning players |
| 2 | glint | options | options stored |
| 2 | limb | yes | hidden body parts and armor pieces |
| 2 | vignette | yes | darkened edges at the server opacity |
| 2 | stopwatch | yes | top-center timer |
| 2 | tnt_countdown | yes | seconds above primed TNT (server fuse if set) |
| 2 | nick_hider | yes | your name is replaced everywhere text is drawn |
| 3 | chat | yes | live lines bottom-left |
| 3 | combat | yes | `disable-miss-penalty` removes the 1.8 miss cooldown when the server asks |
| 3 | entity | yes | flipped entities, rainbow sheep |
| 3 | staff_mod | refused | X-ray only, refused by the no-cheats rule (`IGNORED_POLICY`) |
| 3 | transfer | answered | pings answer TIMED_OUT, transfers REJECTED (a browser cannot open raw TCP) |
| - | pay_now / tebex | declined | no embedded store checkout |

## Client mods

**Right Shift** in game opens the PlebMc home screen: a door to each of the client's
own screens (number keys 1 to 6 work too). Settings > Behavior can make Right Shift
open Mods, Quests or the Shop directly instead. Every screen closes with Esc or Right Shift.

| Screen | What it does |
|---|---|
| **Mods** | Every mod as a card with its state (on, off, or locked when the server blocks it). Click a card to toggle it; right-click it or its gear for its settings page (every option, reset to defaults, "Move on HUD"). Categories on the left (HUD, Gameplay, Squad, Performance) with on/total counts, and a search box (start typing anywhere). |
| **Shop** | Cosmetics only: Hats and Trails tabs (owned/total), item cards with price, Owned, Equipped or Exclusive (never sold), pages, and your player on the left (drag or arrow keys to spin). Picking a card tries it on (only you see it, until you leave). Buy, Equip and Unequip go to the server. |
| **Quests** | All / General / PlebNite on the left with done/total counts and a dot for new quests; Daily and Weekly sections with reset timers; each quest's progress bar, count and reward; finished quests fold into compact "claimed" rows; the daily login row with streak pips. |
| **HUD** | The HUD editor (below). |
| **Account** | plebmc.top login: status, profile (name, ranks), log in with a code, log out. |
| **Settings** | Interface (accent color, menu background, animations, click sounds), Notifications (popups, popup time), HUD (text shadow), Performance (resolution, FPS Boost, FPS counter), Behavior (what Right Shift opens), Developer (Apollo debug). |

| Mod | Id (server option key) | Default | Notes |
|---|---|---|---|
| FPS | `fps` | on | |
| CPS | `cps` | on | left \| right |
| Keystrokes | `keystrokes` | on | WASD, LMB/RMB with CPS, jump |
| Coordinates | `coordinates` | on | XYZ + facing |
| Armor | `armorstatus` | off | durability |
| Potion effects | `potion-effects` | off | name, level, time left |
| Ping | `ping` | off | from the tab list latency |
| Clock | `clock` | off | local time |
| Toggle Sprint | `toggle-sneak` | off | tap sprint; server option `toggle-sprint=false` blocks it |
| Zoom | `zoom` | on | hold **C** (Eaglercraft's zoom key, scroll to adjust); blocked when the server disables `zoom` |
| Fullbright | `lighting` | off | server option `full-bright=false` blocks it; a server brightness rule wins |
| Compass | `compass` | on | heading strip (top center) with a pip for every teammate and waypoint |
| Squad Ping | `squad-ping` | on | **Sneak + F** sends the swap-hands action modern servers use for pings (Apollo servers only) |
| FPS Boost | `fps-boost` | off | fastest video settings while on; your own values come back when it's off |

The **HUD editor** draws the live HUD with a side panel. Drag an element to move it: it
snaps to the screen edges, the center line and other elements, always stays fully on
screen, and if you drop it on another element it moves to the nearest free spot (or back
where it was). Drag the corner handle or scroll over an element to resize it (50% to
200%); arrow keys nudge it by a pixel. The panel lists every HUD element with a show/hide
eye; selecting one shows its size, text color and background, "Reset position", "Reset
look" and its other options. "Reset all" (click twice) puts every element back. Positions
are saved as screen fractions in browser storage (`plebmc.mods`), so they survive resizes
and GUI-scale changes.

Mod options are saved per mod (`"opts"` in `plebmc.mods`); client settings in
`plebmc.prefs`; which quests you've seen (for the NEW tags) in `plebmc.questsSeen`.

**Apollo debug** (turn on Settings > Developer) shows each module's state and the last 100
messages (green handled, orange ignored, red error, cyan sent). "Verbose log" prints every
plugin message to the browser console; "Preview features" fills every module, the shop,
quests and account with demo data.

## Squad and match features (PlebNite)

How each PlebNite feature reaches the screen on this 1.8 client. All of them come from the
server through the normal Apollo modules; "Apollo debug > Preview features" fakes a set of
them around you in singleplayer so you can check every overlay without a server.

| Feature | Apollo module | What this client draws |
|---|---|---|
| Teammate markers | team | colored chevron above each teammate (through walls), name + distance when out of range, and a pip on the Compass |
| Teammate glow / knocked red | glow | a 1.9-style outline in the glow color, through walls (offscreen mask, grown and hollowed). Off while shaders are on |
| Teammate nametags | nametag | the server's lines (health, KNOCKED) replace the vanilla nametag |
| Ping waypoints, Teammate DOWN | waypoint | sky beam in the world, label + distance on screen, pip on the Compass |
| Knocked beam | beam | sky beam in the world (HUD column fallback with shaders on) |
| Cooldown icons | cooldown | item icon (modern item names mapped to 1.8, or guessed from the name: grapple, axe, bow), a clockwise sweep and seconds left |
| Match timer | stopwatch | top center, under the Compass |
| Vignette | vignette | the server's texture (vanilla vignette if missing) blended like the vanilla vignette at the server's opacity |
| TNT countdown | tnt_countdown | seconds above primed TNT from the server's fuse (thrown TNT 40 ticks); the TNT's flash and swell follow that fuse |
| Pop-ups | notification | top-right toasts with an accent bar and a time-left bar |

**Sneak + F.** PlebNite marks a spot when a player swaps hands while sneaking. A 1.8
client has no off hand, so the Squad Ping mod sends the 1.9+ "swap item with off hand"
action itself (player digging packet, action 6). The version translator in front of the
server passes it through as a swap. It is only sent to servers that speak Apollo, because
a real 1.8 server doesn't know action 6.

**Apollo protocol version.** Message tables come from lunar-apollo-protobuf 1.0.2. Newer
fields are skipped and counted; any newer message type shows in the debug screen as
orange "unknown".

## PlebMc server, FPS and resolution

- **Fps Larp** (secret): type `fpslarp` while the mod menu is open. It changes only the number the FPS counters show (FPS mod, the debug line and F3), never how the game runs: a multiplier (1.5x to 100x), an extra amount, or a locked number, plus a max, jitter so it looks real, update speed, text style and color. Each part can stay real. Settings are saved in the browser.

- The title screen has **Join PlebMc** (connects to `wss://eagler.plebmc.top`) and **Discord** (https://discord.gg/bR89zXFH9h). PlebMc is always first in the server list (a default entry, so it can't be deleted).
- **Resolution** (mod menu): draws the game at 100/85/75/60/50% of the window's pixels and stretches it back up. Most browsers are limited by fill rate, so this is the biggest FPS lever.
- **FPS Boost** (mod menu): 75% resolution (unless you picked lower), ignores high-DPI scaling, turns vsync off, caps render distance at 8 chunks, and switches clouds, particles, fancy graphics, smooth lighting, entity shadows and FXAA to their fastest values. Turning it off restores your own values. Measured in the test browser: 17 FPS off, 27 FPS on, same view.

## Cosmetics shop protocol

Cosmetics (hats and trails) are bought with the server's coins (on PlebMc: PlebMc Coins, earned from quests, a daily login reward and time online). The server owns the balance, ownership and what is equipped; the client only displays them and sends requests. Messages are UTF-8 JSON objects on the plugin channel `plebmc:shop`. The client sends `REGISTER plebmc:shop` when it joins (Bukkit needs this before it will deliver messages to the client). Open it with Right Shift, then **Shop** or **Quests**. "Try on" works everywhere and is local only.

**Client to server**

| Message | When |
| --- | --- |
| `{"type":"open"}` | On join and when the shop opens: send a `state` back. |
| `{"type":"buy","id":"crown"}` | Buy an item. Check the price and balance, take the coins, reply with `result` and a fresh `state`. |
| `{"type":"equip","category":"hat","id":"crown"}` | Equip an owned item (`"id":null` takes the slot off). Reply with `state`, and send the change to nearby players as `players`. |

**Server to client**

| Message | Meaning |
| --- | --- |
| `{"type":"state","coins":1250,"owned":["tophat"],"equipped":{"hat":"tophat","trail":"flame"},"catalog":[...]}` | Balance, owned ids, equipped slots. Every field is optional; a missing field keeps its last value. `catalog` replaces the built-in list. |
| `{"type":"players","players":{"<uuid>":{"hat":"crown","trail":"rainbow"},"<uuid2>":null}}` | What other players wear (so everyone sees each other's cosmetics). `null` or `{}` removes a player. `"clear":true` drops everyone first (send on world change). |
| `{"type":"result","ok":false,"message":"Not enough coins","coins":100}` | Answer to buy/equip, shown in the shop. `coins` is optional. |
| `{"type":"notify","title":"§aQuest complete!","message":"Get 3 kills: +75 PlebMc Coins","coins":425}` | A popup (top right, like Apollo notifications). `coins` optional. |

`state` can also carry the quests: `"currency":"PlebMc Coins"`, `"dailyResetIn":18720` and `"weeklyResetIn":...` (seconds), `"loginStreak":4`, `"questCategories":[{"id":"general","name":"General","icon":"general","color":"#3FD8E6"},{"id":"plebnite","name":"PlebNite","icon":"plebnite","color":"#FF9A3C"}]` (one entry each on the Quests screen, after "All"; `icon` and `color` are optional), and `"quests":[{"id":"kills_3","category":"plebnite","cycle":"2026-11-24","name":"Get 3 kills","description":"...","period":"daily","progress":1,"goal":3,"reward":75,"done":false}]` (`period` is `daily`, `weekly` or `login`; `cycle` names the day or week the quest belongs to, so the client can tag it NEW again when it comes back; the login row also has `"streak"` and `"streakLength"`; a quest without `category` shows in every category). Rewards are paid by the server the moment a quest is done, once; the client shows done quests as claimed.

**Website login (plebmc.top).** `{"type":"account"}` asks for the player's website login; `{"type":"link","code":"ABC123"}` links the Minecraft account with a code from the website's Connections page. The server answers `{"type":"account","enabled":true,"linked":true,"username":"Master","displayName":"Master","groups":["Owner","Member"],"staff":true,"site":"https://plebmc.top","linkUrl":"https://plebmc.top/index.php?route=/user/connections","message":"Logged in as Master!","ok":true}` (`message`/`ok` only after a link attempt or an error). It also sends this on join once it has checked the website. `{"type":"logout"}` logs out: the server unlinks the Minecraft account on the website (`users/integration_id:minecraft:<uuid>/integrations/unlink`) and answers with `"linked":false`.

Catalog items that are never sold (given by staff or events) carry `"forSale":false` (and, for older clients, a price nobody can reach); the shop shows them as Exclusive.

Catalog item: `{"id":"gold_tophat","name":"Gold Top Hat","category":"hat","render":"tophat","price":900,"color":"#FFD700","description":"..."}`. `category` is `hat` or `trail`. `render` is the look the client draws: hats `tophat`, `crown`, `halo`, `partyhat`, `beanie`, `horns`; trails `flame`, `hearts`, `notes`, `rainbow`, `cloud`, `portal`, `emerald`, `snow`. `color` tints a hat (optional). Items with a `render` this client doesn't know are hidden, so newer items don't break older clients. Without a catalog the client shows the built-in one (ids equal to the render names, prices 250 to 2,500).

### Server plugins

`plugins/PlebMcShop` implements this protocol on **BungeeCord**, so coins, quests and
cosmetics are shared by every server behind the proxy. It answers `plebmc:shop` itself
(those messages never reach the backends), sends `players` only to people on the same
server, and keeps everything in `players.yml` on the proxy. Its own PlebMc Coins are
separate from in-game economies. Quests are grouped in categories, each with its own
daily and weekly pool and a list of servers it counts on: by default **General** (`'*'`,
every server) and **PlebNite** (only the PlebNite server).

`plugins/PlebMcBridge` runs on the backends and reports quest events (kills, deaths,
matches, worlds, `/plebquest <player> <type>`) to the proxy as `PlebMcBridge` messages on
the standard `BungeeCord` channel: `{"player":"<uuid>","type":"kill","amount":1}` or
`{"player":"<uuid>","world":"arena1"}`. The proxy counts an event for every category
covering the server it came from (General everywhere, PlebNite only on PlebNite). Chat
messages count for `chat` quests on the proxy itself, at most one every 10 seconds. See both plugins' READMEs.

## Testing with a real server (Spigot/Paper 1.8.8 + Apollo + EaglerXBungee)

Requirements: Java 8 for the 1.8.8 server, Java 17+ for BungeeCord.

1. **Server.** Put a Spigot or Paper 1.8.8 jar in `server/`, run it once, accept
   the EULA, then in `server.properties` set `online-mode=false`, `server-port=25566`.
   In `spigot.yml` set `settings.bungeecord: true`.
2. **Apollo.** Put the Apollo Bukkit plugin jar (`apollo-bukkit-*.jar`, from the
   Apollo GitHub releases) in `server/plugins/`. For the commands below also add
   Apollo's example plugin (`apollo-example-bukkit-proto`, or `-json` to test
   `apollo:json`). Restart.
3. **BungeeCord + EaglerXBungee.** Run BungeeCord with
   `gateway/EaglercraftXBungee/EaglerXBungee-Latest.jar` in its `plugins/`. In
   BungeeCord `config.yml` point the default server at `localhost:25566` and set
   `ip_forward: true`. EaglerXBungee listens for browser clients on its configured
   websocket port (default `ws://localhost:8081`). Plugin messages pass through
   BungeeCord to the server unchanged.
4. **Client.** Open the compiled client, Multiplayer, add `ws://localhost:8081`, join.
5. Press **Right Shift > Apollo debug**: the header should say "server speaks
   Apollo", and `OverrideConfigurableSettingsMessage` should be listed as handled.

## Test command list (Apollo example plugin)

Run as an op in chat. The expected result is in the last column.

| Command | Module | Expected |
|---|---|---|
| `/waypoint display` / `remove` / `reset` | waypoint | beams + labels; removed; all gone |
| `/notification display` / `reset` | notification | toast top-right for about 5 s |
| `/title display` / `displayInterpolated` / `reset` | title | big centered title; scale animates |
| `/cooldown displayItem` / `remove` / `reset` | cooldown | ender pearl tile counting down 15 s |
| `/border display` / `remove` / `reset` | border | colored edges nearby; you cannot walk in/out when cancelled |
| `/team create` / `delete` | team | colored markers above teammates |
| `/nametag override` / `reset` | nametag | multi-line nametag above the player |
| `/richpresence override` / `reset` | rich_presence | mod menu status line |
| `/serverrule nametagrenderdistance 32` | server_rule | nametags vanish beyond 32 blocks |
| `/modsettings disable` / `reset` | mod_setting | Fullbright shows "Blocked by server"; reset restores |
| `/beam display` / `remove` / `reset` | beam | colored column |
| `/glow override <player>` / `reset` | glow | colored outline around that player |
| `/hologram display` / `remove` / `reset` | hologram | floating lines |
| `/coloredfire override <player>` | colored_fire | that player's fire is tinted |
| `/limb hideArmor <player>` / `hideBody <player>` | limb | armor / body parts disappear |
| `/vignette display` / `reset` | vignette | darkened edges |
| `/stopwatch stopwatch start` / `stop` / `reset` | stopwatch | top-center timer |
| `/tntcountdown set` then light TNT | tnt_countdown | seconds above the TNT |
| `/nickhider override` / `reset` | nick_hider | your name shows as the nick |
| `/chat display` | chat | live line counting down bottom-left |
| `/combat disablemisspenalty true` | combat | swinging at air no longer delays your next hit |
| `/entity flipEntities` / `rainbowSheep` | entity | upside-down mobs / rainbow sheep |
| `/staffmod enable` | staff_mod | **refused** (orange `IGNORED_POLICY` line) |
| `/transfer ping` / `transfer` | transfer | server receives TIMED_OUT / REJECTED |

To block zoom from your own plugin over JSON on `apollo:json`:

```json
{"@type":"type.googleapis.com/lunarclient.apollo.configurable.v1.OverrideConfigurableSettingsMessage",
 "configurable_settings":[{"apollo_module":"mod_setting","enable":true,
   "properties":{"zoom.enabled":false}}]}
```

## Automated tests

`sh tests/plebmc/run_tests.sh` (JDK only, no Minecraft files needed):

| Test | Checks | What |
|---|---|---|
| `PluginChannelsTest` | 33 | 1.8 REGISTER/UNREGISTER, channel rules, caps, reset |
| `ApolloProtoTest` | 116 | decoder vs real serialized messages (`fixtures.txt`), byte-identical re-encode, JSON, 25k fuzz |
| `ApolloClientTest` | 44 | handshake (`v1_8`), routing, enable/disable, overrides, policy refusals, expiry, reset |
| `ClientModsTest` | 28 | HUD layouts, saved layout, every server override of the client mods |

## Licenses

- **EaglercraftX 1.8 (this repository):** `LICENSE` is "Copyright (c) 2022-2024
  lax1dude, ayunami2000. All Rights Reserved." It is not an open-source license.
  Keep the notice, `CREDITS` and the in-game credits. Redistributing a modified
  client publicly is up to lax1dude's terms, not this document.
- **Minecraft / MCP:** this repository must not contain decompiled Minecraft code,
  the Minecraft jar or MCP files. They stay in `mcp918/` (gitignored); only patch
  files are committed.
- **Apollo** (github.com/LunarClient/Apollo, the server plugin and its examples):
  MIT. No Apollo code is included; message and field names/numbers are protocol
  facts used for interoperability.
- **`lunar-apollo-protobuf`** (npm, GPLv3) is not vendored. It was only read to
  extract field numbers and used as an independent encoder for test fixtures.
