"""Turns a checkout of Flarial's open-source client (flarialmc/dll-oss) into PlebClient for Bedrock.

    python3 rebrand.py <dll-oss checkout>

- Renames what players see (window title, messages, folders) to PlebClient, keeping
  credit to Flarial (the code is theirs, AGPL-3.0).
- Turns off everything that talks to Flarial's servers: the heartbeat (your name and
  server), online users, VIP list, telemetry, crash uploads and the donate messages.
- Swaps the logos for the PlebMc head and the PlebClient wordmark (overlay/).

Every edit must match exactly once (the upstream commit is pinned in flarial.commit),
so a changed upstream fails loudly instead of building something half-renamed.
"""
import os
import re
import shutil
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "flarial"
HERE = os.path.dirname(os.path.abspath(__file__))
problems = []


def edit(path, old, new, count=1):
    p = os.path.join(ROOT, path)
    with open(p, encoding="utf-8", errors="surrogateescape") as f:
        s = f.read()
    n = s.count(old)
    if n != count:
        problems.append("%s: expected %d of %r, found %d" % (path, count, old[:70], n))
        return
    with open(p, "w", encoding="utf-8", errors="surrogateescape", newline="") as f:
        f.write(s.replace(old, new))


# ---------- names players see ----------
edit("src/Client/Client.cpp", 'fmt::format("Flarial v{} {} [{}]"', 'fmt::format("PlebClient v{} {} [{}]"')
edit("src/Client/Hook/Hooks/Render/DirectX/DXGI/SwapchainHook.cpp", 'FindWindowByTitle("Flarial")', 'FindWindowByTitle("PlebClient")')
edit("src/Client/Client.cpp",
     '''            "This Minecraft version is not supported by Flarial Client.\\n\\n"
            "To fix this, try the following:\\n"
            "1. Make sure your launcher is using the release version of the client (not a custom DLL).\\n"
            "2. Restart the launcher and try again.\\n"
            "3. If you've done both of the above, then the client may have not updated :(\\n"
            "   Join our Discord for help and updates: https://flarial.xyz/discord",
            "Flarial - Unsupported Version", MB_OK | MB_ICONWARNING);''',
     '''            "This Minecraft version isn't supported by PlebClient yet.\\n\\n"
            "PlebClient for Bedrock is built on Flarial's open-source client, which supports\\n"
            "Minecraft 1.20.80 to 1.26.3. Use one of those versions, or wait for a PlebClient update.",
            "PlebClient - Unsupported Version", MB_OK | MB_ICONWARNING);''')
edit("src/Utils/BeaconWindow.cpp", 'L"Flarial Client"', 'L"PlebClient"')
edit("src/Utils/Logger/Logger.cpp", '"Flarial-Debugger"', '"PlebClient-Debugger"')
edit("src/Utils/Logger/crashlogs.cpp", 'L"Flarial crashed and saved a crash report.', 'L"PlebClient crashed and saved a crash report.')
edit("src/Client/Hook/Hooks/Visual/FontDrawTransformedHook.cpp", '"Flarial on top!"', '"Welcome to PlebMc!"')
edit("src/Client/Module/Modules/Nick/NickModule.cpp", '"Flarial User"', '"Pleb"')
edit("src/Client/Module/Modules/DebugMenu/DebugMenu.cpp", 'std::format("Flarial V2 [{}], Minecraft {}"', 'std::format("PlebClient [{}], Minecraft {}"')
edit("src/Client/Command/Commands/DiagCommand.cpp", '"=== Flarial Diagnostic Dump ==="', '"=== PlebClient Diagnostic Dump ==="')
edit("src/Client/Command/Commands/DiagCommand.cpp", '"Flarial commit: {}"', '"PlebClient (Flarial) commit: {}"')
edit("src/Client/Command/Commands/DiagCommand.cpp", '"§7Flarial §f{}', '"§7PlebClient §f{}')
edit("src/Client/Hook/Hooks/Render/DirectX/DXGI/SwapchainHook.cpp", 'L"Flarial: client failed', 'L"PlebClient: client failed', 2)
for f in ("DX11/SwapchainHook_DX11.cpp", "DX12/SwapchainHook_DX12.cpp"):
    edit("src/Client/Hook/Hooks/Render/DirectX/" + f, '"Join our discord! https://flarial.xyz/discord"',
         '"Welcome to PlebClient! Built on Flarial (flarial.xyz)."')
