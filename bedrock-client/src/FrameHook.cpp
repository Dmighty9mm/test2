#include "FrameHook.h"

#include <windows.h>
#include <d3d11.h>
#include <dxgi1_2.h>

#include <MinHook.h>

#include <initializer_list>

namespace FrameHook {

std::atomic<unsigned long long> frames{0};

namespace {
using PresentFn = HRESULT(STDMETHODCALLTYPE*)(IDXGISwapChain*, UINT, UINT);
using Present1Fn = HRESULT(STDMETHODCALLTYPE*)(IDXGISwapChain1*, UINT, UINT, const DXGI_PRESENT_PARAMETERS*);
PresentFn origPresent = nullptr;
Present1Fn origPresent1 = nullptr;
void* presentAddr = nullptr;
void* present1Addr = nullptr;

HRESULT STDMETHODCALLTYPE hkPresent(IDXGISwapChain* sc, UINT sync, UINT flags) {
    if (!(flags & DXGI_PRESENT_TEST)) frames.fetch_add(1, std::memory_order_relaxed);
    return origPresent(sc, sync, flags);
}

HRESULT STDMETHODCALLTYPE hkPresent1(IDXGISwapChain1* sc, UINT sync, UINT flags, const DXGI_PRESENT_PARAMETERS* p) {
    if (!(flags & DXGI_PRESENT_TEST)) frames.fetch_add(1, std::memory_order_relaxed);
    return origPresent1(sc, sync, flags, p);
}
}  // namespace

bool install() {
    // a throwaway swap chain on a hidden window, just to find Present in dxgi.dll
    WNDCLASSEXW wc{sizeof(wc)};
    wc.lpfnWndProc = DefWindowProcW;
    wc.hInstance = GetModuleHandleW(nullptr);
    wc.lpszClassName = L"PlebClientDummy";
    RegisterClassExW(&wc);
    HWND hwnd = CreateWindowExW(0, wc.lpszClassName, L"", WS_OVERLAPPEDWINDOW, 0, 0, 64, 64, nullptr, nullptr,
                                wc.hInstance, nullptr);
    if (!hwnd) return false;

    DXGI_SWAP_CHAIN_DESC sd{};
    sd.BufferCount = 1;
    sd.BufferDesc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
    sd.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
    sd.OutputWindow = hwnd;
    sd.SampleDesc.Count = 1;
    sd.Windowed = TRUE;
    sd.SwapEffect = DXGI_SWAP_EFFECT_DISCARD;

    IDXGISwapChain* sc = nullptr;
    ID3D11Device* dev = nullptr;
    ID3D11DeviceContext* ctx = nullptr;
    HRESULT hr = E_FAIL;
    for (D3D_DRIVER_TYPE type : {D3D_DRIVER_TYPE_HARDWARE, D3D_DRIVER_TYPE_WARP}) {
        hr = D3D11CreateDeviceAndSwapChain(nullptr, type, nullptr, 0, nullptr, 0, D3D11_SDK_VERSION, &sd, &sc, &dev,
                                           nullptr, &ctx);
        if (SUCCEEDED(hr)) break;
    }
    if (FAILED(hr) || !sc) {
        DestroyWindow(hwnd);
        UnregisterClassW(wc.lpszClassName, wc.hInstance);
        return false;
    }
    void** vtbl = *reinterpret_cast<void***>(sc);
    presentAddr = vtbl[8];
    IDXGISwapChain1* sc1 = nullptr;
    if (SUCCEEDED(sc->QueryInterface(__uuidof(IDXGISwapChain1), reinterpret_cast<void**>(&sc1))) && sc1) {
        present1Addr = (*reinterpret_cast<void***>(sc1))[22];
        sc1->Release();
    }
    sc->Release();
    ctx->Release();
    dev->Release();
    DestroyWindow(hwnd);
    UnregisterClassW(wc.lpszClassName, wc.hInstance);

    if (MH_Initialize() != MH_OK) return false;
    bool ok = MH_CreateHook(presentAddr, reinterpret_cast<void*>(&hkPresent), reinterpret_cast<void**>(&origPresent)) == MH_OK;
    if (present1Addr) {
        MH_CreateHook(present1Addr, reinterpret_cast<void*>(&hkPresent1), reinterpret_cast<void**>(&origPresent1));
    }
    return ok && MH_EnableHook(MH_ALL_HOOKS) == MH_OK;
}

void uninstall() {
    MH_DisableHook(MH_ALL_HOOKS);
    MH_Uninitialize();
}

}  // namespace FrameHook
