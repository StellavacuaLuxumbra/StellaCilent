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

    static constexpr uint32_t kMeta = 16;

    // region points at the start of one ring region inside the mapping
    static Ring at(uint8_t* region, uint32_t dataCap) {
        return Ring{reinterpret_cast<std::atomic<uint32_t>*>(region + 0),
                    reinterpret_cast<std::atomic<uint32_t>*>(region + 4),
                    region + kMeta,
                    dataCap - 1};
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
            for (int i = 0; i < 4; ++i) {
                data[(idx + i) & mask] = static_cast<uint8_t>(v >> (8 * i));
            }
        };

        put32(h, len);
        put32(h + 4, opcode);
        const uint8_t* bytes = static_cast<const uint8_t*>(payload);
        for (uint32_t i = 0; i < len; ++i) {
            data[(h + 8 + i) & mask] = bytes[i];
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

        auto get32 = [&](uint64_t idx) {
            uint32_t v = 0;
            for (int i = 0; i < 4; ++i) {
                v |= static_cast<uint32_t>(data[(idx + i) & mask]) << (8 * i);
            }
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
        for (uint64_t i = 0; i < len; ++i) {
            out[static_cast<size_t>(i)] = data[(t + 8 + i) & mask];
        }

        tail->store(static_cast<uint32_t>(t + total), std::memory_order_release);
        return true;
    }
};

} // namespace stella
