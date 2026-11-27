#pragma once
#include <windows.h>
#include <d2d1.h>
#include <dwrite.h>

#include <string>

struct Color {
    float r, g, b, a;
    static Color hex(unsigned rgb, float a = 1.f) {
        return {((rgb >> 16) & 255) / 255.f, ((rgb >> 8) & 255) / 255.f, (rgb & 255) / 255.f, a};
    }
    Color alpha(float k) const { return {r, g, b, a * k}; }
};

struct Rect {
    float x, y, w, h;
    bool contains(float px, float py) const { return px >= x && py >= y && px < x + w && py < y + h; }
};

// Direct2D drawing into the overlay's pixels (premultiplied BGRA).
class Gfx {
public:
    bool init();
    // starts a frame on a w x h canvas; returns false when drawing isn't possible
    bool begin(int w, int h);
    void end();
    HDC dc() const { return memDC; }

    void clear();
    void fill(Rect r, Color c, float radius = 0);
    void stroke(Rect r, Color c, float width, float radius = 0, bool dashed = false);
    // left/top aligned text; returns its width
    float text(const std::wstring& s, float x, float y, float size, Color c, bool bold = false, bool shadow = true);
    float measure(const std::wstring& s, float size, bool bold = false);
    void logo(Rect r, float opacity = 1.f);

private:
    IDWriteTextFormat* format(float size, bool bold);
    void loadLogo();

    ID2D1Factory* factory = nullptr;
    IDWriteFactory* dwrite = nullptr;
    ID2D1DCRenderTarget* rt = nullptr;
    ID2D1SolidColorBrush* brush = nullptr;
    ID2D1StrokeStyle* dashes = nullptr;
    ID2D1Bitmap* logoBmp = nullptr;
    bool logoTried = false;
    HDC memDC = nullptr;
    HBITMAP dib = nullptr;
    HGDIOBJ oldBmp = nullptr;
    int cw = 0, ch = 0;
};
