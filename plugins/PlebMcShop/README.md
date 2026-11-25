# PlebioAPI (formerly PlebMcShop)

> Renamed from PlebMcShop. On its first start PlebioAPI moves `plugins/PlebMcShop/` to `plugins/PlebioAPI/`, so config, balances and quests carry over. Remove the old `PlebMcShop.jar` from `plugins/`.

The proxy plugin behind the PlebMc client's **Shop**, **Quests** and **Account** screens (Right Shift).
It runs on **BungeeCord**, so one balance, one set of quests and one wardrobe
work on every server behind the proxy.

- **PlebMc Coins**: the plugin's own coins, separate from every in-game economy (BRX coins etc.).
- **Earning**: a daily login reward with a streak, coins for time online, and daily and
  weekly quests in **categories**: by default **General** (counts on every server) and
  **PlebNite** (counts only on PlebNite). The client shows All / General / PlebNite.
- **Spending**: hats, trails and HD cloaks. Everyone on the PlebMc client sees each other's (per server).
- **plebmc.top login**: players log in to the client with their website (NamelessMC)
  account and see their website name and rank (Right Shift > Account), and log out.

Players on other clients still earn coins (they get chat messages instead of popups) and
can spend them once they use the PlebMc client.

Quest events (kills, wins, matches...) come from the small **PlebMcBridge** plugin on each
backend server (`plugins/PlebMcBridge`). The protocol is in `docs/PLEBMC.md`.

## Install

1. **Proxy:** put `PlebioAPI.jar` in the BungeeCord `plugins/` folder and restart the proxy.
2. **Backends:** put `PlebMcBridge.jar` in `plugins/` on the lobby and PlebNite servers
   (Spigot/Paper/Purpur 1.13+) and restart them.
3. **Wins (PlebNite):** in `plugins/BattleRoyaleX/config.yml`, under
   `Executed-Commands > Player-Win`, add `- plebquest %player% win`, then `/brx reload`.
4. **Website login:** on plebmc.top, StaffCP > Configuration > API: turn the API on and copy
   the key into `website.api-key` in the proxy's `plugins/PlebioAPI/config.yml`. Under
   StaffCP > Integrations > Minecraft, keep Minecraft linking on and turn "UUID linking" off
   (Eaglercraft names usually aren't Mojang accounts, so the website can't look them up).
5. Edit `plugins/PlebioAPI/config.yml` on the proxy if you like, then `/plebshop reload`.

## Quest categories

```yaml
quests:
  categories:
    general:
      name: 'General'        # shown in the client
      icon: general          # general, plebnite, quests, star, ...
      color: '#3FD8E6'       # optional
      servers: ['*']         # '*' = every server
      daily:   { ... }
      weekly:  { ... }
    plebnite:
      name: 'PlebNite'
      icon: plebnite
      color: '#FF9A3C'
      servers: [plebnite]    # Bungee server names that count for this category
      daily:   { ... }
      weekly:  { ... }
login-rewards:
  category: general          # where the daily login row shows
```

Each category picks `daily-count` daily and `weekly-count` weekly quests from its pools,
the same for everyone, and they change every day/week. An event counts for every
category covering the server it happened on: a minute on PlebNite counts for General
*and* PlebNite play quests, a kill in the lobby counts for nothing (PlebNite kill quests
only count on PlebNite). Adding a category is just another block here; the client picks
it up on its own. A quest pays once, the moment it's done. Progress for quests that are
no longer in the config (like the old Lobby ones) is dropped on the player's next update.

Quest types: `kill`, `death`, `match` (died or won), `win`, `play` (a minute online),
`join` (joining or switching servers), `chat` (a chat message, at most one every 10
seconds), `login`, or anything you send with `/plebquest <player> <type> [amount]` on a
backend.

**Upgrading from the Lobby/PlebNite version:** an existing `config.yml` is never
overwritten, so replace its `quests:` section with the one from the new default config
(and add `category: general` under `login-rewards`). Coins, items, PlebNite quest progress
and login streaks are kept. For cloaks, copy the three cloak lines from the default
config's `items:` into yours (the sample images are put in `cloaks/` on the next start).

## Earning

| What | Default |
| --- | --- |
| Daily login (every 20 h; streak resets after 48 h) | 50, 75, 100, 125, 150, 200, 300, then repeats |
| Time online | 10 coins every 15 minutes |
| General: 3 daily from 4, 2 weekly from 3 | 40 to 500 each |
| PlebNite: 3 daily from 6, 2 weekly from 4 | 60 to 750 each |

## plebmc.top login

NamelessMC's API can't check an email and password, so players link once instead:

1. Log in on plebmc.top (email and password, as usual).
2. Account > Connections > Minecraft > **Link**. The site shows a code (`/verify ABC123`).
3. In the client: Right Shift > **Account**, type the code, **Log in**.

The proxy sends the code to NamelessMC (`integration/verify`) with the API key, which links
this Minecraft account to the website account. On every join after that the proxy looks
the account up (`users/integration_id:minecraft:<uuid>`) and the client shows
"Logged in as <name>" with their website groups. **Log out** in the client (or unlinking
on the website) removes the link; they can link again with a new code.
Passwords never go through the game, and the API key never leaves the proxy.

## Commands on the proxy (`plebshop.admin`)

| Command | What |
| --- | --- |
| `/plebshop reload` | Reload config.yml and resend the shop to everyone |
| `/plebshop quests` | Today's and this week's quests in every category |
| `/plebshop list` | Items, prices and looks |
| `/plebshop coins <give\|take\|set> <player> <amount>` | Change someone's PlebMc Coins |
| `/plebshop balance <player>` / `info <player>` | Someone's coins (and what they own) |
| `/plebshop progress <player> <type> [amount]` | Count quest progress on the server they're on |
| `/plebshop give <player> <item>` / `remove` | Give or take an item for free |
| `/plebshop equip <player> <hat\|trail\|cloak> <item\|none>` | Put something on a player |

