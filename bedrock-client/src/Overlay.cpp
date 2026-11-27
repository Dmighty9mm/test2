#include "Overlay.h"

#include <windows.h>

#include <atomic>
#include <chrono>
#include <deque>
#include <mutex>

#include "Config.h"
#include "FrameHook.h"
#include "Gfx.h"
#include "Hud.h"

extern HMODULE g_module;

namespace Overlay {
namespace {
std::atomic<bool> running{true};
HWND game = nullptr;
HWND overlay = nullptr;
HHOOK mouseHook = nullptr;

std::mutex clickLock;
std::deque<ULONGLONG> leftClicks, rightClicks;

ULONGLONG now() { return GetTickCount64(); }

bool gameInFront() {
    HWND fg = GetForegroundWindow();
    return fg && (fg == game || fg == overlay);
}

LRESULT CALLBACK mouseProc(int code, WPARAM wp, LPARAM lp) {
    if (code == HC_ACTION && (wp == WM_LBUTTONDOWN || wp == WM_RBUTTONDOWN)) {
        auto* info = reinterpret_cast<MSLLHOOKSTRUCT*>(lp);
        // ignore clicks made by other programs (autoclickers send injected input)
        if (!(info->flags & LLMHF_INJECTED) && gameInFront()) {
            std::lock_guard<std::mutex> g(clickLock);
            (wp == WM_LBUTTONDOWN ? leftClicks : rightClicks).push_back(now());
        }
    }
    return CallNextHookEx(mouseHook, code, wp, lp);
}

int countClicks(std::deque<ULONGLONG>& q) {
    std::lock_guard<std::mutex> g(clickLock);
    ULONGLONG t = now();
    while (!q.empty() && t - q.front() > 1000) q.pop_front();
    return static_cast<int>(q.size());
}

struct FindCtx {
    DWORD pid;
    HWND best;
    long long area;
};

BOOL CALLBACK findGame(HWND h, LPARAM lp) {
    auto* c = reinterpret_cast<FindCtx*>(lp);
    DWORD pid = 0;
    GetWindowThreadProcessId(h, &pid);
    if (pid != c->pid || h == overlay || !IsWindowVisible(h) || GetWindow(h, GW_OWNER)) return TRUE;
    RECT r;
    if (!GetClientRect(h, &r)) return TRUE;
    long long a = static_cast<long long>(r.right) * r.bottom;
    if (a > c->area) {
        c->area = a;
        c->best = h;
    }
    return TRUE;
}

HWND findGameWindow() {
    FindCtx c{GetCurrentProcessId(), nullptr, 0};
    EnumWindows(findGame, reinterpret_cast<LPARAM>(&c));
    return c.best;
}

LRESULT CALLBACK wndProc(HWND h, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
        case WM_MOUSEACTIVATE:
            return MA_NOACTIVATE;
        case WM_SETCURSOR:
            SetCursor(LoadCursorW(nullptr, IDC_ARROW));
            return TRUE;
        case WM_CLOSE:
            return 0;
    }
    return DefWindowProcW(h, msg, wp, lp);
}

void setClickThrough(bool through) {
    LONG_PTR ex = GetWindowLongPtrW(overlay, GWL_EXSTYLE);
    LONG_PTR want = through ? (ex | WS_EX_TRANSPARENT) : (ex & ~static_cast<LONG_PTR>(WS_EX_TRANSPARENT));
    if (want != ex) SetWindowLongPtrW(overlay, GWL_EXSTYLE, want);
}

void enableDpiAwareness() {
    // per-monitor DPI so the overlay lines up with the game's real pixels
    using Fn = BOOL(WINAPI*)(HANDLE);
    HMODULE u = GetModuleHandleW(L"user32.dll");
    if (auto f = reinterpret_cast<Fn>(reinterpret_cast<void*>(GetProcAddress(u, "SetThreadDpiAwarenessContext")))) {
        f(reinterpret_cast<HANDLE>(-4));  // DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2
    }
}
}  // namespace

void stop() { running = false; }

