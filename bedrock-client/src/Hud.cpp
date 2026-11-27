#include "Hud.h"

#include <windows.h>

#include <algorithm>
#include <cmath>
#include <cstdio>

#include "Config.h"

namespace {
const Color kWhite = Color::hex(0xFFFFFF);
const Color kMuted = Color::hex(0xA9B4C2);
const Color kPanel = Color::hex(0x0E151F, 0.94f);
const Color kCard = Color::hex(0x172230, 1.f);
const Color kLine = Color::hex(0x2A3A4D, 1.f);

struct AccentDef {
    const wchar_t* name;
    unsigned rgb;
};
const AccentDef kAccents[] = {{L"Cyan", 0x3FD8E6}, {L"Gold", 0xF5C542}, {L"Green", 0x3FDC8C},
                              {L"Pink", 0xF06CA8}, {L"Purple", 0x9B7BFF}, {L"Red", 0xF0524F}};
const wchar_t* kSizes[] = {L"Small", L"Normal", L"Large"};
const float kSizeScale[] = {0.8f, 1.f, 1.25f};

bool keyDown(int vk) { return (GetAsyncKeyState(vk) & 0x8000) != 0; }

std::wstring num(long long n) { return std::to_wstring(n); }
}  // namespace

Hud::Hud() {
    mods = {
        {"watermark", L"Watermark", L"PlebClient and the Pleb head", true, 0.010f, 0.015f},
        {"fps", L"FPS", L"Frames per second", true, 0.010f, 0.075f},
        {"cps", L"CPS", L"Clicks per second, left and right", true, 0.010f, 0.115f},
        {"keystrokes", L"Keystrokes", L"W A S D, mouse buttons and space", true, 0.010f, 0.62f},
        {"clock", L"Clock", L"Your computer's time", false, 0.010f, 0.155f},
        {"playtime", L"Playtime", L"How long you've been playing", false, 0.010f, 0.195f},
    };
}

void Hud::load() {
    for (auto& m : mods) {
        m.on = config.getBool(m.id + ".on", m.on);
        m.fx = config.getFloat(m.id + ".x", m.fx);
        m.fy = config.getFloat(m.id + ".y", m.fy);
    }
    backgrounds = config.getBool("hud.backgrounds", true);
    sizeIndex = std::clamp(static_cast<int>(config.getFloat("hud.size", 1)), 0, 2);
    accentIndex = std::clamp(static_cast<int>(config.getFloat("hud.accent", 0)), 0,
                             static_cast<int>(std::size(kAccents)) - 1);
    config.dirty = false;
}

void Hud::save() {
    for (auto& m : mods) {
        config.set(m.id + ".on", m.on);
        config.set(m.id + ".x", m.fx);
        config.set(m.id + ".y", m.fy);
    }
    config.set("hud.backgrounds", backgrounds);
    config.set("hud.size", static_cast<float>(sizeIndex));
    config.set("hud.accent", static_cast<float>(accentIndex));
}

Color Hud::accent() const { return Color::hex(kAccents[accentIndex].rgb); }

float Hud::uiScale(float h) const { return std::clamp(h / 1080.f, 0.6f, 2.5f); }

void Hud::toggleMenu() {
    if (menu) {
        closeMenu();
    } else {
        menu = true;
        editing = false;
    }
}

void Hud::closeMenu() {
    menu = false;
    editing = false;
    dragging = -1;
    save();
}