## Staff lock (cracked servers): do this first

On an offline-mode (cracked) server the proxy can't check who someone is. Your UUID is made
from your name, so anyone who types an Owner's name gets that Owner's UUID, which means
their LuckPerms rank, their BungeeCord permissions, their plebmc.top link and the admin menu.
The staff lock closes that: protected names need a password at login.

1. In `plugins/EaglercraftXBungee/authservice.yml` on the proxy set
   ```yaml
   enable_authentication_system: true
   use_onboard_eaglerx_system: false
   ```
   (PlebioAPI answers the auth hooks: everyone else still joins with no password, only
   protected names get the password screen. Don't run another Eaglercraft auth plugin with it.)
2. Put the new `PlebioAPI.jar` in `plugins/` and restart the proxy.
3. In the **proxy console** (not in game), protect every staff name:
   ```
   plebshop staff set <YourName> <password>
   plebshop staff list
   ```
   `plebshop staff remove <name>` unprotects one, `plebshop staff add <name>` locks one
   until it has a password. Passwords are at least 8 characters and saved salted and hashed
   in `plugins/PlebioAPI/staff-lock.yml`.
4. Join as that name: the client shows a password screen. Type the password; Eaglercraft
   can remember it for that server.

What it does:

- A protected name only gets in after the right password (checked with Eaglercraft's
  challenge hash, so the password itself never crosses the network). Names compare ignoring
  case, so `owner` can't get around `Owner`. 5 wrong passwords from one address lock it out
  for 10 minutes. Without the password the proxy refuses the login before any server sees it.
- Java players (Forge, vanilla) can't use a protected name unless the proxy is in online mode.
- The admin menu and in-game `/plebshop` also need a name that gave its password at this
  login. If a website Owner/staff name or a `plebshop.admin` player shows up unprotected,
  that name is locked on the spot (logged in the console) until you set its password.
- Turn it off with `staff-lock.enabled: false` (not recommended on a cracked server).

## Admin menu in the client

Players logged in on the website (see above) whose website group is in `admin-menu.groups`
(default `['Owner']`) get an **Admin** door on the client's home screen. From there they can
see everyone online, give/take/set PlebMc Coins, give or take cosmetics, send a player to
another server, kick them, broadcast an announcement and reload the config. Every action is
checked again on the proxy, so a modified client can't use it. Turn it off with
`admin-menu.enabled: false`.

## Items

Each item in `items:` has a `category` (`hat`, `trail` or `cloak`), a `render` (the look the
client draws; leave it out for cloaks), a `price` (`-1` = not for sale, give it by command), and optionally a
`color` (`'#RRGGBB'`, tints a hat or colors a plain cloak), a `description` and a `permission` needed to buy it.

### HD cloaks

Put a PNG in the plugin folder (for example `plugins/PlebioAPI/cloaks/mycloak.png`) and add:

```yaml
  my_cloak: {name: 'My Cloak', category: cloak, texture: 'cloaks/mycloak.png', price: 5000, description: 'HD cloak'}
```

then `/plebshop reload`. The image uses the normal Minecraft cape layout: 64x32, or any
multiple of it for more detail (128x64, 256x128, **512x256**, 1024x512, up to 4096x2048,
4 MB max). The art people see on your back is the 10x16 box at (1,1) of the 64x32 layout
(at 512x256: the 80x128 box starting at (8,8)); the inside is the box at (12,1), the edges
are the 1-pixel strips around them. Any cape template works. Players' clients download the
image from the proxy once and keep it until the file changes. A file with the wrong size
or outside the plugin folder is refused with a warning in the console, and the cloak shows
as a plain one in its `color`. Two examples ship with the plugin (`cloaks/plebmc.png`,
`cloaks/storm.png`, copied into the folder on first start) and are used by
`plebmc_cloak` and `storm_cloak` in the default config.

### 3D model hats (same as cloaks)

Make a hat in Blockbench, paint its texture, **File > Save Model** (`.bbmodel`), put it in
`plugins/PlebioAPI/hats/` and add one line, just like a cloak:

```yaml
  king_crown: {name: 'King Crown', category: hat, model: 'hats/king_crown.bbmodel', price: 10000, description: 'A 3D crown'}
```

then `/plebshop reload`. The texture is read from inside the `.bbmodel`. A Java block/item
model `.json` works too (add `texture: 'hats/myhat.png'`). Rotated cubes and groups are fine.
By default the model is centered on the head and stood on top of it; to place it yourself:
`position: model` (keep Blockbench's coordinates: 0,0,0 is the bottom middle of the head, the
top of the head is y 8), `scale: 1.2`, `offset: [0, -1, 0]` (pixels). Players download the
model once and keep it until the file changes. `king_crown.bbmodel` ships as an example (copied
to `hats/` on first start); the line above is in the default config, commented out.

Hats: `tophat`, `crown`, `halo`, `partyhat`, `beanie`, `horns` (or `model:` above).
Trails: `flame`, `hearts`, `notes`, `rainbow`, `cloud`, `portal`, `emerald`, `snow`.

Item and quest ids are what `players.yml` saves, so don't rename ones players already have.
`hidden-in-worlds: [arena1, 'plebnite:arena2']` hides cosmetics in those worlds.

## Build and test

Only a JDK is needed (the BungeeCord API is downloaded for compiling):

```
sh build.sh       # -> PlebioAPI.jar
sh test/run.sh    # the shop on a fake proxy with the real client code, and quest logic with a fixed clock
```
