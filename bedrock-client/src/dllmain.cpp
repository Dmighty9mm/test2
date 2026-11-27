// PlebClient for Minecraft Bedrock. Inject PlebClient.dll into Minecraft.Windows.exe.
#include <windows.h>
#include <objbase.h>

#include "Config.h"
#include "FrameHook.h"
#include "Overlay.h"

HMODULE g_module = nullptr;

namespace {
DWORD WINAPI mainThread(LPVOID) {
    HRESULT co = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    config.load();
    bool hooked = FrameHook::install();
    Overlay::run();
    if (hooked) FrameHook::uninstall();
    if (SUCCEEDED(co)) CoUninitialize();
    // give any thread still inside the Present hook a moment to leave it
    Sleep(200);
    FreeLibraryAndExitThread(g_module, 0);
}
}  // namespace

BOOL APIENTRY DllMain(HMODULE module, DWORD reason, LPVOID) {
    if (reason == DLL_PROCESS_ATTACH) {
        g_module = module;
        DisableThreadLibraryCalls(module);
        // stop a second injection from running a second overlay
        if (FindWindowW(L"PlebClientOverlay", nullptr)) return TRUE;
        if (HANDLE t = CreateThread(nullptr, 0, mainThread, nullptr, 0, nullptr)) CloseHandle(t);
    }
    return TRUE;
}
