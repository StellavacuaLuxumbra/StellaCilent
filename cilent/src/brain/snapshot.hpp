#pragma once
// Deserializes world snapshot from Java (EV_WORLD_SNAPSHOT).
// Layout matches WorldSnapshot.java exactly.

#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

namespace stella {

struct PlayerState {
    int32_t entityId = 0;
    double x = 0, y = 0, z = 0;
    float pitch = 0, yaw = 0;
    float health = 0, armor = 0;
    bool onGround = false, sprinting = false, sneaking = false;
    float fallDistance = 0;
    float velX = 0, velY = 0, velZ = 0;
    int32_t mainHandItem = -1;
    int32_t offHandItem = -1;
    uint8_t selectedSlot = 0;
};

struct EntityState {
    int32_t entityId = 0;
    int16_t typeId = 0;
    float x = 0, y = 0, z = 0;
    float health = 0;
    float velX = 0, velY = 0, velZ = 0;
    bool isPlayer = false;
};

struct CrystalState {
    int32_t entityId = 0;
    float x = 0, y = 0, z = 0;
    uint8_t baseBlock = 0; // 1=obsidian, 2=bedrock, 0=other
};

struct BlockState {
    int32_t x = 0, y = 0, z = 0;
    int16_t blockId = 0;
    uint8_t meta = 0;
};

struct WorldSnapshot {
    int64_t worldTime = 0;
    PlayerState player;
    std::vector<EntityState> entities;
    std::vector<CrystalState> crystals;
    std::vector<BlockState> blocks;

    bool valid = false;

    // Helper: find crystal by entity id
    const CrystalState* findCrystal(int32_t id) const {
        for (auto& c : crystals) {
            if (c.entityId == id) return &c;
        }
        return nullptr;
    }

    // Helper: check if block at pos is obsidian or bedrock
    bool isBlockAt(int32_t x, int32_t y, int32_t z, int16_t targetId) const {
        for (auto& b : blocks) {
            if (b.x == x && b.y == y && b.z == z && b.blockId == targetId) return true;
        }
        return false;
    }