void run() {
    enableDpiAwareness();

    // the game window can take a moment to appear after injection
    for (int i = 0; i < 600 && running && !game; i++) {
        game = findGameWindow();
        if (!game) Sleep(100);
    }
    if (!game) return;

    WNDCLASSEXW wc{sizeof(wc)};
    wc.lpfnWndProc = wndProc;
    wc.hInstance = g_module;
    wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    wc.lpszClassName = L"PlebClientOverlay";
    RegisterClassExW(&wc);
    overlay = CreateWindowExW(WS_EX_LAYERED | WS_EX_TRANSPARENT | WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW,
                              wc.lpszClassName, L"PlebClient", WS_POPUP, 0, 0, 1, 1, game, nullptr, g_module,
                              nullptr);
    if (!overlay) {
        UnregisterClassW(wc.lpszClassName, g_module);
        return;
    }
    ShowWindow(overlay, SW_SHOWNOACTIVATE);

    mouseHook = SetWindowsHookExW(WH_MOUSE_LL, mouseProc, g_module, 0);

    Gfx gfx;
    bool gfxOk = gfx.init();
    Hud hud;
    hud.load();

    const ULONGLONG start = now();
    ULONGLONG fpsTime = now();
    unsigned long long fpsFrames = FrameHook::frames.load();
    int fps = 0;
    bool shiftWas = false, lmbWas = false;
    bool menuWas = false;

    while (running) {
        auto frameStart = std::chrono::steady_clock::now();

        MSG msg;
        while (PeekMessageW(&msg, nullptr, 0, 0, PM_REMOVE)) {
            TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
        if (!IsWindow(game)) break;

        // keep the overlay exactly on the game's drawing area
        RECT cr;
        GetClientRect(game, &cr);
        POINT tl{0, 0};
        ClientToScreen(game, &tl);
        int w = cr.right, h = cr.bottom;
        bool minimized = IsIconic(game) || w <= 0 || h <= 0;

        // FPS from the Present hook, updated twice a second
        ULONGLONG t = now();
        if (t - fpsTime >= 500) {
            unsigned long long f = FrameHook::frames.load();
            fps = static_cast<int>((f - fpsFrames) * 1000 / (t - fpsTime));
            fpsFrames = f;
            fpsTime = t;
        }

        bool front = gameInFront();
        bool shift = (GetAsyncKeyState(VK_RSHIFT) & 0x8000) != 0;
        if (shift && !shiftWas && front) hud.toggleMenu();
        shiftWas = shift;
        if (!front && hud.menuOpen()) hud.closeMenu();

        bool menu = hud.menuOpen();
        if (menu != menuWas) {
            setClickThrough(!menu);
            menuWas = menu;
        }

        Mouse m;
        if (menu) {
            POINT p;
            GetCursorPos(&p);
            ClipCursor(nullptr);
            m.x = static_cast<float>(p.x - tl.x);
            m.y = static_cast<float>(p.y - tl.y);
            m.down = (GetAsyncKeyState(VK_LBUTTON) & 0x8000) != 0;
            m.pressed = m.down && !lmbWas;
            m.released = !m.down && lmbWas;
            lmbWas = m.down;
        } else {
            lmbWas = false;
        }

        if (minimized || !gfxOk) {
            ShowWindow(overlay, SW_HIDE);
        } else {
            Stats st;
            st.fps = fps;
            st.cpsLeft = countClicks(leftClicks);
            st.cpsRight = countClicks(rightClicks);
            st.playMs = t - start;
            st.gameFocused = front;
            if (gfx.begin(w, h)) {
                hud.frame(gfx, static_cast<float>(w), static_cast<float>(h), st, m);
                gfx.end();
                POINT src{0, 0};
                SIZE size{w, h};
                BLENDFUNCTION bf{AC_SRC_OVER, 0, 255, AC_SRC_ALPHA};
                HDC screen = GetDC(nullptr);
                UpdateLayeredWindow(overlay, screen, &tl, &size, gfx.dc(), &src, 0, &bf, ULW_ALPHA);
                ReleaseDC(nullptr, screen);
            }
            if (!IsWindowVisible(overlay)) ShowWindow(overlay, SW_SHOWNOACTIVATE);
            // stay above the game without stealing focus
            SetWindowPos(overlay, HWND_TOP, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE);
        }

        if (config.dirty) config.save();

        auto budget = std::chrono::milliseconds(menu ? 16 : 33);
        auto spent = std::chrono::steady_clock::now() - frameStart;
        if (spent < budget) {
            Sleep(static_cast<DWORD>(std::chrono::duration_cast<std::chrono::milliseconds>(budget - spent).count()));
        }
    }

    if (hud.menuOpen()) hud.closeMenu();
    if (config.dirty) config.save();
    if (mouseHook) UnhookWindowsHookEx(mouseHook);
    DestroyWindow(overlay);
    UnregisterClassW(wc.lpszClassName, g_module);
    overlay = nullptr;
}

}  // namespace Overlay
