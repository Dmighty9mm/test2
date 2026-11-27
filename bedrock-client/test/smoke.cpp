// Smoke test for CI: a fake "game" window that renders with D3D11, loads PlebClient.dll,
// checks the overlay appears, opens the menu with Right Shift and saves screenshots.
#include <windows.h>
#include <d3d11.h>

#include <cstdio>
#include <string>

static void saveShot(HWND w, const char* file) {
    RECT r;
    GetClientRect(w, &r);
    POINT tl{0, 0};
    ClientToScreen(w, &tl);
    int cw = r.right, ch = r.bottom;
    HDC screen = GetDC(nullptr);
    HDC mem = CreateCompatibleDC(screen);
    HBITMAP bmp = CreateCompatibleBitmap(screen, cw, ch);
    HGDIOBJ old = SelectObject(mem, bmp);
    BitBlt(mem, 0, 0, cw, ch, screen, tl.x, tl.y, SRCCOPY | CAPTUREBLT);
    BITMAPINFOHEADER bi{sizeof(bi), cw, -ch, 1, 32, BI_RGB};
    std::string px(static_cast<size_t>(cw) * ch * 4, '\0');
    GetDIBits(mem, bmp, 0, ch, px.data(), reinterpret_cast<BITMAPINFO*>(&bi), DIB_RGB_COLORS);
    BITMAPFILEHEADER fh{0x4D42, static_cast<DWORD>(sizeof(fh) + sizeof(bi) + px.size()), 0, 0, sizeof(fh) + sizeof(bi)};
    FILE* f = fopen(file, "wb");
    fwrite(&fh, sizeof(fh), 1, f);
    fwrite(&bi, sizeof(bi), 1, f);
    fwrite(px.data(), px.size(), 1, f);
    fclose(f);
    SelectObject(mem, old);
    DeleteObject(bmp);
    DeleteDC(mem);
    ReleaseDC(nullptr, screen);
}

static void pressRightShift() {
    INPUT in[2]{};
    in[0].type = in[1].type = INPUT_KEYBOARD;
    in[0].ki.wVk = in[1].ki.wVk = VK_RSHIFT;
    in[0].ki.wScan = in[1].ki.wScan = 0x36;
    in[1].ki.dwFlags = KEYEVENTF_KEYUP;
    SendInput(1, &in[0], sizeof(INPUT));
    Sleep(150);
    SendInput(1, &in[1], sizeof(INPUT));
}

int main(int argc, char** argv) {
    const char* dll = argc > 1 ? argv[1] : "PlebClient.dll";
    WNDCLASSW wc{};
    wc.lpfnWndProc = DefWindowProcW;
    wc.hInstance = GetModuleHandleW(nullptr);
    wc.lpszClassName = L"FakeGame";
    RegisterClassW(&wc);
    HWND w = CreateWindowW(L"FakeGame", L"Minecraft (test)", WS_OVERLAPPEDWINDOW | WS_VISIBLE, 40, 40, 1296, 759,
                           nullptr, nullptr, wc.hInstance, nullptr);
    SetForegroundWindow(w);

    DXGI_SWAP_CHAIN_DESC sd{};
    sd.BufferCount = 2;
    sd.BufferDesc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
    sd.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
    sd.OutputWindow = w;
    sd.SampleDesc.Count = 1;
    sd.Windowed = TRUE;
    sd.SwapEffect = DXGI_SWAP_EFFECT_FLIP_DISCARD;
    IDXGISwapChain* sc = nullptr;
    ID3D11Device* dev = nullptr;
    ID3D11DeviceContext* ctx = nullptr;
    HRESULT hr = E_FAIL;
    for (D3D_DRIVER_TYPE t : {D3D_DRIVER_TYPE_HARDWARE, D3D_DRIVER_TYPE_WARP}) {
        hr = D3D11CreateDeviceAndSwapChain(nullptr, t, nullptr, 0, nullptr, 0, D3D11_SDK_VERSION, &sd, &sc, &dev, nullptr, &ctx);
        if (SUCCEEDED(hr)) break;
    }
    if (FAILED(hr)) {
        printf("FAIL: no D3D11 (%08lx)\n", hr);
        return 1;
    }

    bool loaded = false, shot1 = false, menuPressed = false, shot2 = false, found = false;
    ULONGLONG start = GetTickCount64();
    while (GetTickCount64() - start < 9000) {
        MSG m;
        while (PeekMessageW(&m, nullptr, 0, 0, PM_REMOVE)) DispatchMessageW(&m);
        ULONGLONG t = GetTickCount64() - start;
        ID3D11Texture2D* back = nullptr;
        sc->GetBuffer(0, __uuidof(ID3D11Texture2D), reinterpret_cast<void**>(&back));
        ID3D11RenderTargetView* rtv = nullptr;
        dev->CreateRenderTargetView(back, nullptr, &rtv);
        float sky[4] = {0.45f, 0.68f, 0.95f, 1};
        ctx->ClearRenderTargetView(rtv, sky);
        rtv->Release();
        back->Release();
        sc->Present(1, 0);

        if (!loaded && t > 500) {
            loaded = true;
            if (!LoadLibraryA(dll)) {
                printf("FAIL: LoadLibrary %lu\n", GetLastError());
                return 1;
            }
            printf("loaded %s\n", dll);
        }
        if (!shot1 && t > 3500) {
            shot1 = true;
            found = FindWindowW(L"PlebClientOverlay", nullptr) != nullptr;
            printf("overlay window: %s\n", found ? "yes" : "no");
            saveShot(w, "hud.bmp");
        }
        if (!menuPressed && t > 4000) {
            menuPressed = true;
            SetForegroundWindow(w);
            pressRightShift();
        }
        if (!shot2 && t > 6000) {
            shot2 = true;
            saveShot(w, "menu.bmp");
        }
    }
    printf(found ? "PASS\n" : "FAIL: overlay never appeared\n");
    return found ? 0 : 1;
}
