# PlebColors

Keeps hex colors and gradients (1.16+) for **PlebClient** players on 1.8.

ViaBackwards rounds every hex color to the nearest of the 16 old colors before a
1.8 player sees it, so no client can get the real color back on its own. This
plugin runs on the newer backend server, sees each outgoing packet before
ViaVersion translates it, and for PlebClient players moves hex colors into the
text as PlebClient RGB codes, which Via passes through untouched. Everyone else
(vanilla 1.8, newer clients) gets the packets unchanged.

## Install

On **each 1.16.5 and 1.21.x backend** (the servers that run ViaVersion /
ViaBackwards / ViaRewind), not on BungeeCord:

1. Install **ProtocolLib** (5.3+ on 1.16.5; on 1.21.9+ use the newest ProtocolLib
   dev build that supports your exact version).
2. Put `PlebColors.jar` in `plugins/` and restart. No config.

## What it covers

Chat and system messages, titles, subtitles and action bar, tab header/footer and
tab player names, scoreboard titles and team prefixes/suffixes (sidebar lines),
entity and hologram names (FancyHolograms text displays reach 1.8 as armor
stands; their text keeps its colors), and item names and lore (not for players in
creative mode, since creative would save the codes into the item).

Not covered: signs, boss bars (1.8 has none), and custom fonts (1.8 cannot draw
them). Holograms and the tab header sent before the client announced itself
(first second after joining) are sent again once it does.

## How it is detected

PlebClient registers the plugin channel `plebmc:rgb` when it joins a server.
Only players on that channel get the rewritten text.

## RGB codes

`§` followed by one character `U+E000`-`U+EFFF` holding a 12-bit color
(`0xE000 | r<<8 | g<<4 | b`). Two characters per color, so gradients still fit
1.8's 16-character scoreboard prefixes. PlebClient also draws the Spigot/Bungee
`§x§R§R§G§G§B§B` form.

## Build

`sh build.sh` (JDK 8+; downloads ProtocolLib, Gson and bungeecord-chat from Maven
Central for compiling only). Test: see `test/HexJsonTest.java`.
