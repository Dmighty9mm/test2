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

## Stats for the panel

With `stats.enabled: true` in `config.yml`, it also sends this server's game stats (from
PlaceholderAPI placeholders or vanilla statistics) to the PlebMc Panel's Stats and
Leaderboards pages: online players every few minutes and when they leave. `/plebstats
<player>` shows what is read for a player and sends it now. See `config.yml` for PlebNite
and PlebRPG examples. Tests: `sh build.sh && javac -cp build/classes:build/stubs -d build/test
test/net/plebmc/bridge/StatsTest.java && java -cp build/classes:build/stubs:build/test
net.plebmc.bridge.StatsTest`.

Build: `sh build.sh` (the Bukkit classes in `stubs/` are compile-only stand-ins).