Rect Hud::drawModule(Gfx& g, Module& mod, float x, float y, float s, const Stats& st, bool measureOnly) {
    const float pad = 7 * s, fs = 17 * s;
    const Color acc = accent();
    auto box = [&](float w, float h) {
        Rect r{x, y, w, h};
        if (!measureOnly && backgrounds) g.fill(r, Color::hex(0x000000, 0.42f), 5 * s);
        return r;
    };
    auto labelValue = [&](const std::wstring& label, const std::wstring& value) {
        float lw = g.measure(label, fs, true), vw = g.measure(value, fs, false);
        Rect r = box(pad * 2 + lw + 5 * s + vw, fs * 1.35f + pad);
        if (!measureOnly) {
            g.text(label, x + pad, y + pad * 0.45f, fs, acc, true);
            g.text(value, x + pad + lw + 5 * s, y + pad * 0.45f, fs, kWhite);
        }
        return r;
    };

    if (mod.id == "watermark") {
        float ls = 26 * s, ts = 20 * s;
        float tw = g.measure(L"PlebClient", ts, true);
        Rect r = box(pad * 2 + ls + 6 * s + tw, ls + pad * 2);
        if (!measureOnly) {
            g.logo({x + pad, y + pad, ls, ls});
            g.text(L"PlebClient", x + pad + ls + 6 * s, y + pad + (ls - ts * 1.33f) / 2, ts, kWhite, true);
        }
        return r;
    }
    if (mod.id == "fps") return labelValue(L"FPS", num(st.fps));
    if (mod.id == "cps") return labelValue(L"CPS", num(st.cpsLeft) + L" | " + num(st.cpsRight));
    if (mod.id == "clock") {
        SYSTEMTIME t;
        GetLocalTime(&t);
        int h12 = t.wHour % 12 == 0 ? 12 : t.wHour % 12;
        wchar_t b[32];
        swprintf(b, 32, L"%d:%02d %ls", h12, t.wMinute, t.wHour < 12 ? L"AM" : L"PM");
        return labelValue(L"Time", b);
    }
    if (mod.id == "playtime") {
        unsigned long long sec = st.playMs / 1000;
        wchar_t b[32];
        swprintf(b, 32, L"%llu:%02llu:%02llu", sec / 3600, (sec / 60) % 60, sec % 60);
        return labelValue(L"Played", b);
    }
    if (mod.id == "keystrokes") {
        const float k = 38 * s, gap = 3 * s;
        const float W = k * 3 + gap * 2;
        Rect all{x, y, W, k * 3 + gap * 3 + k * 0.55f};
        if (measureOnly) return all;
        auto key = [&](Rect r, const std::wstring& label, bool down, const std::wstring& sub = L"") {
            g.fill(r, down ? Color::hex(0xFFFFFF, 0.85f) : Color::hex(0x000000, backgrounds ? 0.42f : 0.2f), 5 * s);
            Color tc = down ? Color::hex(0x111111) : kWhite;
            float ts = (sub.empty() ? 16 : 14) * s;
            float tw = g.measure(label, ts, true);
            float ty = r.y + (r.h - ts * 1.33f) / 2 - (sub.empty() ? 0 : 6 * s);
            g.text(label, r.x + (r.w - tw) / 2, ty, ts, tc, true, !down);
            if (!sub.empty()) {
                float ss = 10.5f * s, sw = g.measure(sub, ss);
                g.text(sub, r.x + (r.w - sw) / 2, ty + ts * 1.2f, ss, down ? Color::hex(0x333333) : kMuted, false, !down);
            }
        };
        key({x + k + gap, y, k, k}, L"W", keyDown('W'));
        key({x, y + k + gap, k, k}, L"A", keyDown('A'));
        key({x + k + gap, y + k + gap, k, k}, L"S", keyDown('S'));
        key({x + (k + gap) * 2, y + k + gap, k, k}, L"D", keyDown('D'));
        float half = (W - gap) / 2;
        key({x, y + (k + gap) * 2, half, k}, L"LMB", keyDown(VK_LBUTTON), num(st.cpsLeft) + L" CPS");
        key({x + half + gap, y + (k + gap) * 2, half, k}, L"RMB", keyDown(VK_RBUTTON), num(st.cpsRight) + L" CPS");
        Rect sp{x, y + (k + gap) * 3, W, k * 0.55f};
        bool spDown = keyDown(VK_SPACE);
        g.fill(sp, spDown ? Color::hex(0xFFFFFF, 0.85f) : Color::hex(0x000000, backgrounds ? 0.42f : 0.2f), 5 * s);
        g.fill({sp.x + W * 0.3f, sp.y + sp.h / 2 - 1 * s, W * 0.4f, 2 * s}, spDown ? Color::hex(0x111111) : kWhite);
        return all;
    }
    return {x, y, 0, 0};
}

