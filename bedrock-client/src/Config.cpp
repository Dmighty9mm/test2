#include "Config.h"

#include <windows.h>
#include <shlobj.h>

#include <cstdio>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <sstream>

Config config;

std::wstring Config::path() const {
    PWSTR base = nullptr;
    std::wstring dir;
    if (SUCCEEDED(SHGetKnownFolderPath(FOLDERID_RoamingAppData, 0, nullptr, &base)) && base) {
        dir = base;
    }
    CoTaskMemFree(base);
    if (dir.empty()) {
        wchar_t tmp[MAX_PATH];
        DWORD n = GetTempPathW(MAX_PATH, tmp);
        dir.assign(tmp, n);
    }
    dir += L"\\PlebClient";
    CreateDirectoryW(dir.c_str(), nullptr);
    return dir + L"\\bedrock.cfg";
}

void Config::load() {
    std::ifstream in{std::filesystem::path(path())};
    std::string line;
    while (std::getline(in, line)) {
        size_t eq = line.find('=');
        if (eq != std::string::npos && line[0] != '#') {
            values[line.substr(0, eq)] = line.substr(eq + 1);
        }
    }
}

void Config::save() {
    std::ofstream out{std::filesystem::path(path()), std::ios::trunc};
    out << "# PlebClient for Bedrock settings\n";
    for (auto& [k, v] : values) {
        out << k << '=' << v << '\n';
    }
    dirty = false;
}

bool Config::getBool(const std::string& k, bool def) const {
    auto it = values.find(k);
    return it == values.end() ? def : it->second == "1";
}

float Config::getFloat(const std::string& k, float def) const {
    auto it = values.find(k);
    if (it == values.end()) return def;
    char* end = nullptr;
    float f = std::strtof(it->second.c_str(), &end);
    return end == it->second.c_str() ? def : f;
}

void Config::set(const std::string& k, bool v) {
    values[k] = v ? "1" : "0";
    dirty = true;
}

void Config::set(const std::string& k, float v) {
    char b[32];
    std::snprintf(b, sizeof b, "%.4f", v);
    values[k] = b;
    dirty = true;
}
