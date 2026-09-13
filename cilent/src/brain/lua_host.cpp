#include "brain/lua_host.hpp"
#include <cstdio>
#include <cmath>

namespace stella {

LuaHost::LuaHost() {
    L_ = luaL_newstate();
    if (L_) {
        luaL_openlibs(L_);
        registerFunctions();
    }
}

LuaHost::~LuaHost() {
    if (L_) lua_close(L_);
}

void LuaHost::registerFunctions() {
    // Store 'this' pointer as Lua registry lightuserdata
    lua_pushlightuserdata(L_, this);
    lua_setglobal(L_, "_lua_host");

    lua_register(L_, "attack", l_attack);
    lua_register(L_, "place", l_place);
    lua_register(L_, "break_block", l_break_block);
    lua_register(L_, "look_at", l_look_at);
    lua_register(L_, "swing_hand", l_swing);
    lua_register(L_, "set_slot", l_set_slot);
    lua_register(L_, "sneak", l_sneak);
    lua_register(L_, "sprint", l_sprint);
    lua_register(L_, "velocity", l_velocity);
    lua_register(L_, "use_item", l_use_item);
    lua_register(L_, "gamma", l_gamma);
    lua_register(L_, "chat", l_chat);
    lua_register(L_, "stop_all", l_stop_all);
    lua_register(L_, "player_distance", l_player_distance);
    lua_register(L_, "get_block", l_get_block);
    lua_register(L_, "log", l_log);
}

// Helper to get LuaHost* from Lua state
static LuaHost* getHost(lua_State* L) {
    lua_getglobal(L, "_lua_host");
    LuaHost* host = (LuaHost*)lua_touserdata(L, -1);
    lua_pop(L, 1);
    return host;
}

// Helper: access the pending instruction encoder stored in registry
static InstructionEncoder* getEncoder(lua_State* L) {
    lua_getfield(L, LUA_REGISTRYINDEX, "_encoder");
    InstructionEncoder* enc = (InstructionEncoder*)lua_touserdata(L, -1);
    lua_pop(L, 1);
    return enc;
}

// ---- Lua API functions ----

int LuaHost::l_attack(lua_State* L) {
    int32_t entityId = (int32_t)luaL_checkinteger(L, 1);
    if (auto* enc = getEncoder(L)) enc->attack(entityId);
    return 0;
}

int LuaHost::l_place(lua_State* L) {
    int32_t x = (int32_t)luaL_checkinteger(L, 1);
    int32_t y = (int32_t)luaL_checkinteger(L, 2);
    int32_t z = (int32_t)luaL_checkinteger(L, 3);
    uint8_t face = (uint8_t)luaL_optinteger(L, 4, 1); // default: up
    if (auto* enc = getEncoder(L)) enc->place(x, y, z, face);
    return 0;
}

int LuaHost::l_break_block(lua_State* L) {
    int32_t x = (int32_t)luaL_checkinteger(L, 1);
    int32_t y = (int32_t)luaL_checkinteger(L, 2);
    int32_t z = (int32_t)luaL_checkinteger(L, 3);
    if (auto* enc = getEncoder(L)) enc->breakBlock(x, y, z);
    return 0;
}

int LuaHost::l_look_at(lua_State* L) {
    double x = luaL_checknumber(L, 1);
    double y = luaL_checknumber(L, 2);
    double z = luaL_checknumber(L, 3);
    if (auto* enc = getEncoder(L)) enc->lookAt(x, y, z);
    return 0;
}

int LuaHost::l_swing(lua_State* L) {
    uint8_t hand = (uint8_t)luaL_optinteger(L, 1, 0);
    if (auto* enc = getEncoder(L)) enc->swing(hand);
    return 0;
}

int LuaHost::l_set_slot(lua_State* L) {
    uint8_t slot = (uint8_t)luaL_checkinteger(L, 1);
    if (auto* enc = getEncoder(L)) enc->setSlot(slot);
    return 0;
}

int LuaHost::l_sneak(lua_State* L) {
    bool state = lua_toboolean(L, 1);
    if (auto* enc = getEncoder(L)) enc->sneak(state);
    return 0;
}

int LuaHost::l_sprint(lua_State* L) {
    bool state = lua_toboolean(L, 1);
    if (auto* enc = getEncoder(L)) enc->sprint(state);
    return 0;
}

int LuaHost::l_velocity(lua_State* L) {
    float x = (float)luaL_checknumber(L, 1);
    float y = (float)luaL_checknumber(L, 2);
    float z = (float)luaL_checknumber(L, 3);
    if (auto* enc = getEncoder(L)) enc->velocity(x, y, z);
    return 0;
}

int LuaHost::l_use_item(lua_State* L) {
    if (auto* enc = getEncoder(L)) enc->useItem();
    return 0;
}

int LuaHost::l_gamma(lua_State* L) {
    float val = (float)luaL_checknumber(L, 1);
    if (auto* enc = getEncoder(L)) enc->gamma(val);
    return 0;
}

int LuaHost::l_chat(lua_State* L) {
    const char* text = luaL_checkstring(L, 1);
    if (auto* enc = getEncoder(L)) enc->chat(text);
    return 0;
}

int LuaHost::l_stop_all(lua_State* L) {
    if (auto* enc = getEncoder(L)) enc->stopAll();
    return 0;
}

int LuaHost::l_player_distance(lua_State* L) {
    double x = luaL_checknumber(L, 1);
    double y = luaL_checknumber(L, 2);
    double z = luaL_checknumber(L, 3);
    lua_getglobal(L, "world");
    if (!lua_istable(L, -1)) { lua_pop(L, 1); lua_pushnumber(L, 999999); return 1; }
    lua_getfield(L, -1, "player");
    if (!lua_istable(L, -1)) { lua_pop(L, 2); lua_pushnumber(L, 999999); return 1; }
    lua_getfield(L, -1, "x");
    double px = lua_tonumber(L, -1); lua_pop(L, 1);
    lua_getfield(L, -1, "y");
    double py = lua_tonumber(L, -1); lua_pop(L, 1);
    lua_getfield(L, -1, "z");
    double pz = lua_tonumber(L, -1); lua_pop(L, 1);
    lua_pop(L, 2); // pop player, world
    double dx = x - px, dy = y - py, dz = z - pz;
    lua_pushnumber(L, std::sqrt(dx * dx + dy * dy + dz * dz));
    return 1;
}

int LuaHost::l_get_block(lua_State* L) {
    int32_t x = (int32_t)luaL_checkinteger(L, 1);
    int32_t y = (int32_t)luaL_checkinteger(L, 2);
    int32_t z = (int32_t)luaL_checkinteger(L, 3);
    lua_getglobal(L, "world");
    lua_getfield(L, -1, "blocks");
    if (!lua_istable(L, -1)) { lua_pop(L, 2); lua_pushnil(L); return 1; }
    // blocks are stored as "x,y,z" -> blockName
    char key[64];
    snprintf(key, sizeof(key), "%d,%d,%d", x, y, z);
    lua_getfield(L, -1, key);
    lua_remove(L, -2); // remove blocks table
    lua_remove(L, -2); // remove world table
    return 1;
}

int LuaHost::l_log(lua_State* L) {
    const char* msg = luaL_checkstring(L, 1);
    fprintf(stderr, "[LUA] %s\n", msg);
    return 0;
}

// ---- Snapshot -> Lua table ----

void LuaHost::pushSnapshot(const WorldSnapshot& snap) {
    // Create world table
    lua_newtable(L_);

    lua_pushinteger(L_, snap.worldTime);
    lua_setfield(L_, -2, "time");

    // player
    lua_newtable(L_);
    auto& p = snap.player;
    lua_pushnumber(L_, p.x); lua_setfield(L_, -2, "x");
    lua_pushnumber(L_, p.y); lua_setfield(L_, -2, "y");
    lua_pushnumber(L_, p.z); lua_setfield(L_, -2, "z");
    lua_pushnumber(L_, p.pitch); lua_setfield(L_, -2, "pitch");
    lua_pushnumber(L_, p.yaw); lua_setfield(L_, -2, "yaw");
    lua_pushnumber(L_, p.health); lua_setfield(L_, -2, "health");
    lua_pushnumber(L_, p.armor); lua_setfield(L_, -2, "armor");
    lua_pushboolean(L_, p.onGround); lua_setfield(L_, -2, "onGround");
    lua_pushboolean(L_, p.sprinting); lua_setfield(L_, -2, "sprinting");
    lua_pushboolean(L_, p.sneaking); lua_setfield(L_, -2, "sneaking");
    lua_pushnumber(L_, p.fallDistance); lua_setfield(L_, -2, "fallDistance");
    lua_pushnumber(L_, p.velX); lua_setfield(L_, -2, "velX");
    lua_pushnumber(L_, p.velY); lua_setfield(L_, -2, "velY");
    lua_pushnumber(L_, p.velZ); lua_setfield(L_, -2, "velZ");
    lua_pushinteger(L_, p.mainHandItem); lua_setfield(L_, -2, "mainHandItem");
    lua_pushinteger(L_, p.offHandItem); lua_setfield(L_, -2, "offHandItem");
    lua_pushinteger(L_, p.selectedSlot); lua_setfield(L_, -2, "selectedSlot");
    lua_setfield(L_, -2, "player");

    // entities (array of tables)
    lua_newtable(L_);
    for (size_t i = 0; i < snap.entities.size(); i++) {
        auto& e = snap.entities[i];
        lua_newtable(L_);
        lua_pushinteger(L_, e.entityId); lua_setfield(L_, -2, "id");
        lua_pushinteger(L_, e.typeId); lua_setfield(L_, -2, "typeId");
        lua_pushnumber(L_, e.x); lua_setfield(L_, -2, "x");
        lua_pushnumber(L_, e.y); lua_setfield(L_, -2, "y");
        lua_pushnumber(L_, e.z); lua_setfield(L_, -2, "z");
        lua_pushnumber(L_, e.health); lua_setfield(L_, -2, "health");
        lua_pushnumber(L_, e.velX); lua_setfield(L_, -2, "velX");
        lua_pushnumber(L_, e.velY); lua_setfield(L_, -2, "velY");
        lua_pushnumber(L_, e.velZ); lua_setfield(L_, -2, "velZ");
        lua_pushboolean(L_, e.isPlayer); lua_setfield(L_, -2, "isPlayer");
        lua_rawseti(L_, -2, (int)(i + 1));
    }
    lua_setfield(L_, -2, "entities");

    // crystals (array of tables)
    lua_newtable(L_);
    for (size_t i = 0; i < snap.crystals.size(); i++) {
        auto& c = snap.crystals[i];
        lua_newtable(L_);
        lua_pushinteger(L_, c.entityId); lua_setfield(L_, -2, "id");
        lua_pushnumber(L_, c.x); lua_setfield(L_, -2, "x");
        lua_pushnumber(L_, c.y); lua_setfield(L_, -2, "y");
        lua_pushnumber(L_, c.z); lua_setfield(L_, -2, "z");
        lua_pushinteger(L_, c.baseBlock); lua_setfield(L_, -2, "baseBlock");
        lua_rawseti(L_, -2, (int)(i + 1));
    }
    lua_setfield(L_, -2, "crystals");

    // blocks (keyed by "x,y,z" -> blockName string)
    lua_newtable(L_);
    for (auto& b : snap.blocks) {
        char key[64];
        snprintf(key, sizeof(key), "%d,%d,%d", b.x, b.y, b.z);
        // Map blockId to simple name
        const char* name = "unknown";
        switch (b.blockId) {
            case 0: name = "air"; break;
            case 1: name = "stone"; break;
            case 2: name = "grass_block"; break;
            case 9: name = "water"; break;
            case 10: name = "lava"; break;
            case 33: name = "bedrock"; break;
            case 49: name = "obsidian"; break;
            case 127: name = "end_stone"; break;
            default: name = "other"; break;
        }
        lua_pushstring(L_, name);
        lua_setfield(L_, -2, key);
    }
    lua_setfield(L_, -2, "blocks");

    lua_setglobal(L_, "world");
}

// ---- Load / Tick ----

bool LuaHost::loadScript(const std::string& name, const std::string& path) {
    if (!L_) return false;
    if (luaL_dofile(L_, path.c_str()) != LUA_OK) {
        lastError_ = lua_tostring(L_, -1);
        lua_pop(L_, 1);
        return false;
    }
    modules_[name] = {name, false, true};
    return true;
}

bool LuaHost::loadScriptString(const std::string& name, const std::string& code) {
    if (!L_) return false;
    if (luaL_dostring(L_, code.c_str()) != LUA_OK) {
        lastError_ = lua_tostring(L_, -1);
        lua_pop(L_, 1);
        return false;
    }
    modules_[name] = {name, false, true};
    return true;
}

void LuaHost::setModuleEnabled(const std::string& name, bool enabled) {
    auto it = modules_.find(name);
    if (it != modules_.end()) {
        it->second.enabled = enabled;
    }
}

bool LuaHost::isModuleEnabled(const std::string& name) const {
    auto it = modules_.find(name);
    return it != modules_.end() && it->second.loaded && it->second.enabled;
}

std::vector<uint8_t> LuaHost::tick(const WorldSnapshot& snap) {
    InstructionEncoder encoder;

    if (!L_ || !snap.valid) return {};

    // Push world snapshot into Lua
    pushSnapshot(snap);

    // Store encoder in registry so Lua action functions can access it
    lua_pushlightuserdata(L_, &encoder);
    lua_setfield(L_, LUA_REGISTRYINDEX, "_encoder");

    // Call tick() for each enabled module
    for (auto& [name, mod] : modules_) {
        if (!mod.loaded || !mod.enabled) continue;

        // Try module_name.tick() first (table-based)
        lua_getglobal(L_, name.c_str());
        if (lua_istable(L_, -1)) {
            lua_getfield(L_, -1, "tick");
            if (lua_isfunction(L_, -1)) {
                lua_remove(L_, -2); // remove module table
                if (lua_pcall(L_, 0, 0, 0) != LUA_OK) {
                    lastError_ = std::string(name) + ": " + lua_tostring(L_, -1);
                    lua_pop(L_, 1);
                }
            } else {
                lua_pop(L_, 2); // pop nil tick + module table
            }
        } else {
            lua_pop(L_, 1); // pop nil/non-table
        }
    }

    // Clear encoder reference
    lua_pushnil(L_);
    lua_setfield(L_, LUA_REGISTRYINDEX, "_encoder");

    return encoder.build();
}

} // namespace stella
