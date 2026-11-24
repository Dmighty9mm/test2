# PlebMcBridge

Tiny backend plugin (Spigot/Paper/Purpur 1.13+) that tells **PlebMcShop** on the proxy
what players do, for quests. Put it on the lobby and PlebNite servers.

It sends, over the standard BungeeCord channel (sub-channel `PlebMcBridge`):

- `kill` for the killer, `death` and `match` for the victim of a player death
- the player's world on join and world change (for `hidden-in-worlds`)
- anything from `/plebquest <player> <type> [amount]` (`plebshop.admin`, console by
  default). `win` also counts a `match`.

Hook other plugins up with their reward commands, e.g. BRX `Player-Win`:
`- plebquest %player% win`, or a parkour finish: `plebquest {player} parkour`.

Which quests an event counts for depends on the server it came from; see
`quests.categories` in PlebMcShop's config.

Build: `sh build.sh` (the Bukkit classes in `stubs/` are compile-only stand-ins).
