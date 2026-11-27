PlebClient for Minecraft Bedrock (Windows)
==========================================

How to use
1. Start Minecraft Bedrock.
2. Inject PlebClient.dll into Minecraft.Windows.exe with any DLL injector.
3. The HUD shows up on top of the game. Press Right Shift to open the menu.
   If the game has your mouse locked, press Esc first (pause menu), then Right Shift.

What it has
- Watermark (PlebClient and the Pleb head)
- FPS, CPS (left | right), Keystrokes (WASD, mouse buttons, space)
- Clock and Playtime (off by default)
- Menu: turn mods on and off, drag the HUD where you want it (Edit layout),
  HUD size, accent color and backgrounds.

Settings are saved in %APPDATA%\PlebClient\bedrock.cfg.

How it works
PlebClient draws in its own see-through window that sits on top of the game,
and counts frames by hooking DXGI Present. It never reads or changes the game's
memory, so updates to Minecraft don't break it.

Building
  cmake -S . -B build -G Ninja -DCMAKE_BUILD_TYPE=Release
  cmake --build build
Needs Visual Studio 2022 (or mingw-w64). MinHook is downloaded automatically.
