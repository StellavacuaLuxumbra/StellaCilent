#pragma once
// Shared-memory IPC protocol constants.
// Must be kept in sync with executer/src/main/java/dev/stella/executer/ipc/Protocol.java
// (see docs/protocol.md).
//
// Java = thin driver: rendering primitives + low-level actions + world queries.
// C++  = brain: all game logic lives here.

#include <cstdint>

namespace stella {

constexpr uint32_t kMagic = 0x53544C31u; // "STL1"
constexpr uint32_t kVersion = 2;

constexpr uint64_t kHeaderSize = 64;
constexpr uint32_t kDataCap = 8 * 1024 * 1024;
constexpr uint64_t kRegionSize = 16ull + kDataCap;
constexpr uint64_t kFileSize = kHeaderSize + 2ull * kRegionSize;

constexpr int kHMagic = 0;
constexpr int kHVersion = 4;
constexpr int kHDataCap = 8;

constexpr uint64_t kOffNativeToJava = kHeaderSize;
constexpr uint64_t kOffJavaToNative = kHeaderSize + kRegionSize;

// ============================================================
// C++ -> Java  (commands)
// ============================================================

// handshake
constexpr uint32_t kOpConnect      = 0;

// rendering
constexpr uint32_t kCmdDrawLine    = 50;
constexpr uint32_t kCmdDrawBox     = 51;
constexpr uint32_t kCmdDrawText    = 52;
constexpr uint32_t kCmdDrawRect    = 53;
constexpr uint32_t kCmdDrawRectF   = 54;
constexpr uint32_t kCmdClearRender = 55;

// actions
constexpr uint32_t kCmdSwingHand   = 10;
constexpr uint32_t kCmdAttack      = 11;
constexpr uint32_t kCmdPlaceBlock  = 12;
constexpr uint32_t kCmdBreakBlock  = 13;
constexpr uint32_t kCmdSetSlot     = 14;
constexpr uint32_t kCmdUseItem     = 15;
constexpr uint32_t kCmdSneak       = 16;
constexpr uint32_t kCmdSprint      = 17;
constexpr uint32_t kCmdSetVelocity = 18;
constexpr uint32_t kCmdSendChat    = 19;
constexpr uint32_t kCmdLookAt      = 20;
constexpr uint32_t kCmdMoveTo      = 21;
constexpr uint32_t kCmdSetGamma    = 22;
constexpr uint32_t kCmdAutoDamage  = 23;
constexpr uint32_t kCmdStopAll     = 24;

// queries
constexpr uint32_t kCmdGetPlayerPos  = 30;
constexpr uint32_t kCmdGetEntities   = 31;
constexpr uint32_t kCmdGetBlockAt    = 32;
constexpr uint32_t kCmdGetRaytrace   = 33;
constexpr uint32_t kCmdGetPlayers    = 34;
constexpr uint32_t kCmdGetInventory  = 35;

// module control
constexpr uint32_t kCmdSetModule     = 25;
constexpr uint32_t kCmdSetModuleLua  = 26;

// instruction sequence (C++ Lua brain sends batch actions)
constexpr uint32_t kCmdInstructionSeq = 60;

// ============================================================
// Instruction opcodes (inside kCmdInstructionSeq payload)
// ============================================================
constexpr uint8_t kInstNop       = 0x00;
constexpr uint8_t kInstAttack    = 0x01; // [entityId:i32]
constexpr uint8_t kInstPlace     = 0x02; // [x:i32][y:i32][z:i32][face:u8]
constexpr uint8_t kInstBreak     = 0x03; // [x:i32][y:i32][z:i32]
constexpr uint8_t kInstLookAt    = 0x04; // [x:f64][y:f64][z:f64]
constexpr uint8_t kInstSwing     = 0x05; // [hand:u8]
constexpr uint8_t kInstSetSlot   = 0x06; // [slot:u8]
constexpr uint8_t kInstSneak     = 0x07; // [state:u8]
constexpr uint8_t kInstSprint    = 0x08; // [state:u8]
constexpr uint8_t kInstVelocity  = 0x09; // [x:f32][y:f32][z:f32]
constexpr uint8_t kInstUseItem   = 0x0A;
constexpr uint8_t kInstGamma     = 0x0B; // [value:f32]
constexpr uint8_t kInstChat      = 0x0C; // [utf8text]
constexpr uint8_t kInstStopAll   = 0x0D;

// ============================================================
// Java -> C++  (events)
// ============================================================

constexpr uint32_t kEvConnected     = 100;
constexpr uint32_t kEvPong          = 101;

// game events
constexpr uint32_t kEvTick          = 110;
constexpr uint32_t kEvRender3D      = 111;
constexpr uint32_t kEvRender2D      = 112;
constexpr uint32_t kEvAttack        = 113;
constexpr uint32_t kEvDamage        = 114;
constexpr uint32_t kEvChat          = 115;
constexpr uint32_t kEvBlockUpdate   = 116;
constexpr uint32_t kEvDeath         = 117;
constexpr uint32_t kEvModuleToggle  = 118;
constexpr uint32_t kEvSettingsUpdate = 119;

// query responses
constexpr uint32_t kEvPlayerPos     = 130;
constexpr uint32_t kEvEntityList    = 131;
constexpr uint32_t kEvBlockAt       = 132;
constexpr uint32_t kEvRaytrace      = 133;
constexpr uint32_t kEvPlayerList    = 134;
constexpr uint32_t kEvInventory     = 135;

// world snapshot (Java sends full game state every tick)
constexpr uint32_t kEvWorldSnapshot = 200;

} // namespace stella
