#pragma once
// Single-producer / single-consumer ring buffer over shared memory.
// Record layout: [u32 payloadLen][u32 opcode][payload bytes...]
// Cursors are u32 monotonic counters; the data area is a power of two so
// positions map via idx & mask even after u32 wraparound.
//
// Memory ordering: payload is written before a release-store of `head`,
// and read only after an acquire-load observes enough space. Mirrors the
// VarHandle getAcquire/setRelease logic on the Java side.

#include <atomic>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

namespace stella {

struct Ring {
    std::atomic<uint32_t>* head; // producer cursor
    std::atomic<uint32_t>* tail; // consumer cursor
    uint8_t* data;
    uint32_t mask; // DATA_CAP - 1
    uint32_t kDataCap; // DATA_CAP

    static constexpr uint32_t kMeta = 16;

    // region points at the start of one ring region inside the mapping
    static Ring at(uint8_t* region, uint32_t dataCap) {
        return Ring{reinterpret_cast<std::atomic<uint32_t>*>(region + 0),
                    reinterpret_cast<std::atomic<uint32_t>*>(region + 4),
                    region + kMeta,
                    dataCap - 1,
                    dataCap};
    }

    bool write(uint32_t opcode, const void* payload, uint32_t len) {
        const uint64_t total = 8ull + len;
        if (total > mask) {
            return false;
        }
        const uint64_t h = head->load(std::memory_order_relaxed);
        const uint64_t t = tail->load(std::memory_order_acquire);
        const uint64_t used = ((h & 0xFFFFFFFF) - (t & 0xFFFFFFFF)) & 0xFFFFFFFF;
        if (used + total > static_cast<uint64_t>(mask)) {
            return false; // full
        }

        auto put32 = [&](uint64_t idx, uint32_t v) {
            uint32_t pos = static_cast<uint32_t>(idx & mask);
            std::memcpy(data + pos, &v, 4);
        };

        put32(h, len);
        put32(h + 4, opcode);

        // Bulk copy with wrap-around
        const uint32_t dataPos = static_cast<uint32_t>((h + 8) & mask);
        const uint32_t first = std::min(len, kDataCap - dataPos);
        const uint8_t* bytes = static_cast<const uint8_t*>(payload);
        std::memcpy(data + dataPos, bytes, first);
        if (first < len) {
            std::memcpy(data, bytes + first, len - first);
        }

        head->store(static_cast<uint32_t>(h + total), std::memory_order_release);
        return true;
    }

    bool write(uint32_t opcode, const std::string& text) {
        return write(opcode, text.data(), static_cast<uint32_t>(text.size()));
    }

    // Returns true and fills opcode/out when a complete record was consumed.
    bool read(uint32_t& opcode, std::vector<uint8_t>& out) {
        const uint64_t t = tail->load(std::memory_order_relaxed);
        const uint64_t h = head->load(std::memory_order_acquire);
        if (h == t) {
            return false;
        }

        auto get32 = [&](uint64_t idx) -> uint32_t {
            uint32_t pos = static_cast<uint32_t>(idx & mask);
            uint32_t v = 0;
            std::memcpy(&v, data + pos, 4);
            return v;
        };

        const uint64_t len = get32(t);
        const uint64_t total = 8ull + len;
        const uint64_t used = ((h & 0xFFFFFFFF) - (t & 0xFFFFFFFF)) & 0xFFFFFFFF;
        if (used < total) {
            return false; // not fully published yet
        }
        opcode = get32(t + 4);
        out.resize(static_cast<size_t>(len));

        // Bulk copy with wrap-around
        const uint32_t dataPos = static_cast<uint32_t>((t + 8) & mask);
        const uint32_t first = std::min(static_cast<uint32_t>(len), kDataCap - dataPos);
        std::memcpy(out.data(), data + dataPos, first);
        if (first < static_cast<uint32_t>(len)) {
            std::memcpy(out.data() + first, data, static_cast<size_t>(len) - first);
        }

        tail->store(static_cast<uint32_t>(t + total), std::memory_order_release);
        return true;
    }
};

} // namespace stella
