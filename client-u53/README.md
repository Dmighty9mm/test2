# PlebClient on EaglercraftX u53

The browser client now builds on **EaglercraftX 1.8 u53** (July 2025), lax1dude's newest release.
The rest of this repository is the older u35 patch tree and is kept for reference; the Forge port
(`java-client/`) and the PlebioAPI plugin (`plugins/PlebMcShop/`) are unchanged.

## Build

```sh
sh client-u53/setup.sh                 # offline HTML (JavaScript)
sh client-u53/setup.sh "" wasm         # also the WASM-GC bundle
```

`setup.sh` clones the u53 workspace from
`https://gitflic.ru/project/lax1dude/eaglercraft-1_8-workspace` (commit `746793a`, "u53") into
`../eaglercraft_u53_workspace`, copies in the files below and runs the workspace's Gradle build.
It needs JDK 17 (`JDK17=/path/to/jdk` to point at another one) and network access to `gitflic.ru`
and `eaglercraft-teavm-fork.github.io`.

## What is where

- `overlay/` holds the u53 files that PlebClient hooks into, with the hooks applied: 27 game
  classes in `src/game/java` (title screen, theme, HUD, cosmetics layers, item models, RGB text,
  zoom, sprint, borders...) and the engine files in `src/main`, `src/teavm`, `src/lwjgl`,
  `src/wasm-gc-teavm` and `src/platform-api` (render scale for FPS Boost, the PlebMc default
  server, server resource packs without a hash, raw packets for squad pings, the RGB font path).
  Every hook calls into `net.plebmc.*`, so a future Eagler update only needs these files re-merged.
- `sources/main/java/net/plebmc` (shared with the old tree) is all of PlebClient's own code.
- `sources/resources/assets/plebitems` and `sources/resources/plebclient` are its assets.

## Porting notes (u35 to u53)

- `EntityLivingBase.getActivePotionEffects()` now returns an `ObjectContainer`; PlebClient uses
  `getActivePotionEffectsList()`.
- `IResourcePack` gained `getEaglerFileIndex()` (u53 reads OptiFine-style `.properties` files);
  PlebClient's item packs return an empty index or their wrapped pack's.
- u53 armor rendering first tries OptiFine CIT armor (`CustomItems`), then PlebClient's custom
  item armor, then vanilla.
- Mouse coordinates use u53's `windowDPI`; FPS Boost's render scale multiplies it.
- `DefaultServer` takes a third `hideAddress` argument.
- The WASM build has no render-scale option (the canvas size is set by its JS loader), so FPS
  Boost's resolution slider only applies to the JavaScript build.
