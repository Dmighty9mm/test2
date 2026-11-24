# PlebMcShop

The proxy plugin behind the PlebMc client's **Shop & Quests** menu (Right Shift).
It runs on **BungeeCord**, so one balance, one set of quests and one wardrobe
work on every server behind the proxy.

- **PlebMc Coins**: the plugin's own coins, separate from every in-game economy (BRX coins etc.).
- **Earning**: a daily login reward with a streak, coins for time online, and daily and
  weekly quests in **categories**: one tab per category in the client (by default
  *PlebMc Lobby* and *PlebNite*).
- **Spending**: hats and trails. Everyone on the PlebMc client sees each other's (per server).
- **plebmc.top login**: players log in to the client with their website (NamelessMC)
  account and see their website name and rank (Right Shift > Log in).

Players on other clients still earn coins (they get chat messages instead of popups) and
can spend them once they use the PlebMc client.

Quest events (kills, wins, parkour...) come from the small **PlebMcBridge** plugin on each
backend server (`plugins/PlebMcBridge`). The protocol is in `docs/PLEBMC.md`.

## Install

1. **Proxy:** put `PlebMcShop.jar` in the BungeeCord `plugins/` folder and restart the proxy.
2. **Backends:** put `PlebMcBridge.jar` in `plugins/` on the lobby and PlebNite servers
   (Spigot/Paper/Purpur 1.13+) and restart them.
3. **Wins (PlebNite):** in `plugins/BattleRoyaleX/config.yml`, under
   `Executed-Commands > Player-Win`, add `- plebquest %player% win`, then `/brx reload`.
4. **Parkour (lobby):** add `plebquest {player} parkour` to your parkour plugin's finish
   reward commands (ajParkour: `rewards` commands; use its player placeholder).
5. **Website login:** on plebmc.top, StaffCP > Configuration > API: turn the API on and copy
   the key into `website.api-key` in the proxy's `plugins/PlebMcShop/config.yml`. Under
   StaffCP > Integrations > Minecraft, keep Minecraft linking on and turn "UUID linking" off
   (Eaglercraft names usually aren't Mojang accounts, so the website can't look them up).
6. Edit `plugins/PlebMcShop/config.yml` on the proxy if you like, then `/plebshop reload`.

## Quest categories

```yaml
quests:
  categories:
    lobby:
      name: 'PlebMc Lobby'   # the title in the client
      tab: 'Lobby'           # the tab button
      servers: [lobby]       # Bungee server names that count for this category
      daily:   { ... }
      weekly:  { ... }
```

Each category picks `daily-count` daily and `weekly-count` weekly quests from its pools,
the same for everyone. Progress only counts on that category's servers: a kill in the
lobby doesn't count for a PlebNite kill quest. Servers in no category (like survival)
count nothing. The daily login shows in every tab, and `login` quests count anywhere.

Quest types: `kill`, `death`, `match` (died or won), `win`, `play` (a minute online on
that server), `join` (connecting to that server), `login`, `parkour`, or anything you
send with `/plebquest <player> <type> [amount]` on a backend.

## Earning

| What | Default |
| --- | --- |
| Daily login (every 20 h; streak resets after 48 h) | 50, 75, 100, 125, 150, 200, 300, then repeats |
| Time online | 10 coins every 15 minutes |
| Lobby: 3 daily from 4, 2 weekly from 3 | 50 to 400 each |
| PlebNite: 3 daily from 6, 2 weekly from 4 | 60 to 750 each |

## plebmc.top login

NamelessMC's API can't check an email and password, so players link once instead:

1. Log in on plebmc.top (email and password, as usual).
2. Account > Connections > Minecraft > **Link**. The site shows a code (`/verify ABC123`).
3. In the client: Right Shift > **Log in**, type the code, **Log in**.

The proxy sends the code to NamelessMC (`integration/verify`) with the API key, which links
this Minecraft account to the website account. On every join after that the proxy looks
the account up (`users/integration_id:minecraft:<uuid>`) and the client shows
"Logged in as <name>" with their website groups. Unlinking on the website logs them out.
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
| `/plebshop equip <player> <hat\|trail> <item\|none>` | Put something on a player |

## Items

Each item in `items:` has a `category` (`hat` or `trail`), a `render` (the look the
client draws), a `price` (`-1` = not for sale, give it by command), and optionally a
`color` (`'#RRGGBB'`, tints a hat), a `description` and a `permission` needed to buy it.

Hats: `tophat`, `crown`, `halo`, `partyhat`, `beanie`, `horns`.
Trails: `flame`, `hearts`, `notes`, `rainbow`, `cloud`, `portal`, `emerald`, `snow`.

Item and quest ids are what `players.yml` saves, so don't rename ones players already have.
`hidden-in-worlds: [arena1, 'plebnite:arena2']` hides cosmetics in those worlds.

## Build and test

Only a JDK is needed (the BungeeCord API is downloaded for compiling):

```
sh build.sh       # -> PlebMcShop.jar
sh test/run.sh    # the shop on a fake proxy with the real client code, and quest logic with a fixed clock
```
