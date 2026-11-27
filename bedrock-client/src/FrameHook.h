#pragma once
#include <atomic>

// Counts the game's frames by hooking IDXGISwapChain::Present (and Present1).
namespace FrameHook {
bool install();
void uninstall();
extern std::atomic<unsigned long long> frames;
}