    // Block id constants (MC 1.21.1)
    static constexpr int16_t BLOCK_OBSIDIAN = 49;  // will be resolved at runtime
    static constexpr int16_t BLOCK_BEDROCK = 33;
};

class SnapshotDeserializer {
public:
    static WorldSnapshot deserialize(const uint8_t* data, uint32_t len) {
        WorldSnapshot snap;
        if (len < 12) return snap;

        uint32_t off = 0;

        // header
        snap.worldTime = readI64(data, off); off += 8;
        uint8_t playerCount = data[off]; off += 1;
        uint16_t entityCount = readU16(data, off); off += 2;
        uint16_t crystalCount = readU16(data, off); off += 2;
        uint16_t sectionCount = readU16(data, off); off += 2;

        // player (first player only for now)
        if (playerCount > 0 && off + 52 <= len) {
            auto& p = snap.player;
            p.entityId = readI32(data, off); off += 4;
            p.x = readF64(data, off); off += 8;
            p.y = readF64(data, off); off += 8;
            p.z = readF64(data, off); off += 8;
            p.pitch = readF32(data, off); off += 4;
            p.yaw = readF32(data, off); off += 4;
            p.health = readF32(data, off); off += 4;
            p.armor = readF32(data, off); off += 4;
            p.onGround = data[off] != 0; off += 1;
            p.sprinting = data[off] != 0; off += 1;
            p.sneaking = data[off] != 0; off += 1;
            p.fallDistance = readF32(data, off); off += 4;
            p.velX = readF32(data, off); off += 4;
            p.velY = readF32(data, off); off += 4;
            p.velZ = readF32(data, off); off += 4;
            p.mainHandItem = readI32(data, off); off += 4;
            p.offHandItem = readI32(data, off); off += 4;
            p.selectedSlot = data[off]; off += 1;
        }

        // entities
        snap.entities.reserve(entityCount);
        for (uint16_t i = 0; i < entityCount && off + 25 <= len; i++) {
            EntityState e;
            e.entityId = readI32(data, off); off += 4;
            e.typeId = readI16(data, off); off += 2;
            e.x = readF32(data, off); off += 4;
            e.y = readF32(data, off); off += 4;
            e.z = readF32(data, off); off += 4;
            e.health = readF32(data, off); off += 4;
            e.velX = readF32(data, off); off += 4;
            e.velY = readF32(data, off); off += 4;
            e.velZ = readF32(data, off); off += 4;
            e.isPlayer = data[off] != 0; off += 1;
            snap.entities.push_back(e);
        }

        // crystals
        snap.crystals.reserve(crystalCount);
        for (uint16_t i = 0; i < crystalCount && off + 16 <= len; i++) {
            CrystalState c;
            c.entityId = readI32(data, off); off += 4;
            c.x = readF32(data, off); off += 4;
            c.y = readF32(data, off); off += 4;
            c.z = readF32(data, off); off += 4;
            c.baseBlock = data[off]; off += 1;
            snap.crystals.push_back(c);
        }

        // sections (grouped block data)
        // Per section: chunkX(i16), chunkZ(i16), sectionY(i16), blockCount(u16), then blockCount * [localPos(u16) + blockId(i16)]
        for (uint16_t s = 0; s < sectionCount && off + 8 <= len; s++) {
            int16_t chunkX = readI16(data, off); off += 2;
            int16_t chunkZ = readI16(data, off); off += 2;
            int16_t sectionY = readI16(data, off); off += 2;
            uint16_t blockCount = readU16(data, off); off += 2;

            int32_t baseX = (int32_t)chunkX * 16;
            int32_t baseZ = (int32_t)chunkZ * 16;
            int32_t baseY = (int32_t)sectionY * 16;

            for (uint16_t b = 0; b < blockCount && off + 4 <= len; b++) {
                uint16_t localPos = readU16(data, off); off += 2;
                int16_t blockId = readI16(data, off); off += 2;

                int32_t lx = (localPos >> 8) & 0xF;
                int32_t lz = (localPos >> 4) & 0xF;
                int32_t ly = localPos & 0xF;

                BlockState bs;
                bs.x = baseX + lx;
                bs.y = baseY + ly;
                bs.z = baseZ + lz;
                bs.blockId = blockId;
                snap.blocks.push_back(bs);
            }
        }

        snap.valid = true;
        return snap;
    }

private:
    static uint16_t readU16(const uint8_t* p, int off) {
        return (uint16_t)p[off] | ((uint16_t)p[off + 1] << 8);
    }
    static int16_t readI16(const uint8_t* p, int off) {
        return (int16_t)readU16(p, off);
    }
    static uint32_t readU32(const uint8_t* p, int off) {
        return (uint32_t)p[off] | ((uint32_t)p[off+1] << 8) |
               ((uint32_t)p[off+2] << 16) | ((uint32_t)p[off+3] << 24);
    }
    static int32_t readI32(const uint8_t* p, int off) {
        return (int32_t)readU32(p, off);
    }
    static float readF32(const uint8_t* p, int off) {
        uint32_t bits = readU32(p, off); float v; std::memcpy(&v, &bits, 4); return v;
    }
    static double readF64(const uint8_t* p, int off) {
        uint64_t lo = 0;
        for (int i = 0; i < 8; i++) lo |= ((uint64_t)p[off + i]) << (8 * i);
        double v; std::memcpy(&v, &lo, 8); return v;
    }
    static int64_t readI64(const uint8_t* p, int off) {
        uint64_t lo = 0;
        for (int i = 0; i < 8; i++) lo |= ((uint64_t)p[off + i]) << (8 * i);
        return (int64_t)lo;
    }
};

} // namespace stella
