#pragma once
// Encodes instruction sequences to send to Java (CMD_INSTRUCTION_SEQ).
// Mirrors the InstructionDecoder opcodes in Protocol.java.

#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

namespace stella {

// Instruction opcodes (must match Protocol.java INST_* constants)
enum InstOp : uint8_t {
    INST_NOP       = 0x00,
    INST_ATTACK    = 0x01,
    INST_PLACE     = 0x02,
    INST_BREAK     = 0x03,
    INST_LOOK_AT   = 0x04,
    INST_SWING     = 0x05,
    INST_SET_SLOT  = 0x06,
    INST_SNEAK     = 0x07,
    INST_SPRINT    = 0x08,
    INST_VELOCITY  = 0x09,
    INST_USE_ITEM  = 0x0A,
    INST_GAMMA     = 0x0B,
    INST_CHAT      = 0x0C,
    INST_STOP_ALL  = 0x0D,
};

class InstructionEncoder {
public:
    void reset() { buf_.clear(); }

    void attack(int32_t entityId) {
        buf_.push_back(INST_ATTACK);
        putI32(entityId);
    }

    void place(int32_t x, int32_t y, int32_t z, uint8_t face) {
        buf_.push_back(INST_PLACE);
        putI32(x); putI32(y); putI32(z);
        buf_.push_back(face);
    }

    void breakBlock(int32_t x, int32_t y, int32_t z) {
        buf_.push_back(INST_BREAK);
        putI32(x); putI32(y); putI32(z);
    }

    void lookAt(double x, double y, double z) {
        buf_.push_back(INST_LOOK_AT);
        putF64(x); putF64(y); putF64(z);
    }

    void swing(uint8_t hand = 0) {
        buf_.push_back(INST_SWING);
        buf_.push_back(hand);
    }

    void setSlot(uint8_t slot) {
        buf_.push_back(INST_SET_SLOT);
        buf_.push_back(slot);
    }

    void sneak(bool state) {
        buf_.push_back(INST_SNEAK);
        buf_.push_back(state ? 1 : 0);
    }

    void sprint(bool state) {
        buf_.push_back(INST_SPRINT);
        buf_.push_back(state ? 1 : 0);
    }

    void velocity(float x, float y, float z) {
        buf_.push_back(INST_VELOCITY);
        putF32(x); putF32(y); putF32(z);
    }

    void useItem() {
        buf_.push_back(INST_USE_ITEM);
    }

    void gamma(float value) {
        buf_.push_back(INST_GAMMA);
        putF32(value);
    }

    void chat(const std::string& text) {
        buf_.push_back(INST_CHAT);
        buf_.push_back((uint8_t)text.size());
        buf_.insert(buf_.end(), text.begin(), text.end());
    }

    void stopAll() {
        buf_.push_back(INST_STOP_ALL);
    }

    // Build the final payload: [count:u16][instructions...]
    std::vector<uint8_t> build() const {
        std::vector<uint8_t> payload;
        uint16_t count = countInstructions();
        payload.push_back(count & 0xFF);
        payload.push_back((count >> 8) & 0xFF);
        payload.insert(payload.end(), buf_.begin(), buf_.end());
        return payload;
    }

    bool empty() const { return buf_.empty(); }

private:
    std::vector<uint8_t> buf_;

    void putI32(int32_t v) {
        buf_.push_back(v & 0xFF);
        buf_.push_back((v >> 8) & 0xFF);
        buf_.push_back((v >> 16) & 0xFF);
        buf_.push_back((v >> 24) & 0xFF);
    }

    void putF32(float v) {
        uint32_t bits;
        std::memcpy(&bits, &v, 4);
        putI32((int32_t)bits);
    }

    void putF64(double v) {
        uint64_t bits;
        std::memcpy(&bits, &v, 8);
        for (int i = 0; i < 8; i++) {
            buf_.push_back((bits >> (8 * i)) & 0xFF);
        }
    }

    uint16_t countInstructions() const {
        uint16_t count = 0;
        uint32_t i = 0;
        while (i < buf_.size()) {
            uint8_t op = buf_[i]; i++;
            count++;
            switch (op) {
                case INST_NOP: break;
                case INST_ATTACK: i += 4; break;
                case INST_PLACE: i += 13; break;
                case INST_BREAK: i += 12; break;
                case INST_LOOK_AT: i += 24; break;
                case INST_SWING: i += 1; break;
                case INST_SET_SLOT: i += 1; break;
                case INST_SNEAK: i += 1; break;
                case INST_SPRINT: i += 1; break;
                case INST_VELOCITY: i += 12; break;
                case INST_USE_ITEM: break;
                case INST_GAMMA: i += 4; break;
                case INST_CHAT: {
                    if (i < buf_.size()) {
                        uint8_t len = buf_[i]; i += 1 + len;
                    }
                    break;
                }
                case INST_STOP_ALL: break;
                default: break;
            }
        }
        return count;
    }
};

} // namespace stella
