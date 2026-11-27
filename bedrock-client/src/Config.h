#pragma once
#include <map>
#include <string>

// Settings kept in %APPDATA%\PlebClient\bedrock.cfg as "key=value" lines.
class Config {
public:
    void load();
    void save();
    bool getBool(const std::string& k, bool def) const;
    float getFloat(const std::string& k, float def) const;
    void set(const std::string& k, bool v);
    void set(const std::string& k, float v);
    bool dirty = false;

private:
    std::wstring path() const;
    std::map<std::string, std::string> values;
};

extern Config config;