void Hud::frame(Gfx& g, float w, float h, const Stats& st, const Mouse& m) {
    const float s = uiScale(h) * kSizeScale[sizeIndex];
    menuAnim = std::clamp(menuAnim + (menu ? 0.2f : -0.25f), 0.f, 1.f);

    // dragging HUD pieces in edit mode
    if (editing) {
        if (m.pressed) {
            for (int i = static_cast<int>(mods.size()) - 1; i >= 0; i--) {
                if (mods[i].on && mods[i].last.contains(m.x, m.y)) {
                    dragging = i;
                    dragDx = m.x - mods[i].last.x;
                    dragDy = m.y - mods[i].last.y;
                    break;
                }
            }
        }
        if (dragging >= 0 && m.down) {
            Module& d = mods[dragging];
            d.fx = std::clamp((m.x - dragDx) / w, 0.f, std::max(0.f, 1.f - d.last.w / w));
            d.fy = std::clamp((m.y - dragDy) / h, 0.f, std::max(0.f, 1.f - d.last.h / h));
        }
        if (m.released) dragging = -1;
    }

    if (menuAnim > 0 && !editing) g.fill({0, 0, w, h}, Color::hex(0x000000, 0.35f * menuAnim));

    for (auto& mod : mods) {
        if (!mod.on) continue;
        Rect size = drawModule(g, mod, 0, 0, s, st, true);
        float x = std::clamp(mod.fx * w, 0.f, std::max(0.f, w - size.w));
        float y = std::clamp(mod.fy * h, 0.f, std::max(0.f, h - size.h));
        mod.last = drawModule(g, mod, x, y, s, st, false);
        if (editing) {
            bool hot = mod.last.contains(m.x, m.y) || &mod == (dragging >= 0 ? &mods[dragging] : nullptr);
            g.stroke({mod.last.x - 3, mod.last.y - 3, mod.last.w + 6, mod.last.h + 6},
                     hot ? accent() : Color::hex(0xFFFFFF, 0.7f), 1.5f, 6, true);
        }
    }

    if (editing) {
        const float ms = uiScale(h);
        std::wstring t = L"Drag the HUD where you want it";
        float tw = g.measure(t, 16 * ms, true);
        float bw = 110 * ms, bh = 34 * ms;
        Rect bar{(w - tw - bw - 40 * ms) / 2, 18 * ms, tw + bw + 40 * ms, bh + 16 * ms};
        g.fill(bar, kPanel, 10 * ms);
        g.text(t, bar.x + 14 * ms, bar.y + (bar.h - 16 * ms * 1.33f) / 2, 16 * ms, kWhite, true, false);
        Rect done{bar.x + bar.w - bw - 8 * ms, bar.y + 8 * ms, bw, bh};
        bool hot = done.contains(m.x, m.y);
        g.fill(done, hot ? accent() : accent().alpha(0.85f), 8 * ms);
        float dw = g.measure(L"Done", 15 * ms, true);
        g.text(L"Done", done.x + (bw - dw) / 2, done.y + (bh - 15 * ms * 1.33f) / 2, 15 * ms, Color::hex(0x0B1118), true, false);
        if (m.pressed && done.contains(m.x, m.y)) {
            editing = false;
            dragging = -1;
            save();
        }
        return;
    }
    if (menuAnim > 0) drawMenu(g, w, h, m);
}

