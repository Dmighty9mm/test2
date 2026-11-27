cmake_minimum_required(VERSION 3.20)
project(PlebClientBedrock LANGUAGES C CXX RC)

# PlebClient for Minecraft Bedrock (Windows): a DLL you inject into the game.
# It draws its own HUD and menu in a see-through window on top of the game and
# counts frames by hooking DXGI's Present (MinHook). It never reads or changes the
# game's memory, so it keeps working across Bedrock updates.

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)
FetchContent_Declare(minhook
    GIT_REPOSITORY https://github.com/TsudaKageyu/minhook.git
    GIT_TAG c3fcafdc10146beb5919319d0683e44e3c30d537) # v1.3.4
FetchContent_GetProperties(minhook)
if(NOT minhook_POPULATED)
    FetchContent_Populate(minhook)
endif()
add_library(minhook STATIC
    ${minhook_SOURCE_DIR}/src/buffer.c
    ${minhook_SOURCE_DIR}/src/hook.c
    ${minhook_SOURCE_DIR}/src/trampoline.c
    ${minhook_SOURCE_DIR}/src/hde/hde64.c)
target_include_directories(minhook PUBLIC ${minhook_SOURCE_DIR}/include)

add_library(PlebClient SHARED
    src/dllmain.cpp
    src/Config.cpp
    src/FrameHook.cpp
    src/Gfx.cpp
    src/Hud.cpp
    src/Overlay.cpp
    src/resources.rc)
target_compile_definitions(PlebClient PRIVATE UNICODE _UNICODE WIN32_LEAN_AND_MEAN NOMINMAX _WIN32_WINNT=0x0A00)
target_link_libraries(PlebClient PRIVATE minhook d2d1 dwrite windowscodecs d3d11 dxgi user32 gdi32 shell32 ole32 shlwapi)
set_target_properties(PlebClient PROPERTIES PREFIX "" OUTPUT_NAME PlebClient)
if(MSVC)
    target_compile_options(PlebClient PRIVATE /W3 /utf-8)
    set_property(TARGET PlebClient minhook PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded")
else()
    # one DLL with nothing else to ship
    target_link_options(PlebClient PRIVATE -static -static-libgcc -static-libstdc++)
endif()

# CI smoke test: a fake game window that loads the DLL and takes screenshots
add_executable(smoke test/smoke.cpp)
target_compile_definitions(smoke PRIVATE UNICODE _UNICODE)
target_link_libraries(smoke PRIVATE d3d11 gdi32 user32)
