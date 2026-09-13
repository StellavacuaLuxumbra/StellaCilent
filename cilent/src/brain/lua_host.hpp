#pragma once
// Lua brain host for Stella Client.
// Manages Lua VM, loads module scripts, bridges world snapshot <-> Lua tables.

#include "snapshot.hpp"
#include "instruction.hpp"

extern "C" {
#include "lua/lua.h"
#include "lua/lualib.h"
#include "lua/lauxlib.h"
}

#include <functional>
#include <string>
#include <unordered_map>
#include <vector>

namespace stella {

class LuaHost {
public:
    LuaHost();
    ~LuaHost();

    // Non-copyable
    LuaHost(const LuaHost&) = delete;
    LuaHost& operator=(const LuaHost&) = delete;

    // Load a Lua script from file
    bool loadScript(const std::string& name, const std::string& path);

    // Load a Lua script from embedded string
    bool loadScriptString(const std::string& name, const std::string& code);

    // Push world snapshot into Lua globals and call tick() for all loaded modules
    // Returns the encoded instruction payload to send to Java
    std::vector<uint8_t> tick(const WorldSnapshot& snap);

    // Enable/disable a Lua module
    void setModuleEnabled(const std::string& name, bool enabled);

    // Check if a module is loaded and enabled
    bool isModuleEnabled(const std::string& name) const;

    // Get last error message
    const std::string& lastError() const { return lastError_; }

private:
    lua_State* L_ = nullptr;
    std::string lastError_;

    struct LuaModule {
        std::string name;
        bool enabled = false;
        bool loaded = false;
    };
    std::unordered_map<std::string, LuaModule> modules_;

    // Push world snapshot into Lua global table `world`
    void pushSnapshot(const WorldSnapshot& snap);

    // Register C function bindings into Lua
    void registerFunctions();

    // Lua-callable functions (static, use upvalue for LuaHost*)
    static int l_attack(lua_State* L);
    static int l_place(lua_State* L);
    static int l_break_block(lua_State* L);
    static int l_look_at(lua_State* L);
    static int l_swing(lua_State* L);
    static int l_set_slot(lua_State* L);
    static int l_sneak(lua_State* L);
    static int l_sprint(lua_State* L);
    static int l_velocity(lua_State* L);
    static int l_use_item(lua_State* L);
    static int l_gamma(lua_State* L);
    static int l_chat(lua_State* L);
    static int l_stop_all(lua_State* L);
    static int l_player_distance(lua_State* L);
    static int l_get_block(lua_State* L);
    static int l_log(lua_State* L);
};

} // namespace stella
