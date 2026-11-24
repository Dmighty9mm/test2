# PlebMcShop

Server plugin behind the PlebMc client's **Cosmetics** menu (Right Shift > Cosmetics).
Players buy hats and trails with their coins, the server remembers what they own and
wear, and everyone on the PlebMc client sees each other's cosmetics. Players on other
clients see nothing and are not affected.

It speaks the `plebmc:shop` protocol in `docs/PLEBMC.md` ("Cosmetics shop protocol").

## Install

1. Put `PlebMcShop.jar` in `plugins/` and restart (Spigot/Paper/Purpur 1.13 or newer).
2. Edit `plugins/PlebMcShop/config.yml` if you like, then `/plebshop reload`.
3. Join with the PlebMc client and check `/plebshop balance <you>` shows your coins.

## Coins

`coins.mode` in config.yml:

| Mode | Balance | Taking coins |
| --- | --- | --- |
| `command` (default) | PlaceholderAPI `balance-placeholder` (`%battleroyalex_coins%`) | console `take-command` (`brx coins remove %player% %amount%`) |
| `vault` | Vault economy | Vault withdraw |
| `internal` | this plugin's own `players.yml` | `/plebshop coins give|take|set` |

Before a purchase the plugin checks the balance; after the take command it checks the
balance went down. If it can't read the balance, or the command didn't take the coins,
the player doesn't get the item and the console says why. A finished purchase is saved
to `players.yml` straight away.

## Commands (`plebshop.admin`, ops by default)

| Command | What |
| --- | --- |
| `/plebshop reload` | Reload config.yml and resend the shop to everyone |
| `/plebshop list` | Items, prices and looks |
| `/plebshop give <player> <item>` / `remove` | Give or take an item for free (Battle Pass rewards, events) |
| `/plebshop equip <player> <hat\|trail> <item\|none>` | Put something on a player |
| `/plebshop info <player>` | What they own and wear |
| `/plebshop balance <player>` | The coins the shop reads (to check the placeholder) |
| `/plebshop coins <give\|take\|set> <player> <amount>` | Internal mode only |

## Items

Each item in `items:` has a `category` (`hat` or `trail`), a `render` (the look the
client draws), a `price` (`-1` = not for sale, give it by command), and optionally a
`color` (`'#RRGGBB'`, tints a hat), a `description` and a `permission` needed to buy it.

Hats: `tophat`, `crown`, `halo`, `partyhat`, `beanie`, `horns`.
Trails: `flame`, `hearts`, `notes`, `rainbow`, `cloud`, `portal`, `emerald`, `snow`.

So a new hat is a new color of an existing look, e.g.
`ruby_crown: {name: 'Ruby Crown', category: hat, render: crown, price: 20000, color: '#E0115F'}`.
Item ids are what `players.yml` saves, so don't rename ids that players already own.

`hidden-in-worlds: [world_a, world_b]` hides everyone's cosmetics while they are in those
worlds (for example the match worlds).

## Build and test

Only a JDK is needed (the Bukkit classes in `stubs/` are compile-only stand-ins):

```
sh build.sh       # -> PlebMcShop.jar
sh test/run.sh    # runs the plugin against a fake server and the real client shop code
```