void Hud::drawMenu(Gfx& g, float w, float h, const Mouse& m) {
    const float s = uiScale(h);
    const float a = menuAnim;
    const Color acc = accent();
    const float pw = 660 * s, ph = 452 * s, pad = 20 * s;
    const float lift = (1 - a) * 14 * s;
    Rect panel{(w - pw) / 2, (h - ph) / 2 + lift, pw, ph};
    g.fill(panel, kPanel.alpha(a), 14 * s);
    g.stroke(panel, kLine.alpha(a), 1, 14 * s);
    g.fill({panel.x + pad, panel.y, pw - pad * 2, 2 * s}, acc.alpha(a));

    // header
    float y = panel.y + pad;
    g.logo({panel.x + pad, y, 44 * s, 44 * s}, a);
    g.text(L"PlebClient", panel.x + pad + 54 * s, y - 2 * s, 26 * s, kWhite.alpha(a), true, false);
    g.text(L"for Minecraft Bedrock", panel.x + pad + 56 * s, y + 30 * s, 13 * s, acc.alpha(a), false, false);
    Rect close{panel.x + pw - pad - 30 * s, y + 4 * s, 30 * s, 30 * s};
    bool closeHot = close.contains(m.x, m.y);
    g.fill(close, (closeHot ? kLine : kCard).alpha(a), 8 * s);
    float xw = g.measure(L"✕", 14 * s);
    g.text(L"✕", close.x + (close.w - xw) / 2, close.y + (close.h - 14 * s * 1.33f) / 2, 14 * s, kWhite.alpha(a), false, false);
    y += 62 * s;

    // module cards, three to a row
    g.text(L"MODS", panel.x + pad, y, 11.5f * s, kMuted.alpha(a), true, false);
    y += 20 * s;
    const float gap = 10 * s, cw = (pw - pad * 2 - gap * 2) / 3, chh = 70 * s;
    for (size_t i = 0; i < mods.size(); i++) {
        Module& mod = mods[i];
        Rect c{panel.x + pad + (i % 3) * (cw + gap), y + (i / 3) * (chh + gap), cw, chh};
        bool hot = c.contains(m.x, m.y);
        g.fill(c, (hot ? Color::hex(0x1D2B3C) : kCard).alpha(a), 10 * s);
        g.stroke(c, (mod.on ? acc : kLine).alpha(a), mod.on ? 1.5f : 1.f, 10 * s);
        g.text(mod.name, c.x + 12 * s, c.y + 10 * s, 15 * s, kWhite.alpha(a), true, false);
        g.text(mod.desc.size() > 30 ? mod.desc.substr(0, 29) + L"…" : mod.desc, c.x + 12 * s, c.y + 34 * s, 11.5f * s,
               kMuted.alpha(a), false, false);
        // on/off switch
        Rect sw{c.x + c.w - 44 * s, c.y + 12 * s, 32 * s, 18 * s};
        g.fill(sw, (mod.on ? acc : kLine).alpha(a), 9 * s);
        float kx = mod.on ? sw.x + sw.w - 16 * s : sw.x + 2 * s;
        g.fill({kx, sw.y + 2 * s, 14 * s, 14 * s}, kWhite.alpha(a), 7 * s);
        if (m.pressed && hot) {
            mod.on = !mod.on;
            save();
        }
    }
    y += ((mods.size() + 2) / 3) * (chh + gap) + 8 * s;

    // settings buttons
    g.text(L"HUD", panel.x + pad, y, 11.5f * s, kMuted.alpha(a), true, false);
    y += 20 * s;
    struct Btn {
        std::wstring label;
        int action;
    };
    Btn btns[] = {{L"Edit layout", 0},
                  {std::wstring(L"Size: ") + kSizes[sizeIndex], 1},
                  {std::wstring(L"Color: ") + kAccents[accentIndex].name, 2},
                  {std::wstring(L"Backgrounds: ") + (backgrounds ? L"On" : L"Off"), 3}};
    const float bw = (pw - pad * 2 - gap * 3) / 4, bh = 38 * s;
    for (int i = 0; i < 4; i++) {
        Rect b{panel.x + pad + i * (bw + gap), y, bw, bh};
        bool hot = b.contains(m.x, m.y);
        bool primary = i == 0;
        g.fill(b, (primary ? (hot ? acc : acc.alpha(0.85f)) : (hot ? Color::hex(0x1D2B3C) : kCard)).alpha(a), 9 * s);
        if (!primary) g.stroke(b, kLine.alpha(a), 1, 9 * s);
        float tw = g.measure(btns[i].label, 13.5f * s, true);
        g.text(btns[i].label, b.x + (b.w - tw) / 2, b.y + (bh - 13.5f * s * 1.33f) / 2, 13.5f * s,
               (primary ? Color::hex(0x0B1118) : kWhite).alpha(a), true, false);
        if (m.pressed && hot) {
            if (i == 0) editing = true;
            if (i == 1) sizeIndex = (sizeIndex + 1) % 3;
            if (i == 2) accentIndex = (accentIndex + 1) % static_cast<int>(std::size(kAccents));
            if (i == 3) backgrounds = !backgrounds;
            save();
        }
    }
    y += bh + 16 * s;
    g.text(L"Right Shift opens this menu. If your mouse is stuck in the game, press Esc first.", panel.x + pad, y,
           12 * s, kMuted.alpha(a), false, false);

    if (m.pressed && closeHot) closeMenu();
}
