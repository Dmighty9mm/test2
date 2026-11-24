# PlebMcShop

Server plugin behind the PlebMc client's **Shop & Quests** menu (Right Shift).

- **PlebMc Coins**: the plugin's own coins, separate from every other economy on the server.
- **Earning**: a daily login reward with a streak, coins for time online, and daily and
  weekly quests (kills, matches, wins, playtime, logins).
- **Spending**: hats and trails. Everyone on the PlebMc client sees each other's.

Players on other clients still earn coins (they get chat messages instead of popups) and
can spend them once they use the PlebMc client.

It speaks the `plebmc:shop` protocol in `docs/PLEBMC.md` ("Cosmetics shop protocol").

## Install

1. Put `PlebMcShop.jar` in `plugins/` and restart (Spigot/Paper/Purpur 1.13 or newer).
2. For Battle Royale X wins to count, add this line under `Executed-Commands > Player-Win`
   in `plugins/BattleRoyaleX/config.yml`, then `/brx reload`:
   `- plebshop progress %player% win`
3. Edit `plugins/PlebMcShop/config.yml` if you like, then `/plebshop reload`.

## Earning

| What | Default |
| --- | --- |
| Daily login (every 20 h; streak resets after 48 h) | 50, 75, 100, 125, 150, 200, 300, then repeats |
| Time online | 10 coins every 15 minutes |
| Daily quests (3 a day, same for everyone, from 7) | 60 to 150 each |
| Weekly quests (2 a week, from 5) | 400 to 750 each |

Quest types the plugin counts itself: `kill`, `death`, `match` (a match ended for you:
you died or won), `play` (a minute online), `login`. Any other type, like `win`, is counted
with `/plebshop progress <player> <type> [amount]`, so other plugins' command hooks can
feed quests. Quest days start at `quests.reset-hour` in `timezone` (America/Chicago).

## Commands (`plebshop.admin`, ops by default)

| Command | What |
| --- | --- |
| `/plebshop reload` | Reload config.yml and resend the shop to everyone |
| `/plebshop quests` | Today's and this week's quests |
| `/plebshop list` | Items, prices and looks |
| `/plebshop coins <give\|take\|set> <player> <amount>` | Change someone's PlebMc Coins |
| `/plebshop balance <player>` | Someone's PlebMc Coins |
| `/plebshop progress <player> <type> [amount]` | Count quest progress (e.g. `win`) |
| `/plebshop give <player> <item>` / `remove` | Give or take an item for free |
| `/plebshop equip <player> <hat\|trail> <item\|none>` | Put something on a player |
| `/plebshop info <player>` | Coins, streak, what they own and wear |

## Items

Each item in `items:` has a `category` (`hat` or `trail`), a `render` (the look the
client draws), a `price` (`-1` = not for sale, give it by command), and optionally a
`color` (`'#RRGGBB'`, tints a hat), a `description` and a `permission` needed to buy it.

Hats: `tophat`, `crown`, `halo`, `partyhat`, `beanie`, `horns`.
Trails: `flame`, `hearts`, `notes`, `rainbow`, `cloud`, `portal`, `emerald`, `snow`.

A new hat is a new color of an existing look, e.g.
`ruby_crown: {name: 'Ruby Crown', category: hat, render: crown, price: 12000, color: '#E0115F'}`.
Item and quest ids are what `players.yml` saves, so don't rename ones players already have.

`hidden-in-worlds: [world_a]` hides everyone's cosmetics while they're in those worlds.

## Build and test

Only a JDK is needed (the Bukkit classes in `stubs/` are compile-only stand-ins):

```
sh build.sh       # -> PlebMcShop.jar
sh test/run.sh    # the plugin on a fake server + the real client shop code, and quest logic with a fixed clock
```
