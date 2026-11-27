# PlebMc and PlebClient

This document covers everything built for PlebMc so far: the clients, the server plugins,
how to install them, and the technical reference for developers.

- **PlebClient** is PlebMc's own Minecraft 1.8 client. It comes as a browser client
  (Eaglercraft, an offline HTML file), a Windows desktop app, and a Forge 1.8.9 mod for
  Minecraft Java Edition.
- **PlebMc** is the server network (`play.plebmc.top`, `wss://eagler.plebmc.top`). The
  client talks to it through the server plugins described below.

Part 1 is for players and server owners. Part 2 is the install guide for every plugin.
Part 3 is the developer reference (code layout, hooks, protocols, tests).

## Contents

- [Status: what is merged and what is still in review](#status-what-is-merged-and-what-is-still-in-review)
- Part 1: Features
  - [The clients](#the-clients)
  - [Title screen and menu design](#title-screen-and-menu-design)
  - [The Right Shift screens](#the-right-shift-screens)
  - [Client mods](#client-mods)
  - [PlebNite mods](#plebnite-mods)
  - [Fps Larp (secret mod)](#fps-larp-secret-mod)
  - [FPS Boost and resolution](#fps-boost-and-resolution)
  - [PlebMc Coins, the shop and quests](#plebmc-coins-the-shop-and-quests)
  - [Cosmetics: hats and HD cloaks](#cosmetics-hats-and-hd-cloaks)
  - [Website login (plebmc.top)](#website-login-plebmctop)
  - [Owner admin menu](#owner-admin-menu)
  - [Account security on a cracked server](#account-security-on-a-cracked-server)
  - [Apollo server features (waypoints come from the server)](#apollo-server-features-waypoints-come-from-the-server)
  - [Custom items: QualityArmory guns, totems and netherite](#custom-items-qualityarmory-guns-totems-and-netherite)
  - [Hex colors and gradients](#hex-colors-and-gradients)
  - [FancyHolograms armor stand fix](#fancyholograms-armor-stand-fix)
  - [Web tools: Cloak Maker, Hat Maker, Client Studio](#web-tools-cloak-maker-hat-maker-client-studio)
  - [Known limits](#known-limits)
- Part 2: [Server install guide](#part-2-server-install-guide)
- Part 3: [Developer reference](#part-3-developer-reference)

## Status: what is merged and what is still in review

Only PR #1 (Apollo and the first mods) and PR #6 (model hats, admin menu, menu theme, merged
into the #5 branch) are merged. Everything else is in open pull requests that stack on each
other. Merge them in this order for the smallest diffs:

| PR | Branch | What it adds |
|---|---|---|
| #1 (merged) | `claude/project-thread-kgpjy6` | Apollo server API, first PvP/QoL mods, mod menu, HUD editor |
| #2 | `claude/project-thread-tf9pxh` | PlebNite Apollo features, FPS Boost, Join PlebMc / Discord buttons, coins shop, quests, website login, Fps Larp, the screen redesign, PlebNite mods, HD cloaks |
| #3 | `claude/project-thread-y7on4g` | Forge 1.8.9 port (`java-client/`), PlebMcShop renamed to PlebioAPI |
| #4 | `claude/project-thread-we2cag` | Custom item models, totems and netherite, item NBT log, rename to PlebClient, crown model, bundled PlebNite pack |
| #5 | `claude/project-thread-lfv0pq` | Hex colors and gradients, PlebColors plugin, hologram fix (merges #3 and #4) |
| #6 (merged into #5) | `claude/project-thread-kwi4dk` | Model hats like cloaks, Owner admin menu, menu theme, Storm Tracker off by default |
| #8 | `claude/project-thread-kwi4dk` | New title screen, shop collections, trails removed, animated cloaks, EaglercraftX u53 port (with WASM), 20 more mods, web tools |
| #7 | `claude/project-thread-c0rppo` | Admin menu needs nLogin `/login` and a separate `/adminlogin` password |
| #9 | `claude/project-thread-d65x79` | PlebMc accounts: self-hosted panel at `client.plebmc.top`, sign-in required on the client, PlebClient-only coins (builds on #7) |

#7 and #9 branch off the #5 branch, not off #8, so they will need merging with #8.
None of this has been tested against the live servers, because the build machine can't reach
them; it was tested with unit tests, simulated servers and the real client in a headless browser.

# Part 1: Features

## The clients

| Client | File | How to use it |
|---|---|---|
| Browser (offline HTML) | `PlebClient_1.8_Offline.html` | Open it in any browser. Works on Chromebooks and school computers. Settings and worlds are kept in the browser's storage. |
| Windows desktop app | `PlebClient-Windows.zip` | Unzip the whole folder and run `PlebClient.exe`. It is the same browser client in its own window (an Electron wrapper), without the browser's 60 FPS cap. If Windows says the app isn't signed, click More info, then Run anyway. Its settings and worlds are separate from the browser's. |
| Java Edition (Forge 1.8.9) | `PlebMc-Forge-1.8.9.zip` (contains `PlebMc-1.0.0-forge-1.8.9.jar`) | Install Forge 1.8.9 (`11.15.1.2318` or newer), put the jar in `%APPDATA%\.minecraft\mods`, start the Forge 1.8.9 profile. |

The newest builds are in the project files under `plebmc/` (each feature batch has its own
folder, for example `plebmc/menus-admin/`). Jars are shipped inside zips because the app
won't download `.jar` files directly.

**Browser client base.** The browser client was built on EaglercraftX 1.8 **u35** (the
patch tree in this repository). PR #8 ports it to **u53** (`client-u53/`, see its README),
lax1dude's newest release, which can also build a WASM-GC version. See
[Known limits](#known-limits).

**Forge port.** `java-client/` is the same PlebClient code as a Forge mod. Java players get
the same screens, mods, shop, quests, cosmetics, login, Apollo features, hex colors and
hologram fix, and it talks to the same server plugins. Differences:

- Join PlebMc connects to `play.plebmc.top` instead of `wss://eagler.plebmc.top`.
- No Resolution setting (Java draws at the window size). FPS Boost still switches vsync,
  render distance and the fast video settings.
- Zoom is its own key (C, rebind under Controls > PlebMc), because Java has no built-in zoom.
- Settings are saved in `.minecraft/config/plebmc/`.
- OptiFine works alongside. Glow outlines and sky beams turn off while OptiFine shaders are on
  (the HUD markers stay).
- Custom item models (QualityArmory) are only in the browser and Windows clients so far.
- The Forge jar builds and its mixins resolve, but the newest Forge builds have not been run
  in a real Forge client.

**Name.** The client calls itself **PlebClient**. Things about the server keep the PlebMc
name ("Join PlebMc", "PlebMc Coins").

## Title screen and menu design

**Main menu.** The title screen always offers **Play PlebMc** (joins the PlebMc server
directly) and **Discord** (https://discord.gg/bR89zXFH9h), and PlebMc is pinned first in the
server list as a default entry that can't be deleted. The browser client joins
`wss://eagler.plebmc.top`; the Forge client joins `play.plebmc.top`.

**Menu redesign (PR #6).** `PlebTheme` restyles every vanilla menu in the PlebClient look:
dark glass buttons with an accent Join button, themed sliders, backgrounds and lists, and a
PLEBCLIENT wordmark instead of the Minecraft logo. **Settings > PlebClient menus** turns it
off and brings back the classic menus.

**New title screen (PR #8).** With the PlebClient menus on, the title screen is replaced by:

- the PlebMc wallpaper (or the Minecraft panorama) as the background, with a slight parallax;
- a transparent sidebar with the mascot head as its logo: Play PlebMc, Singleplayer,
  Multiplayer, Wardrobe, Mods, Quests, Customize, Options, Edit Profile, then Discord and
  Language (Forge: Forge Mods and Quit). Arrow keys and Enter move through it, 1 to 9 open
  an entry;
- a 3D showcase of your skin wearing the hat and cloak you last wore on PlebMc, with a
  `store.plebmc.top` pill that opens the store;
- your Pleb Coins from your last visit (top right, opens the shop);
- a clickable mini mascot that says tips in a speech bubble;
- snow, ember or firefly particles.

**Settings > Title screen** (the Customize entry) sets the sidebar mode, background, dimming,
mascot, particles, clock, splash text and the accent color (12 colors). PR #8 also adds a
**Menu background** setting (blurred wallpaper by default, wallpaper, dark, glow or the old
grid) and a **Window style** (solid, glass or clear).

## The Right Shift screens

**Right Shift** in game opens the PlebClient home screen, a door to each of the client's own
screens (number keys work too). Settings > Behavior can make Right Shift open Mods, Quests or
the Shop directly. Every screen closes with Esc or Right Shift. Each feature has its own
screen; nothing is crammed into one giant menu.

| Screen | What it does |
|---|---|
| **Mods** | Every mod as a card showing on, off, or locked (blocked by the server). Click to toggle, right-click or the gear for its settings page (every option, reset, "Move on HUD"). Categories on the left (All, PlebNite, HUD, Gameplay, Visual, Performance) with on/total counts, and a search box (start typing anywhere). |
| **Shop** | Cosmetics only. Collection tabs (PlebMc, Partners, or what the server sends), Hats and Cloaks tabs, an Owned tab, item cards with 3D hat thumbnails or the cloak's own art, price and Owned / Equipped / Exclusive badges, pages, and your player on the left (drag or arrow keys to spin; offline it wears your own skin). Picking a card tries it on (only you see it). Buy, Equip and Unequip go to the server. |
| **Quests** | All / General / PlebNite with done/total counts and a dot for new quests. Daily and Weekly sections with reset timers, progress bars, rewards, compact "claimed" rows for finished quests, and the daily login row with streak pips. |
| **HUD** | The HUD editor (see below). |
| **Account** | plebmc.top login: status, website name and ranks, log in with a code, log out. With PR #9: Sign in to your PlebMc account. |
| **Admin** | Only for website Owners (see [Owner admin menu](#owner-admin-menu)). |
| **Settings** | Interface (accent color, menu background, window style, animations, click sounds), Title screen, Notifications, HUD (text shadow, brackets, bold/italic), Performance (resolution, FPS Boost, FPS counter), Behavior (what Right Shift opens, RGB text colors, Clean holograms, Custom item models), Developer (Apollo debug, item NBT log), and export/import of every setting through the clipboard (never the login). |

**HUD editor.** Drag an element to move it: it snaps to the screen edges, the center line and
other elements, always stays on screen, and if you drop it on another element it moves to the
nearest free spot. Drag the corner handle or scroll to resize (50% to 200%); arrow keys nudge
by a pixel. The side panel lists every element with a show/hide eye, its size, text color
(including Accent and Chroma), background, "Reset position" and "Reset look". "Reset all"
(click twice) puts everything back. Positions are saved as screen fractions, so they survive
resizes and GUI-scale changes.

**Apollo debug** (Settings > Developer) shows each Apollo module's state and the last 100
messages. "Preview features" fills every module, the shop, quests and account with demo data,
so you can see every overlay in singleplayer without a server.

## Client mods

Each mod has an id; a server can force a mod off with the Apollo mod setting
`"<id>.enabled" = false` (see Part 3). Mods are saved per player (`plebmc.mods` in browser
storage, `config/plebmc/` on Forge).

| Mod | Id | Default | What it does |
|---|---|---|---|
| FPS | `fps` | on | Frames per second |
| CPS | `cps` | on | Clicks per second, left \| right |
| Keystrokes | `keystrokes` | on | WASD, LMB/RMB with CPS, jump |
| Coordinates | `coordinates` | on | XYZ and facing, one-line layout, custom labels |
| Armor | `armorstatus` | off | Worn armor and the held item as real item icons with enchant glint, a green-to-red durability bar and uses left; vertical or horizontal |
| Potion effects | `potion-effects` | off | Name, level, time left |
| Ping | `ping` | off | From the tab list latency |
| Clock | `clock` | off | Local time |
| Toggle Sprint | `toggle-sneak` | off | Tap to sprint; servers can block it (`toggle-sprint=false`) |
| Zoom | `zoom` | on | Hold **C**; options for zoom level, scroll to adjust and smooth camera; servers can block it |
| Fullbright | `lighting` | off | Full brightness; servers can block it (`full-bright=false`) |
| FPS Boost | `fps-boost` | off | See [FPS Boost and resolution](#fps-boost-and-resolution) |
| Speed | `speed` | off | How fast you're moving |
| Day Counter | `day-counter` | off | The in-game day and time |
| Biome | `biome` | off | The biome you're in |
| Server Address | `server-address` | off | The server you're on |
| Arrow Counter | `arrow-counter` | off | Arrows in your inventory |
| Light Level | `light-level` | off | Block light where you stand |
| Session Timer | `session-timer` | off | How long you've played this session |
| Crosshair | `crosshair` | off | Your own crosshair: 7 styles, color, size, gap, dot, outline, invert |
| Scoreboard | `scoreboard` | off | Hide the red numbers, background, size, side, shadow, or hide it |
| Chat | `chat` | off | Timestamps and stacking repeated messages |
| Hurt Camera | `hurt-cam` | off | Less or no screen shake when hit |
| Block Outline | `block-outline` | off | Color (including chroma), thickness, opacity and face fill |
| Clear Weather | `clear-weather` | off | Hides rain and snow (and optionally splashes and rain sound) |
| Overlays | `overlays` | off | Hide the pumpkin blur and vignette, lower or hide the fire overlay |
| Death Coordinates | `death-coords` | on | Prints where you died in chat |

The mods from Speed down to Death Coordinates came with PR #8. Every HUD mod's text color
can also be the accent color or Chroma (a moving rainbow).

## PlebNite mods

The PlebNite category in Mods holds everything made for PlebNite matches:

| Mod | Id | Default | What it does |
|---|---|---|---|
| Compass | `compass` | on | Heading strip at the top with a pip for every teammate and waypoint, and a square pointing at the middle of the storm (red while you're outside it) |
| Squad Ping | `squad-ping` | on | **Sneak + F** marks a spot for your squad (see below) |
| Squad | `squad-hud` | on | Each teammate with their color, a health bar (gray when too far to know) and distance |
| Supplies | `supplies` | off | Blocks, arrows, golden apples, pearls, potions and TNT in your inventory as icons with counts |
| Low Health Warning | `low-health` | on | Red screen edges at 2 to 5 hearts, pulsing faster the lower you are |
| Storm Tracker | `storm` | **off** | Distance to the storm edge (red "IN THE STORM" with the way back), closing/opening timer, optional safe-zone size. Read from the world border, hidden when there is none |

**Storm Tracker is off by default** since PR #6; players turn it on in Mods > PlebNite.

**Teammates through walls.** PlebNite marks teammates with Apollo's team and glow modules.
The client draws a colored chevron above each teammate through walls (with name and distance
when far away), a 1.9-style outline in the teammate's color (red when knocked), the server's
nametag lines (health or KNOCKED), and a pip on the Compass. Glow outlines turn off while
shaders are on.

**Squad pings (Sneak + F).** PlebNite places a ping when a player swaps hands while sneaking.
A 1.8 client has no off hand, so the Squad Ping mod sends the 1.9+ "swap item with off hand"
action itself (player digging packet, action 6), which Via passes through as a swap. The
server then sends the ping waypoint and sky beam to the squad for 8 seconds. It is only sent
to servers that speak Apollo, because a real 1.8 server doesn't know action 6.

**Armor HUD.** The Armor mod draws the armor you wear and your held item as the real item
icons, each with a durability bar and uses left that turns green, yellow, then red.

The other PlebNite features the server sends (cooldown icons for gadgets, the grapple, the
Quake Axe and the Phase Bow, the match timer, the knocked vignette, the real TNT fuse, and
the pop-ups for game modes, first blood, kill streaks, Champion, first win, top 5, revives
and daily rewards) are covered in
[Apollo server features](#apollo-server-features-waypoints-come-from-the-server).

## Fps Larp (secret mod)

Open Mods and type `fpslarp` (in the search box; there is no button). It opens the Fps Larp
settings. It only changes the number the FPS counters show (the FPS mod, the debug line and
F3), never how the game runs. You can set:

- a multiplier (1.5x up to 100x), extra FPS on top, or a locked number (like 999);
- a max it never goes above;
- jitter, so the number wobbles and looks real;
- how often the number updates;
- the text style and color (rainbow too).

Each part can stay real. Settings are saved with the client.

## FPS Boost and resolution

- **Resolution** (Settings > Performance): draws the game at 100/85/75/60/50% of the window's
  pixels and stretches it back up. Browsers are usually limited by fill rate, so this is the
  biggest FPS lever. Browser and Windows only.
- **FPS Boost** (mod): 75% resolution (unless you picked lower), ignores high-DPI scaling,
  turns vsync off, caps render distance at 8 chunks, and switches clouds, particles, fancy
  graphics, smooth lighting, entity shadows and FXAA to their fastest values. Turning it off
  restores your own values. Measured in the test browser: 17 FPS off, 27 FPS on, same view.
  On Forge it changes everything except the resolution. On the u53 WASM build the canvas size
  is set by the loader, so the resolution part only applies to the JavaScript build.

## PlebMc Coins, the shop and quests

**PlebMc Coins** are PlebClient's own currency, separate from every in-game economy (BRX coins
and so on). They live in the **PlebioAPI** plugin on the BungeeCord proxy, so one balance,
one set of quests and one wardrobe work on every server (Lobby and PlebNite). The plugin was
called PlebMcShop before; it moves its data folder by itself on first start.

**Earning (defaults):**

| What | Default |
|---|---|
| Daily login (every 20 h; streak resets after 48 h) | 50, 75, 100, 125, 150, 200, 300, then repeats |
| Time online | 10 coins every 15 minutes |
| General quests: 3 daily from 4, 2 weekly from 3 | 40 to 500 each |
| PlebNite quests: 3 daily from 6, 2 weekly from 4 | 60 to 750 each |

**Quests** have two categories: **General** (counts on every server: playtime, joining
servers, chat, the 5-day login) and **PlebNite** (counts only on PlebNite: kills, matches,
wins, playtime). The client shows them in one Quests screen with All / General / PlebNite,
each split into Daily and Weekly. Quests are the same for everyone, change every day and every
week (from Monday, `America/Chicago` time), and pay once, the moment they're done. Chat quests
count at most one message every 10 seconds. A "Quest complete!" pop-up shows the reward.
Players on other clients get chat lines instead of pop-ups. With PR #9 (`plebclient-only:
true`, the default there) only PlebClient players earn coins at all; existing balances stay.

Quest events (kills, deaths, matches, wins, worlds) come from the small **PlebMcBridge**
plugin on each backend. Wins on PlebNite need one line in the BattleRoyaleX config (see
Part 2). Categories, pools and rewards are all in `plugins/PlebioAPI/config.yml`.

**Shop.** Cosmetics are bought with PlebMc Coins in Right Shift > Shop. Items can be for sale
(`price`), exclusive (`price: -1`, given by staff with `/plebshop give`, like the Drop Zone
Crown Battle Pass reward) or need a permission. Since PR #8 the shop has **collections**
(top tabs, by default **PlebMc** and **Partners**); a partner item carries
`collection: partners` and `creator: 'Name'`. Trails were removed in PR #8 (the plugin skips
old trail items with a warning). `hidden-in-worlds` hides cosmetics in chosen worlds.

## Cosmetics: hats and HD cloaks

Everyone on PlebClient sees each other's hats and cloaks (per server). Players download
each cloak image or hat model from the proxy once and keep it until the file changes.

**Built-in hats** (`render:`): `tophat`, `crown`, `halo`, `partyhat`, `beanie`, `horns`, and
since PR #8 `cap`, `headphones`, `bunny`, `wizard`, `viking`, `cowboy`, `propeller`, `chef`.
`color:` tints a hat. The `crown` look uses the textured "goldcrown-king" Blockbench model
(Diamond Crown and Drop Zone Crown use it); the separate "Crown" shop item added in PR #4 was
removed in PR #6.

**HD cloaks.** Put a PNG in `plugins/PlebioAPI/cloaks/` and add one line to `items:`:

```yaml
  my_cloak: {name: 'My Cloak', category: cloak, texture: 'cloaks/mycloak.png', price: 5000, description: 'HD cloak'}
```

then `/plebshop reload`. The PNG uses the normal cape layout: 64x32 or any multiple up to
4096x2048 (512x256 gives 8x detail, 4 MB max). The art on your back is the 10x16 box at (1,1)
of the 64x32 layout (at 512x256, the 80x128 box at (8,8)). A cloak with only `color:` is a
plain colored cloak. `plebmc_cloak` and `storm_cloak` ship as examples.

**Animated cloaks (PR #8).** Stack the frames top to bottom in one PNG (16 frames of 256x128
= a 256x2048 image) and add `frametime` (ms per frame, default 100, 20 to 10000). Up to 64
frames, 8,388,608 pixels and 8 MB. `aurora_cloak`, `inferno_cloak`, `galaxy_cloak` and
`rainbow_wave_cloak` ship as examples. The Cloak Maker web tool builds these.

**3D model hats, added the same way as cloaks (PR #6).** Make a hat in Blockbench, paint its
texture, File > Save Model (`.bbmodel`), put it in `plugins/PlebioAPI/hats/` and add a line:

```yaml
  king_crown: {name: 'King Crown', category: hat, model: 'hats/king_crown.bbmodel', scale: 0.8, offset: [0, -1, 0], price: 10000, description: 'A 3D crown'}
```

then `/plebshop reload`. The texture is read from inside the `.bbmodel`. A Java block/item
model `.json` works too (add `texture: 'hats/myhat.png'`). Rotated cubes and groups are fine.

- `position: auto` (default) centers the model on the head and stands it on top.
  `position: model` keeps Blockbench's coordinates (0,0,0 is the bottom middle of the head,
  the top of the head is y 8).
- `scale:` resizes it. `1` is the Blockbench size, `0.8` smaller, `1.3` bigger. It scales
  from the top middle of the head, so it stays sitting on the head.
- `offset: [x, y, z]` moves it in skin pixels after placing (a head is 8 pixels wide).
  `y` is up/down (`-1` sinks it 1 pixel into the head), `x` left/right, `z` forward/back.
  Decimals work. If x or z goes the wrong way, flip the sign.

`king_crown.bbmodel` ships as an example (commented out in the default config). Players get
changes the next time the shop is sent to them; nothing needs rebuilding. The Hat Maker web
tool makes `.bbmodel` hats without Blockbench.

## Website login (plebmc.top)

Players log in to the client with their plebmc.top (NamelessMC) account and see their website
name and ranks in Right Shift > Account. NamelessMC's API can't check an email and password,
so players link once with a code:

1. Log in on plebmc.top as usual.
2. Account > Connections > Minecraft > **Link**. The site shows a code.
3. In the client: Right Shift > **Account**, type the code, **Log in**.

The proxy sends the code to NamelessMC with the API key, which links the Minecraft account.
On every join after that the client shows "Logged in as <name>" with the website groups.
**Log out** in the client unlinks the account on the website. Passwords never go through the
game, and the API key never leaves the proxy. Java and Eaglercraft players log in the same
way. Setup is in Part 2.

## Owner admin menu

Players logged in on the website whose website group is in `admin-menu.groups` (default
`['Owner']`) get an **Admin** door on the Right Shift home screen. From there they can see
everyone online (server, coins, client, website account, groups, cosmetics), give / take / set
PlebMc Coins, give or take cosmetics, send a player to another server, kick them, broadcast an
announcement and reload the config. The Give/Take/Set button last used stays highlighted.
Every action is checked again on the proxy and logged in the console, so a modified client
can't use it. Turn it off with `admin-menu.enabled: false`.

## Account security on a cracked server

On an offline-mode server anyone can join with anyone's name. Two open PRs close that hole:

- **PR #7 (nLogin + admin password).** PlebioAPI waits for nLogin's `/login` before it allows
  the admin menu, buying, equipping, linking or unlinking. The admin menu and `/plebshop` also
  need a second password typed each session with `/adminlogin <password>`, set only from the
  proxy console (`plebshop adminpass set <name> <password>`). 5 wrong tries lock the name for
  10 minutes. Without nLogin, a staff lock (`plebshop staff set <name> <password>`) makes
  protected names enter a password on Eaglercraft's own login screen (needs EaglerXBungee).
- **PR #9 (PlebMc accounts).** A self-hosted **PlebMc Panel** (`panel/`, Python + SQLite,
  Docker + Caddy, hosted at `client.plebmc.top`). Every name can have a password account.
  PlebClient opens on a Sign in / Create account screen and can't be used until you sign in;
  you then play under that account's name. Other clients sign in with
  `/account login <password>`. A name with an account that doesn't sign in within 90 seconds
  is kicked. Admins manage coins, cosmetics, kicks, locks and password resets on the website.
  Coins still live in PlebioAPI. Setup steps are in `panel/README.md` on that branch.

## Apollo server features (waypoints come from the server)

PlebClient speaks the **Apollo** server API (`lunar:apollo` protobuf and `apollo:json`), so a
server running the Apollo plugin can drive the client. It never claims to be another client:
the handshake only carries the Minecraft version. PlebNite runs Apollo 1.3.0 with
PlebNiteCore.

**Waypoints come from the server.** The client has no way to make its own waypoints. Every
waypoint, beam, marker and ping you see was sent by the server through Apollo's waypoint and
beam modules: Sneak + F asks the server for a ping, and the server sends the ping waypoint
(8 s), the red "Teammate DOWN" waypoint (15 s) and the knocked beam (15 s) to the squad. A
server without Apollo shows none of them. Waypoints draw as a sky beam in the world, a label
with distance on screen, and a pip on the Compass, through walls.

All 18 PlebNite Apollo features work: teammate markers, teammate glow (red when knocked),
teammate nametags (health or KNOCKED), ping waypoints, Teammate DOWN waypoints, knocked beams,
cooldown icons (gadgets, grapple, Quake Axe, Phase Bow), the match timer, the knocked /
low-health vignette, the real TNT fuse, and pop-ups for game mode announcements, first blood,
kill streaks, Champion, first win of the day, top 5 rewards, teammate revived and daily
reward claimed. Every other Apollo module is in the table in Part 3. Nothing gives an unfair
advantage: Apollo's X-ray staff mod is refused.

## Custom items: QualityArmory guns, totems and netherite

PlebNite runs QualityArmory on Purpur 1.16.5, which picks gun, ammo and medkit models with
**CustomModelData**. 1.8 doesn't support that, so 1.8 players used to see plain base items.
PlebClient (browser and Windows) now draws them:

- It reads the 1.14+ `overrides` with `custom_model_data` (and `damage` / `damaged`) from
  resource packs, converts each model to 1.8 once on resource reload, and swaps it in for the
  hand, GUI, dropped items and item frames. A model that fails falls back to the normal item.
- It accepts the server's newer-format pack (with no or a bad SHA-1) without breaking vanilla
  1.8 models.
- **Built-in pack.** The QualityArmory pack (sounds removed) is bundled at
  `sources/resources/plebclient/plebnite-pack.zip` and used only on `*.plebmc.top`, after the
  server's own pack, so guns show even when the server pack fails to download.
- **Totems and netherite.** Items Via substitutes ("1.16 Netherite Sword", or a Via
  original-id tag) get PlebClient's own textures, including worn netherite armor.
- QualityArmory hats show on players' heads like on 1.16.
- **Settings > Behavior > Custom item models** (on by default) turns it off.
- **Settings > Developer > Log item NBT** (off by default) records the NBT of items as they
  arrive (held, inventory, containers, nearby players), with Copy all and Save .txt.

Not checked in game yet: worn netherite armor on others, dropped items and item frames.

## Hex colors and gradients

1.16+ servers send hex colors and gradients, but ViaBackwards rounds them to the 16 old
colors on the server before a 1.8 player sees them, so no client can recover them alone. Two
parts fix it:

- **PlebClient** draws RGB color codes in their real color everywhere text is drawn: chat,
  tab list, scoreboard, nametags, titles, action bar, item names and lore, and holograms. It
  reads Spigot/Bungee `§x§R§R§G§G§B§B` and a compact 2-character code (so gradients fit 1.8's
  16-character scoreboard prefixes). Settings > Behavior > **RGB text colors** turns it off.
- **PlebColors** (server plugin, needs ProtocolLib) runs on each 1.16.5 and 1.21.x backend,
  sees each packet before ViaVersion translates it, and for PlebClient players only (they
  register the `plebmc:rgb` channel) moves hex colors into those codes. Everyone else gets the
  packets unchanged. It covers chat, titles, action bar, tab header/footer and names,
  scoreboard titles and team prefixes/suffixes, entity and hologram names, and item names and
  lore (not in creative). Not covered: signs and custom fonts.

**ProtocolLib versions:** 5.4.0 on the 1.16.5 server
(https://hangar.papermc.io/dmulloy2/ProtocolLib/versions/5.4.0), and the newest dev build on
1.21.11 (https://github.com/dmulloy2/ProtocolLib/releases/tag/dev-build), because released
versions don't support 1.21.11 yet. Both servers need Java 17 or newer.

## FancyHolograms armor stand fix

FancyHolograms uses text display entities (1.19.4+), which reach 1.8 as armor stands, and
sometimes the invisible flag gets lost, so players saw armor stand bodies. With
Settings > Behavior > **Clean holograms** (on by default):

- a blank hologram line no longer shows "Armor Stand";
- an armor stand that always shows its name, holds and wears nothing, and is a marker or has
  no gravity draws only its name.

Decorative armor stands with gear or without a visible name still draw normally. Hologram
text keeps its hex colors with PlebColors. Nothing is needed on the server.

## Web tools: Cloak Maker, Hat Maker, Client Studio

Three single-page tools in `tools/` (PR #8). Open the `index.html` in a browser or host them
on GitHub Pages:

- **Cloak Maker** (`tools/cloak-maker/`): turns a GIF, frames, an image or a built-in effect
  into an animated cloak PNG and its config line, with a 3D preview and limit checks.
- **Hat Maker** (`tools/hat-maker/`): a voxel hat editor that exports a `.bbmodel` PlebioAPI
  reads.
- **Client Studio** (`tools/client-studio/`): designs a crosshair, HUD and menu look and gives
  an "Import all" code for Settings > import.

## Known limits

- **WASM isn't possible on u35.** The u35 base in this repository only builds to JavaScript.
  The WASM-GC version of Eaglercraft needs u38 or newer (its own platform layer and a custom
  TeaVM). PR #8's u53 port adds it: `sh client-u53/setup.sh "" wasm`. Older browsers without
  WASM-GC support (like older Safari) still need the JavaScript build.
- No Social screen: there is no friends or party system on the servers to connect it to.
- The Forge port doesn't have custom item models yet, and its newest builds haven't been run
  in a real Forge client.
- Nothing has been tested on the live servers.

# Part 2: Server install guide

The network: a **BungeeCord proxy** (with EaglerXServer/EaglerXBungee for browser players and
nLogin), a **Lobby** and **PlebNite** (Purpur 1.16.5 with ViaVersion, ViaBackwards and
ViaRewind, QualityArmory, BattleRoyaleX, Apollo) and a 1.21.11 server.

| Plugin | Where | Needs |
|---|---|---|
| PlebioAPI (`PlebioAPI.jar`, was PlebMcShop) | BungeeCord proxy | Java 17+ |
| PlebMcBridge (`PlebMcBridge.jar`) | Lobby and PlebNite (Spigot/Paper/Purpur 1.13+) | |
| PlebColors (`PlebColors.jar`) | Each 1.16.5 and 1.21.x backend, **not** the proxy | ProtocolLib, Java 17+ |
| Apollo (`apollo-bukkit-*.jar`) | PlebNite (already installed) | |

Plugin jars are shipped in zips in the project files (`PlebioAPI.zip`, `PlebColors.zip`).
Build them yourself with `sh build.sh` in each `plugins/<name>/` folder (only a JDK needed).

### PlebioAPI (proxy)

1. If the old `PlebMcShop.jar` is in the proxy's `plugins/`, delete it.
2. Put `PlebioAPI.jar` in the proxy's `plugins/` and restart the proxy. On first start it
   moves `plugins/PlebMcShop/` to `plugins/PlebioAPI/` (coins, quests and config carry over)
   and copies the example cloaks and hat model into `cloaks/` and `hats/`.
3. Edit `plugins/PlebioAPI/config.yml` (items, collections, quests, rewards, admin groups),
   then run `/plebshop reload`. An existing config is never overwritten, so when upgrading,
   copy new sections (for example `collections:`, new `quests:`, new cloak lines) from the
   default config in the jar.
4. Give staff `plebshop.admin` for the `/plebshop` commands.

Commands (`plebshop.admin`): `/plebshop reload`, `quests`, `list`,
`coins <give|take|set> <player> <amount>`, `balance <player>`, `info <player>`,
`progress <player> <type> [amount]`, `give <player> <item>`, `remove <player> <item>`,
`equip <player> <hat|cloak> <item|none>`.

### Website login (NamelessMC)

1. On plebmc.top: StaffCP > Configuration > API. Turn the API on and copy the key.
2. Paste it into `website.api-key` in `plugins/PlebioAPI/config.yml` (keep
   `url: 'https://plebmc.top'`).
3. StaffCP > Integrations > Minecraft: keep Minecraft linking **on** and turn
   **UUID linking off** (Eaglercraft names usually aren't Mojang accounts, so the website
   can't look them up).
4. `/plebshop reload` on the proxy. Then try one link yourself.

Players don't need to download anything else. The admin menu also needs the website login,
since it goes by website group.

### PlebMcBridge (Lobby and PlebNite)

1. Put `PlebMcBridge.jar` in `plugins/` on the Lobby and PlebNite servers and restart them.
2. **Wins:** in `plugins/BattleRoyaleX/config.yml`, under `Executed-Commands > Player-Win`, add
   `- plebquest %player% win`, then `/brx reload`.
3. Other plugins can count quests with `/plebquest <player> <type> [amount]` (for example a
   parkour finish: `plebquest {player} parkour`, with a matching quest in the config).

### PlebColors (1.16.5 and 1.21.x backends)

1. Install ProtocolLib: **5.4.0** on 1.16.5, the newest **dev build** on 1.21.11.
2. Put `PlebColors.jar` in `plugins/` on each of those servers (not BungeeCord) and restart.
   There is no config.

### Apollo (PlebNite)

Apollo is the official Apollo Bukkit plugin; PlebNite already runs 1.3.0 with PlebNiteCore.
Nothing PlebClient-specific is needed. To test on a fresh server, see
[Testing with a real server](#testing-with-a-real-server-spigotpaper-188--apollo--eaglerxbungee).

### PR #7 and #9 extras (only after merging them)

- **#7:** with nLogin on the proxy nothing extra is needed for `/login`. Set each admin's
  second password from the proxy console: `plebshop adminpass set <name> <password>`, then
  they type `/adminlogin <password>` in game after `/login`.
- **#9:** host the panel (`panel/README.md`: Docker on a small VPS, DNS `client` record,
  `.env` with `PANEL_DOMAIN`, `PANEL_PUBLIC_URL`, `PANEL_SERVER_KEY`), then set `accounts:` in
  `plugins/PlebioAPI/config.yml` (`enabled: true`, `url: 'https://client.plebmc.top'`,
  `server-key:` the same key) and `/plebshop reload`.

# Part 3: Developer reference

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
| `Minecraft.refreshResources` | `PlebItems.onReload` | wrap 1.13+ packs, read custom item rules, add the `plebitems` pack |
| `ModelBakery.loadItemModels` | `PlebItems.itemModelLocations` | bake the converted custom models and new-item models |
| `ItemModelMesher.getItemModel(ItemStack)` | `PlebItems.itemModel` | swap in a custom model (all render paths: hand, GUI, dropped, frames) |
| `LayerArmorBase.renderLayer` | `PlebItems.armorTexture` | netherite armor texture on players |
| `LayerCustomHead.doRenderLayer` | `PlebItems.customHat` | pack hats (QualityArmory hats on quartz) drawn on heads like 1.16 |
| `GuiButton.drawButton`, `GuiOptionSlider` / `GuiSlider` / `GuiScreenOptionsSounds` | `PlebTheme.drawButton`, `sliderKnob` | PlebClient menu theme (dark glass buttons, accent Join button) |
| `GuiScreen.drawWorldBackground` / `drawBackground`, `GuiSlot` | `PlebTheme.background`, `slotBackground`, `slotOverlay` | themed backgrounds and lists |
| `GuiMainMenu.drawScreen` | `PlebTheme.mainMenuTitle`, `splashTranslate` | PLEBCLIENT wordmark instead of the logo (classic menu only) |
| `GuiMainMenu.initGui` / `drawScreen` / `mouseClicked` / `keyTyped` | `TitleMenu.init`, `draw`, `click`, `key` | the PlebClient title screen (sidebar, server card, info bar) |

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

## Cosmetics shop protocol

Cosmetics (hats and cloaks) are bought with the server's coins (on PlebMc: PlebMc Coins, earned from quests, a daily login reward and time online). The server owns the balance, ownership and what is equipped; the client only displays them and sends requests. Messages are UTF-8 JSON objects on the plugin channel `plebmc:shop`. The client sends `REGISTER plebmc:shop` when it joins (Bukkit needs this before it will deliver messages to the client). Open it with Right Shift, then **Shop** or **Quests**. "Try on" works everywhere and is local only.

**Client to server**

| Message | When |
| --- | --- |
| `{"type":"open"}` | On join and when the shop opens: send a `state` back. |
| `{"type":"buy","id":"crown"}` | Buy an item. Check the price and balance, take the coins, reply with `result` and a fresh `state`. |
| `{"type":"equip","category":"hat","id":"crown"}` | Equip an owned item (`"id":null` takes the slot off). Reply with `state`, and send the change to nearby players as `players`. |
| `{"type":"texture","id":"5707c10a4d93e2b1"}` | Download a cloak texture the catalog names. |

**Server to client**

| Message | Meaning |
| --- | --- |
| `{"type":"state","coins":1250,"owned":["tophat"],"equipped":{"hat":"tophat","cloak":"red_cloak"},"catalog":[...]}` | Balance, owned ids, equipped slots. Every field is optional; a missing field keeps its last value. `catalog` replaces the built-in list. |
| `{"type":"players","players":{"<uuid>":{"hat":"crown","cloak":"red_cloak"},"<uuid2>":null}}` | What other players wear (so everyone sees each other's cosmetics). `null` or `{}` removes a player. `"clear":true` drops everyone first (send on world change). |
| `{"type":"result","ok":false,"message":"Not enough coins","coins":100}` | Answer to buy/equip, shown in the shop. `coins` is optional. |
| `{"type":"texture","id":"5707c10a4d93e2b1","part":0,"parts":3,"data":"iVBORw0..."}` | One part of a cloak PNG, base64 (parts are joined in order, then decoded). The id is a content hash, so the client keeps the image across servers. |
| `{"type":"notify","title":"§aQuest complete!","message":"Get 3 kills: +75 PlebMc Coins","coins":425}` | A popup (top right, like Apollo notifications). `coins` optional. |

`state` can also carry the quests: `"currency":"PlebMc Coins"`, `"dailyResetIn":18720` and `"weeklyResetIn":...` (seconds), `"loginStreak":4`, `"questCategories":[{"id":"general","name":"General","icon":"general","color":"#3FD8E6"},{"id":"plebnite","name":"PlebNite","icon":"plebnite","color":"#FF9A3C"}]` (one entry each on the Quests screen, after "All"; `icon` and `color` are optional), and `"quests":[{"id":"kills_3","category":"plebnite","cycle":"2026-11-24","name":"Get 3 kills","description":"...","period":"daily","progress":1,"goal":3,"reward":75,"done":false}]` (`period` is `daily`, `weekly` or `login`; `cycle` names the day or week the quest belongs to, so the client can tag it NEW again when it comes back; the login row also has `"streak"` and `"streakLength"`; a quest without `category` shows in every category). Rewards are paid by the server the moment a quest is done, once; the client shows done quests as claimed.

**Admin menu.** The `account` message carries `"admin":true` for website groups listed in the proxy's `admin-menu.groups` (default Owner) and only once the player has logged in this session (nLogin /login, or the staff lock password without nLogin; on a cracked server the name alone proves nothing). Account-changing messages (buy, equip, link, logout, admin) are refused until then; the home screen then shows an Admin door. `{"type":"admin"}` asks for the admin data, answered with `{"type":"admin","allowed":true,"players":[{"uuid","name","server","coins","client","account","groups","owned","equipped"}],"servers":[...],"items":[...],"registered":1287}`; actions are `{"type":"admin","action":"coins|item|send|kick|broadcast|reload","player":"<uuid>",...}` and are checked again on the proxy.

**Website login (plebmc.top).** `{"type":"account"}` asks for the player's website login; `{"type":"link","code":"ABC123"}` links the Minecraft account with a code from the website's Connections page. The server answers `{"type":"account","enabled":true,"linked":true,"username":"Master","displayName":"Master","groups":["Owner","Member"],"staff":true,"site":"https://plebmc.top","linkUrl":"https://plebmc.top/index.php?route=/user/connections","message":"Logged in as Master!","ok":true}` (`message`/`ok` only after a link attempt or an error). It also sends this on join once it has checked the website. `{"type":"logout"}` logs out: the server unlinks the Minecraft account on the website (`users/integration_id:minecraft:<uuid>/integrations/unlink`) and answers with `"linked":false`.

**PlebMc account sign-in (the panel).** On join the client sends `{"type":"signin"}`; the server answers `{"type":"signin","enabled":true,"panel":"https://client.plebmc.top","known":true,"exists":true,"signedIn":false,"locked":false,"name":"Owner","role":"player","kickIn":88}`. The client signs in with `{"type":"signin","password":"...","remember":true}`, with a saved token `{"type":"signin","token":"..."}`, or makes an account with `{"type":"signin","register":true,"password":"...","remember":true}`. Answers are the same status plus `ok`, `message`, and `token` (keep it, only sent with `remember`) or `forget:true` (drop the saved token). `{"type":"signout","token":"..."}` signs out and revokes the token. While accounts are on, `buy`, `equip`, `link`, `logout` and `admin` are refused until `signedIn`. The proxy talks to the panel on `/api/server/*` with the server key (see `panel/app.py`).

**Start screen (client sign-in).** The client can't be used until it is signed in: `LoginGateScreen` posts to the panel's `/api/client/login` or `/api/client/register` (`{"name","password"}`, plain `text/plain` POST, CORS open, no cookies) and keeps the returned token in local storage (`account.name`, `account.token`). On start it re-checks it with `/api/client/check`; `/api/client/logout` revokes it. The username is forced to the account's name, and the same token is sent as the in-game `signin` token on join. Coins, quests and login rewards are only for players on the PlebMc client (`plebclient-only` in PlebioAPI).

Catalog items that are never sold (given by staff or events) carry `"forSale":false` (and, for older clients, a price nobody can reach); the shop shows them as Exclusive.

Catalog item: `{"id":"gold_tophat","name":"Gold Top Hat","category":"hat","render":"tophat","price":900,"color":"#FFD700","description":"..."}`. `category` is `hat` or `cloak` (trails were removed; clients drop them). `collection` puts the item under a shop tab (default `plebmc`) and `creator` shows "by <creator>" in the details. `state` can carry `"collections":[{"id":"plebmc","name":"PlebMc"},{"id":"partners","name":"Partners","icon":"partner","color":"#FF7AC6"}]` (the default when it doesn't); items in a collection that isn't listed show under the first one. A cloak has `"render":"cloak"` and either a `"texture"` id (plus `"textureWidth"`) for an image the client downloads with `texture`, or just a `color` for a plain cloak. Cloak images use the vanilla cape layout (64x32) at any multiple up to 4096x2048; the model's UVs are fractions of the image, so a 512x256 cloak draws at 8x detail. An animated cloak stacks its frames top to bottom (the image is `textureWidth` wide and a multiple of `textureWidth/2` tall); the catalog item then has `"frames":16,"frameTime":90` (ms per frame, 20 to 10000). The client slices the frames into separate textures (at most 64 frames and 8,388,608 pixels) and shows frame `(now / frameTime) % frames`. `tools/cloak-maker/index.html` builds these images. `render` is the look the client draws: hats `tophat`, `crown`, `halo`, `partyhat`, `beanie`, `horns`, `cap`, `headphones`, `bunny`, `wizard`, `viking`, `cowboy`, `propeller`, `chef`. `color` tints a hat (optional). A 3D model hat has `"render":"model"`, a `"model"` id and a `"texture"` id (plus `"textureWidth"`/`"textureHeight"`); the client downloads both with `texture` (the model as `{"v":1,"quads":[x,y,z,u,v ×4, ...]}` in head pixels, y up, top of the head at y 8) and draws it on the head. Items with a `render` this client doesn't know are hidden, so newer items don't break older clients. Without a catalog the client shows the built-in one (ids equal to the render names, prices 250 to 2,500).

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

## Hex colors and holograms: implementation

**RGB text.** The font renderer (`FontRenderer` and `EaglerFontRenderer`) draws
RGB color codes in their real color everywhere text is drawn: chat, tab list,
scoreboard, nametags, titles, action bar, item names and lore, signs and
holograms. Two forms are read (`net.plebmc.client.text.RgbText`): Spigot/Bungee
`§x§R§R§G§G§B§B`, and a compact `§` + one char `U+E000`-`U+EFFF` (12-bit color).
Wrapped lines keep their RGB color, and stripping formatting removes RGB codes.
Settings > Behavior > **RGB text colors** (on) turns it off, which draws the
nearest of the 16 old colors instead.

ViaBackwards rounds hex colors to the 16 old colors on the server, so the client
alone never sees them. **PlebColors** (`plugins/PlebColors`, needs ProtocolLib) on
each newer backend rewrites hex colors into compact RGB codes, only for players
whose client registered `plebmc:rgb` (PlebClient does on every join).

**Holograms.** Text display holograms (FancyHolograms, 1.19.4+) reach 1.8 as armor
stands with a visible name. Settings > Behavior > **Clean holograms** (on):
- a blank hologram line (empty name) no longer shows "Armor Stand";
- an armor stand that always shows its name, holds and wears nothing, and is a
  marker or has no gravity draws only its name, even if the invisible flag got lost.
  Decorative armor stands with gear or without a visible name still draw.

Hooks: `FontRenderer.renderStringAtPos` / `getFormatFromString`,
`EaglerFontRenderer`, `EnumChatFormatting.getTextWithoutFormattingCodes`,
`ArmorStandRenderer.canRenderName` / `renderModel`. The Forge port does the same
through mixins.

## Item NBT log

The client is called **PlebClient** in its own menus (the server stays "PlebMc").

PlebNite runs QualityArmory on a 1.16.5 server; Via translates its items down to
1.8. Before any model swapping, **Settings > Developer > Log item NBT** (off by
default, saved in `plebmc.prefs` as `itemNbtLog`) records what actually arrives:

- your held item, hotbar, inventory, armor, the item on the cursor, the open
  container's slots (shops, chests), and the held item and armor of players within
  32 blocks;
- a new entry only when an item changes (a stack that only changed size is skipped);
- the registry name, numeric id, damage, count, display name, lore and the whole NBT;
- hints: `CustomModelData` at any depth, ViaBackwards/ViaRewind backup tags,
  `PublicBukkitValues`, QualityArmory-looking keys and original item ids.

**Open item NBT log** (same section) shows the entries with **Copy all** and
**Save .txt**; every entry is also printed to the browser console as
`[item nbt] ...`. The log keeps the last 400 entries in memory only. With the
switch off, the check returns before doing anything. Code: `items/ItemNbtLog`
(pure Java, `ItemNbtLogTest`), `hud/ItemNbtWatch` (called from `PlebClient.renderHud`,
at most 10 scans a second), `hud/ItemNbtScreen`.

## Custom item models (QualityArmory and other CustomModelData packs)

Setting: **Settings > Behavior > Custom item models** (on by default). Code: `items/` (pure Java, tested) and `itemrender/` (game side).

- **Rules** come from resource packs in the 1.14+ format: `assets/minecraft/models/item/<item>.json` with `overrides` and a `custom_model_data` predicate. `damage` / `damaged` predicates also work. The last matching override wins, like vanilla.
- **Matching:** base item + CustomModelData first. If Via swapped the base item, a CustomModelData value used by exactly one model still matches. Items with no CustomModelData can match by display name against a model's file name.
- **Conversion:** every model an override points at is flattened (parents), checked and rewritten for 1.8 once on resource reload (`ModelConverter`): rotations snapped to 1.8's allowed angles, element bounds clamped, missing textures dropped, 1.9 display transforms converted to 1.8's rotation order (`ItemTransforms`). A model that fails is skipped and the item keeps its normal look.
- **Textures** are served under the `plebitems` domain so they never replace vanilla textures.
- **Speed:** stacks with no NBT on items that have no rules take a fast path; others are cached (2048 entries, cleared on reload).
- **Newer packs:** server packs with `pack_format` 4+ are wrapped (`ModernPackFilter`) so their vanilla models/blockstates don't break 1.8 items. Server packs with no or a bad SHA-1 are still accepted (downloaded each time).
- **Totems and netherite:** items Via substitutes ("1.16 Netherite Sword", or a Via original-id tag) get PlebClient's own textures, including worn netherite armor.
- **Bundled PlebNite pack:** the QualityArmory pack (sounds removed) is at `sources/resources/plebclient/plebnite-pack.zip`. It is imported once and used only on `*.plebmc.top`, after the server's own pack.
- **Debug:** Settings > Developer > Item NBT log shows the held item's NBT, including CustomModelData.

## Cosmetic crown

The Crown hat is the "goldcrown-king" Blockbench model: `cosmetics/CrownModel.java` (boxes and UVs, generated from the .bbmodel) and `assets/plebitems/textures/cosmetics/crown.png`, drawn by `CosmeticLayer`. The other hats are still plain colored boxes.

## Title screen

The browser client builds on EaglercraftX u53; see `client-u53/README.md`.


With the PlebClient menus on, `hud/TitleMenu.java` replaces the vanilla title screen. The background is the PlebMc wallpaper (`assets/plebitems/textures/gui/wallpaper.jpg`, scaled to cover the screen with a slight mouse parallax) or the Minecraft panorama. On top of it there is a transparent sidebar (the mascot head from `assets/plebitems/textures/gui/mascot.png`, 11x11 `nav_*` icons from `Ui`, labels that slide in while you point at it, a translucent accent hover wash, a thin accent divider and a small accent indicator) and a faint version line. Arrow keys and Enter move through the sidebar, and 1-9 open an entry. `TitleActions` holds what each entry opens and differs per client (browser: Edit Profile, Language and Credits; Forge: Forge Mods and Quit).

- **Showcase**: your own skin in 3D (the browser uses the Eagler profile skin, Forge the session's skin), turning slowly and following the mouse, wearing the hat and cloak you last wore on PlebMc, with your name and a `store.plebmc.top` pill under it. Clicking the player or the pill opens https://store.plebmc.top.
- **Coins** (top right): your Pleb Coins from the last time you were on PlebMc (`--` until then). Clicking opens the cosmetics shop. The last balance and outfit are kept in the client's storage (`lastLook`, written by `Cosmetics.lookJson()` and read back with `Cosmetics.loadLook()`).
- **Mini mascot** (bottom right): the chibi mascot (`buddy.png`, a 5x4 sheet of 108px frames) bobs, hops when you point at him and says a short tip in a speech bubble, and explodes when clicked, then pops back.
- **Particles**: snow (default), embers or fireflies drift over the background. Each particle is computed from its index and the time, so nothing is stored.

**Settings > Title screen** (the sidebar's Customize entry) sets the sidebar (opens on hover, always open, icons only), the background (wallpaper or panorama), how much it is darkened, the mini mascot, the particles, the mascot head, a clock, the splash text and the accent color (12 colors). Turning the PlebClient menus off brings back the classic menu.

## Menu theme

`hud/PlebTheme.java` restyles every vanilla menu (buttons, sliders, backgrounds, lists, the title screen wordmark) in the PlebClient look. **Settings > PlebClient menus** turns it off (on by default; `ClientPrefs.modernMenus`). The Forge port does the same with mixins (`MixinGuiButton`, `MixinGuiScreen`, `MixinGuiSlot`, `MixinGuiMainMenu`, the slider mixins).

## Admin menu

`hud/AdminScreen.java`, opened from the Admin door on the home screen. The door only shows when the proxy's `account` message says `"admin":true` (website group in `admin-menu.groups`, default Owner). See the protocol above.

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

`CosmeticsTest`, `CustomItemsTest`, `FpsLarpTest`, `ItemNbtLogTest` and `RgbTextTest` run in the same script. The plugins have their own: `sh plugins/PlebMcShop/test/run.sh` (shop and quests on a fake proxy with the real client code) and `plugins/PlebColors/test/HexJsonTest.java`.

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

