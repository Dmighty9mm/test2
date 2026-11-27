#pragma once
#include <string>
#include <vector>

#include "Gfx.h"

// What the HUD shows, filled in by the overlay every frame.
struct Stats {
    int fps = 0;
    int cpsLeft = 0;
    int cpsRight = 0;
    unsigned long long playMs = 0;
    bool gameFocused = false;
};

// The mouse over the overlay (only while the menu is open).
struct Mouse {
    float x = -1, y = -1;
    bool down = false;
    bool pressed = false;   // went down this frame
    bool released = false;  // went up this frame
};

struct Module {
    std::string id;
    std::wstring name;
    std::wstring desc;
    bool on = true;
    float fx = 0, fy = 0;  // position as a fraction of the screen
    Rect last{};           // where it was drawn last frame (pixels)
};

class Hud {
public:
    Hud();
    void load();
    // draws the HUD (and the menu when open); returns true when something changed that should be saved
    void frame(Gfx& g, float w, float h, const Stats& st, const Mouse& m);
    bool menuOpen() const { return menu; }
    void toggleMenu();
    void closeMenu();

private:
    float uiScale(float h) const;
    Rect drawModule(Gfx& g, Module& mod, float x, float y, float s, const Stats& st, bool measureOnly);
    void drawMenu(Gfx& g, float w, float h, const Mouse& m);
    void save();
    Color accent() const;

    std::vector<Module> mods;
    bool menu = false;
    bool editing = false;
    bool backgrounds = true;
    int sizeIndex = 1;    // small, normal, large
    int accentIndex = 0;  // see accent()
    int dragging = -1;
    float dragDx = 0, dragDy = 0;
    float menuAnim = 0;
};