edit("src/Client/Module/Modules/ClickGUI/ClickGUI.cpp", '"No Flarial Logo"', '"No PlebClient Logo"')
edit("src/Client/Module/Modules/ClickGUI/ClickGUI.cpp", '"Stay anonymous on Flarial\'s API."', '"Not used by PlebClient."')
# the menu logo is the PlebMc head in full color, so it isn't tinted
edit("src/Client/Module/Modules/ClickGUI/ClickGUI.cpp", '"PNG", true, FlarialGUI::D2DColorToImColor(fLARIALlOGO));', '"PNG");')
dr = "src/Client/Module/Modules/Misc/DiscordRPC/DiscordRPCListener.hpp"
edit(dr, '.setLargeImageText("Flarial Client")', '.setLargeImageText("PlebClient")', 2)
edit(dr, '.setButton1("Website", "https://flarial.xyz", true)', '.setButton1("PlebMc", "https://client.plebmc.top", true)', 2)
edit(dr, '.setButton2("Discord Server", "https://flarial.xyz/discord", true)', '.setButton2("Built on Flarial", "https://flarial.xyz", true)', 2)
edit(dr, 'fmt::format("Flarial Client {} (#{})', 'fmt::format("PlebClient {} (#{})', 3)

# ---------- folders: %appdata%/PlebClient instead of Flarial's ----------
for dirpath, _, files in os.walk(os.path.join(ROOT, "src")):
    for name in files:
        if not name.endswith((".cpp", ".hpp")):
            continue
        p = os.path.join(dirpath, name)
        with open(p, encoding="utf-8", errors="surrogateescape") as f:
            s = f.read()
        t = s.replace('\\\\Flarial', '\\\\PlebClient')          # "...\\Flarial\\Config"
        t = t.replace('/ "Flarial" /', '/ "PlebClient" /')      # path / "Flarial" / "Scripts"
        t = t.replace('openSubFolder("Flarial\\\\', 'openSubFolder("PlebClient\\\\')
        t = t.replace('R"(\\Flarial\\Assets', 'R"(\\PlebClient\\Assets')
        t = t.replace('std::string dir = "Flarial" +', 'std::string dir = "PlebClient" +')
        t = t.replace('Roamingstate/Flarial/', 'Roamingstate/PlebClient/')
        if t != s:
            with open(p, "w", encoding="utf-8", errors="surrogateescape", newline="") as f:
                f.write(t)

# ---------- nothing goes to Flarial's servers ----------
edit("main.cpp", "if ((elapsed >= std::chrono::seconds(60)) and xuid != \"\") {",
     "if (false && (elapsed >= std::chrono::seconds(60)) and xuid != \"\") { // PlebClient: no heartbeat to Flarial")
edit("main.cpp", "if (onlineUsersFetchElapsed >= std::chrono::minutes(3) &&",
     "if (false && onlineUsersFetchElapsed >= std::chrono::minutes(3) &&")
edit("main.cpp", "if (vipFetchElapsed >= std::chrono::minutes(3) &&", "if (false && vipFetchElapsed >= std::chrono::minutes(3) &&")
edit("main.cpp", "if (onlineAnnouncementElapsed >= std::chrono::minutes(10) && ModuleManager::initialized &&",
     "if (false && onlineAnnouncementElapsed >= std::chrono::minutes(10) && ModuleManager::initialized &&")
edit("src/Utils/Telemetry.cpp",
     "void Telemetry::sendTelemetryAsync(const std::string& endpoint, const nlohmann::json& payload) {",
     "void Telemetry::sendTelemetryAsync(const std::string& endpoint, const nlohmann::json& payload) {\n"
     "    if (!endpoint.empty()) return; // PlebClient: no telemetry")
edit("src/Utils/CrashTelemetry.cpp",
     'auto result = APIUtils::POST_Simple("https://api.flarial.xyz/api/v1/crash-logs", jsonData);',
     'std::pair<long, std::string> result{0, "crash reports are not uploaded in PlebClient"};')
edit("src/Utils/APIUtils.cpp", 'std::string users = get("https://api.flarial.xyz/vips");', 'std::string users; // PlebClient: no Flarial API')
edit("src/Utils/APIUtils.cpp", 'std::string users = get("https://api.flarial.xyz/allOnlineUsers");', 'std::string users; // PlebClient: no Flarial API')


# ---------- dependencies that moved ----------
# EclipseMenu's rift and discord-presence are no longer on GitHub: Discord presence comes
# from a fork, and the expression formatter (rift) is replaced by a small built-in one.
# Branch-tracking dependencies are pinned so builds don't change under us.
edit("CMakeLists.txt", "GIT_REPOSITORY https://github.com/EclipseMenu/discord-presence.git\n        GIT_TAG main",
     "GIT_REPOSITORY https://github.com/X-rays5-forks/discord-presence.git\n        GIT_TAG c568a61cc05baac4e45f0392442cd932f901f19e")
edit("CMakeLists.txt", "GIT_REPOSITORY https://github.com/fmtlib/fmt.git\n        GIT_TAG        master",
     "GIT_REPOSITORY https://github.com/fmtlib/fmt.git\n        GIT_TAG        4968433a6abecbbf7e72e97188926586a7fd0662")
