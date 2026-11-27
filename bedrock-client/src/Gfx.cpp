#include "Gfx.h"

#include <wincodec.h>

#include <algorithm>
#include <cmath>
#include <map>

extern HMODULE g_module;

namespace {
template <class T>
void release(T*& p) {
    if (p) {
        p->Release();
        p = nullptr;
    }
}
std::map<int, IDWriteTextFormat*> formats;  // key: size * 2 + bold
}  // namespace

bool Gfx::init() {
    if (FAILED(D2D1CreateFactory(D2D1_FACTORY_TYPE_SINGLE_THREADED, __uuidof(ID2D1Factory), nullptr,
                                 reinterpret_cast<void**>(&factory)))) {
        return false;
    }
    if (FAILED(DWriteCreateFactory(DWRITE_FACTORY_TYPE_SHARED, __uuidof(IDWriteFactory),
                                   reinterpret_cast<IUnknown**>(&dwrite)))) {
        return false;
    }
    D2D1_RENDER_TARGET_PROPERTIES props = D2D1::RenderTargetProperties(
        D2D1_RENDER_TARGET_TYPE_DEFAULT, D2D1::PixelFormat(DXGI_FORMAT_B8G8R8A8_UNORM, D2D1_ALPHA_MODE_PREMULTIPLIED), 0,
        0, D2D1_RENDER_TARGET_USAGE_NONE, D2D1_FEATURE_LEVEL_DEFAULT);
    if (FAILED(factory->CreateDCRenderTarget(&props, &rt))) return false;
    rt->SetTextAntialiasMode(D2D1_TEXT_ANTIALIAS_MODE_GRAYSCALE);
    if (FAILED(rt->CreateSolidColorBrush(D2D1::ColorF(1, 1, 1, 1), &brush))) return false;
    float dash[] = {4.f, 3.f};
    D2D1_STROKE_STYLE_PROPERTIES sp = D2D1::StrokeStyleProperties();
    sp.dashStyle = D2D1_DASH_STYLE_CUSTOM;
    factory->CreateStrokeStyle(&sp, dash, 2, &dashes);
    memDC = CreateCompatibleDC(nullptr);
    return memDC != nullptr;
}

bool Gfx::begin(int w, int h) {
    if (w <= 0 || h <= 0) return false;
    if (w != cw || h != ch) {
        if (oldBmp) SelectObject(memDC, oldBmp);
        if (dib) DeleteObject(dib);
        BITMAPINFO bi{};
        bi.bmiHeader.biSize = sizeof(bi.bmiHeader);
        bi.bmiHeader.biWidth = w;
        bi.bmiHeader.biHeight = -h;
        bi.bmiHeader.biPlanes = 1;
        bi.bmiHeader.biBitCount = 32;
        bi.bmiHeader.biCompression = BI_RGB;
        void* bits = nullptr;
        dib = CreateDIBSection(memDC, &bi, DIB_RGB_COLORS, &bits, nullptr, 0);
        if (!dib) return false;
        oldBmp = SelectObject(memDC, dib);
        cw = w;
        ch = h;
    }
    RECT rc{0, 0, w, h};
    if (FAILED(rt->BindDC(memDC, &rc))) return false;
    rt->BeginDraw();
    rt->Clear(D2D1::ColorF(0, 0, 0, 0));
    return true;
}

void Gfx::end() {
    if (rt->EndDraw() == static_cast<HRESULT>(D2DERR_RECREATE_TARGET)) {
        // the bitmap belonged to the old target
        release(logoBmp);
        logoTried = false;
    }
}

void Gfx::clear() { rt->Clear(D2D1::ColorF(0, 0, 0, 0)); }

void Gfx::fill(Rect r, Color c, float radius) {
    brush->SetColor(D2D1::ColorF(c.r, c.g, c.b, c.a));
    D2D1_RECT_F d = D2D1::RectF(r.x, r.y, r.x + r.w, r.y + r.h);
    if (radius > 0) {
        rt->FillRoundedRectangle(D2D1::RoundedRect(d, radius, radius), brush);
    } else {
        rt->FillRectangle(d, brush);
    }
}

void Gfx::stroke(Rect r, Color c, float width, float radius, bool dashed) {
    brush->SetColor(D2D1::ColorF(c.r, c.g, c.b, c.a));
    float h = width / 2;
    D2D1_RECT_F d = D2D1::RectF(r.x + h, r.y + h, r.x + r.w - h, r.y + r.h - h);
    ID2D1StrokeStyle* st = dashed ? dashes : nullptr;
    if (radius > 0) {
        rt->DrawRoundedRectangle(D2D1::RoundedRect(d, radius, radius), brush, width, st);
    } else {
        rt->DrawRectangle(d, brush, width, st);
    }
}

