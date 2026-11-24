# PlebMc for Minecraft Java Edition (Forge 1.8.9)

The PlebMc client as a Forge mod, so Java Edition players get the same thing as
the Eaglercraft client: the **Right Shift** home screen (Mods, Shop, Quests, HUD
editor, Account, Settings), the PlebNite mods (Storm Tracker, Squad, Supplies,
Low Health Warning, Compass, Squad Ping), the HUD mods, Apollo server features
(squad markers, glow, nametags, ping waypoints, cooldowns, match timer, vignette,
notifications and the rest), PlebMc Coins cosmetics with HD cloaks, quests, the
plebmc.top login, Join PlebMc / Discord buttons and the secret Fps Larp mod.

It talks to the same server plugins as the Eaglercraft client (Apollo on the
backends, `plebmc:shop` from **PlebMcShop** on BungeeCord), so nothing changes on
the server.

## Install

1. Install **Forge 1.8.9** (`11.15.1.2318` or newer 1.8.9 build).
2. Put `PlebMc-1.0.0.jar` in `.minecraft/mods/`.
3. Start the Forge 1.8.9 profile. The title screen has **Join PlebMc** and
   **Discord**, and PlebMc is pinned to the top of the server list.

Settings are saved in `.minecraft/config/plebmc/`. The zoom key is **C** (rebind it
under Controls > PlebMc). OptiFine works alongside; glow outlines and sky beams turn
off while OptiFine shaders are on (the HUD markers stay).

## Build

Needs a JDK 17 (or 21) to run Gradle and a JDK 8 for compiling (Gradle downloads
one if none is installed).

```sh
cd java-client
./gradlew build          # -> build/libs/PlebMc-1.0.0.jar
```

## How it is put together

| Path | What |
|---|---|
| `src/main/java/net/plebmc/client/` | The PlebMc client code from `sources/main/java/net/plebmc/client/` (Apollo, mods, screens, cosmetics). Only the few Eaglercraft platform calls were swapped for Java Edition ones. |
| `src/main/java/net/plebmc/forge/` | The Forge mod: events for keys, clicks, HUD, world pass, title screen and disconnect, plus the zoom key, raw packets (squad ping) and server list pin. |
| `src/main/java/net/plebmc/forge/compat/` | Stand-ins for Eaglercraft APIs: storage (files instead of browser storage), links, image decoding, GL helpers. |
| `src/main/java/net/plebmc/forge/mixin/` | 13 small mixins for the hooks Forge has no event for (nametags, flipped mobs, limbs, armor, cape, rainbow sheep, colored fire, TNT countdown, border, miss penalty, FOV/zoom, fullbright, nick hider, join and plugin messages). |

When the shared client code changes in `sources/main/java/net/plebmc/client/`, copy
the change here too. The differences are small: imports of
`net.lax1dude.eaglercraft...` become `net.plebmc.forge.compat...` or the vanilla
classes, and the places listed below.

### Differences from the Eaglercraft client

- **Server address**: Join PlebMc connects to `play.plebmc.top` (`PlebMenu.SERVER_ADDRESS`).
- **Resolution** setting is gone (Java Edition draws at the window size). FPS Boost
  still switches vsync, render distance and the fast video settings.
- **Zoom** is the mod's own key (C), since Java Edition has no built-in zoom.
- **Storage**: `config/plebmc/*.dat` instead of browser storage.
- Glow outlines use a Java Edition framebuffer instead of Eaglercraft's GL wrapper.