edit("CMakeLists.txt", "GIT_REPOSITORY https://github.com/Neargye/magic_enum.git\n        GIT_TAG        master",
     "GIT_REPOSITORY https://github.com/Neargye/magic_enum.git\n        GIT_TAG        7d87efb4a3dddbbe8caa9ca14eff05ede1102ab8")
edit("CMakeLists.txt", "GIT_REPOSITORY https://github.com/kunitoki/LuaBridge3.git\n        GIT_TAG        master",
     "GIT_REPOSITORY https://github.com/kunitoki/LuaBridge3.git\n        GIT_TAG        d6ff443436eeba6ee9840d22d89f8fbdda08580b")
edit("CMakeLists.txt", "GIT_REPOSITORY https://github.com/cursey/safetyhook.git\n        GIT_TAG main",
     "GIT_REPOSITORY https://github.com/cursey/safetyhook.git\n        GIT_TAG 755a4a08aa7d0d7143331dc854235ebc2d1570f9")
edit("CMakeLists.txt", "GIT_REPOSITORY https://github.com/DisabledMallis/NuvolaEventSystem.git\n        GIT_TAG        main",
     "GIT_REPOSITORY https://github.com/DisabledMallis/NuvolaEventSystem.git\n        GIT_TAG        a7b288004925916c2368ea8acee2cfc962c3c6b9")
edit("CMakeLists.txt", "GIT_REPOSITORY https://github.com/EquinoxHouse/jsoncpp.git\n       GIT_TAG mcbe",
     "GIT_REPOSITORY https://github.com/EquinoxHouse/jsoncpp.git\n       GIT_TAG 3000d0b3ee1a9c3dbe0c37129f3602442c1ccf97")
edit("CMakeLists.txt", """FetchContent_Declare(
        rift
        GIT_REPOSITORY https://github.com/EclipseMenu/rift.git
        GIT_TAG v2.0.2
)
""", "")
edit("CMakeLists.txt", "jsoncpp discord-rpc rift)", "jsoncpp discord-rpc)")
edit("CMakeLists.txt", "runtimeobject discord-rpc rift)", "runtimeobject discord-rpc)")
ef = "src/Client/GUI/Engine/ExpressionFormat.hpp"
edit(ef, "#include <rift.hpp>", "#include <cctype>\n#include <cstring>\n#include <vector>")
p = os.path.join(ROOT, ef)
with open(p, encoding="utf-8") as f:
    s = f.read()
a = s.find("    /**\n     * @brief Initialize the expression system")
b = s.find("    /**\n     * @brief Check if a format string contains any expressions")
if a < 0 or b < a:
    problems.append(ef + ": couldn't find the rift functions to replace")
else:
    with open(os.path.join(HERE, "ExpressionEval.inc"), encoding="utf-8") as f:
        s = s[:a] + f.read() + s[b:]
    s = s.replace("(powered by rift)", "").replace("(rift syntax)", "")
    with open(p, "w", encoding="utf-8", newline="") as f:
        f.write(s)

# ---------- the DLL is PlebClient.dll ----------
cm = os.path.join(ROOT, "CMakeLists.txt")
with open(cm, encoding="utf-8") as f:
    s = f.read()
if "add_library(Flarial SHARED" not in s:
    problems.append("CMakeLists.txt: add_library(Flarial SHARED ...) not found")
elif "OUTPUT_NAME PlebClient" not in s:
    with open(cm, "a", encoding="utf-8") as f:
        f.write("\n# PlebClient: the DLL is PlebClient.dll\nset_target_properties(Flarial PROPERTIES OUTPUT_NAME PlebClient)\n")

# ---------- logos ----------
src = os.path.join(HERE, "overlay")
for dirpath, _, files in os.walk(src):
    for name in files:
        rel = os.path.relpath(os.path.join(dirpath, name), src)
        dst = os.path.join(ROOT, rel)
        if not os.path.exists(dst):
            problems.append("overlay file has no upstream match: " + rel)
        shutil.copyfile(os.path.join(dirpath, name), dst)

left = []
for dirpath, _, files in os.walk(os.path.join(ROOT, "src")):
    for name in files:
        # Telemetry.cpp still names its endpoints, but sendTelemetryAsync returns first
        if name.endswith((".cpp", ".hpp")) and name != "Telemetry.cpp":
            with open(os.path.join(dirpath, name), encoding="utf-8", errors="surrogateescape") as f:
                if "api.flarial.xyz" in f.read():
                    left.append(name)
if left:
    problems.append("still calls Flarial's API: " + ", ".join(left))

if problems:
    print("rebrand failed:\n  " + "\n  ".join(problems))
    sys.exit(1)
print("PlebClient rebrand applied")