IDWriteTextFormat* Gfx::format(float size, bool bold) {
    int key = static_cast<int>(std::lround(size * 2)) * 2 + (bold ? 1 : 0);
    auto it = formats.find(key);
    if (it != formats.end()) return it->second;
    IDWriteTextFormat* f = nullptr;
    dwrite->CreateTextFormat(L"Segoe UI", nullptr, bold ? DWRITE_FONT_WEIGHT_BOLD : DWRITE_FONT_WEIGHT_SEMI_BOLD,
                             DWRITE_FONT_STYLE_NORMAL, DWRITE_FONT_STRETCH_NORMAL, size, L"en-us", &f);
    if (f) f->SetWordWrapping(DWRITE_WORD_WRAPPING_NO_WRAP);
    formats[key] = f;
    return f;
}

float Gfx::measure(const std::wstring& s, float size, bool bold) {
    IDWriteTextFormat* f = format(size, bold);
    if (!f) return 0;
    IDWriteTextLayout* layout = nullptr;
    if (FAILED(dwrite->CreateTextLayout(s.c_str(), static_cast<UINT32>(s.size()), f, 10000.f, 1000.f, &layout))) {
        return 0;
    }
    DWRITE_TEXT_METRICS m{};
    layout->GetMetrics(&m);
    layout->Release();
    return m.widthIncludingTrailingWhitespace;
}

float Gfx::text(const std::wstring& s, float x, float y, float size, Color c, bool bold, bool shadow) {
    IDWriteTextFormat* f = format(size, bold);
    if (!f) return 0;
    D2D1_RECT_F box = D2D1::RectF(x, y, x + 10000.f, y + size * 2);
    if (shadow) {
        float o = std::max(1.f, size / 14.f);
        brush->SetColor(D2D1::ColorF(0, 0, 0, c.a * 0.55f));
        D2D1_RECT_F sb = D2D1::RectF(x + o, y + o, x + o + 10000.f, y + o + size * 2);
        rt->DrawText(s.c_str(), static_cast<UINT32>(s.size()), f, sb, brush);
    }
    brush->SetColor(D2D1::ColorF(c.r, c.g, c.b, c.a));
    rt->DrawText(s.c_str(), static_cast<UINT32>(s.size()), f, box, brush);
    return measure(s, size, bold);
}

void Gfx::loadLogo() {
    logoTried = true;
    HRSRC res = FindResourceW(g_module, L"PLEB_LOGO", RT_RCDATA);
    if (!res) return;
    HGLOBAL mem = LoadResource(g_module, res);
    void* data = LockResource(mem);
    DWORD size = SizeofResource(g_module, res);
    if (!data || !size) return;
    IWICImagingFactory* wic = nullptr;
    if (FAILED(CoCreateInstance(CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&wic)))) return;
    IWICStream* stream = nullptr;
    IWICBitmapDecoder* dec = nullptr;
    IWICBitmapFrameDecode* frame = nullptr;
    IWICFormatConverter* conv = nullptr;
    if (SUCCEEDED(wic->CreateStream(&stream)) &&
        SUCCEEDED(stream->InitializeFromMemory(static_cast<BYTE*>(data), size)) &&
        SUCCEEDED(wic->CreateDecoderFromStream(stream, nullptr, WICDecodeMetadataCacheOnLoad, &dec)) &&
        SUCCEEDED(dec->GetFrame(0, &frame)) && SUCCEEDED(wic->CreateFormatConverter(&conv)) &&
        SUCCEEDED(conv->Initialize(frame, GUID_WICPixelFormat32bppPBGRA, WICBitmapDitherTypeNone, nullptr, 0,
                                   WICBitmapPaletteTypeCustom))) {
        rt->CreateBitmapFromWicBitmap(conv, nullptr, &logoBmp);
    }
    release(conv);
    release(frame);
    release(dec);
    release(stream);
    release(wic);
}

void Gfx::logo(Rect r, float opacity) {
    if (!logoTried) loadLogo();
    if (!logoBmp) return;
    rt->DrawBitmap(logoBmp, D2D1::RectF(r.x, r.y, r.x + r.w, r.y + r.h), opacity,
                   D2D1_BITMAP_INTERPOLATION_MODE_LINEAR);
}
