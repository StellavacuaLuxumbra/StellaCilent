package dev.stella.ipc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Decodes and executes instruction sequences sent from the C++ Lua brain.
 * Each instruction is: [opcode:u8] [operands...]
 */
public final class InstructionDecoder {
    private InstructionDecoder() {}

    public static void execute(byte[] payload) {
        if (payload.length < 2) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        int count = (payload[0] & 0xFF) | ((payload[1] & 0xFF) << 8);
        int offset = 2;

        for (int i = 0; i < count && offset < payload.length; i++) {
            offset = executeOne(payload, offset, mc);
        }
    }

    private static int executeOne(byte[] p, int off, MinecraftClient mc) {
        if (off >= p.length) return off;
        int opcode = p[off] & 0xFF;
        off++;

        switch (opcode) {
            case Protocol.INST_NOP -> {}

            case Protocol.INST_ATTACK -> {
                if (off + 4 > p.length) return p.length;
                int entityId = readInt(p, off); off += 4;
                Entity target = mc.world.getEntityById(entityId);
                if (target != null) {
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            }

            case Protocol.INST_PLACE -> {
                if (off + 13 > p.length) return p.length;
                int x = readInt(p, off); off += 4;
                int y = readInt(p, off); off += 4;
                int z = readInt(p, off); off += 4;
                int face = p[off] & 0xFF; off++;
                BlockPos pos = new BlockPos(x, y, z);
                Direction dir = Direction.values()[MathHelper_clamp(face, 0, 5)];
                Vec3d hitVec = Vec3d.ofCenter(pos).add(
                        dir.getUnitVector().mul(0.5f).x(),
                        dir.getUnitVector().mul(0.5f).y(),
                        dir.getUnitVector().mul(0.5f).z());
                BlockHitResult hit = new BlockHitResult(hitVec, dir, pos, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
            }

            case Protocol.INST_BREAK -> {
                if (off + 12 > p.length) return p.length;
                int x = readInt(p, off); off += 4;
                int y = readInt(p, off); off += 4;
                int z = readInt(p, off); off += 4;
                mc.interactionManager.attackBlock(new BlockPos(x, y, z), Direction.UP);
            }

            case Protocol.INST_LOOK_AT -> {
                if (off + 24 > p.length) return p.length;
                double tx = readDouble(p, off); off += 8;
                double ty = readDouble(p, off); off += 8;
                double tz = readDouble(p, off); off += 8;
                Vec3d eye = mc.player.getEyePos();
                double dx = tx - eye.x;
                double dy = ty - eye.y;
                double dz = tz - eye.z;
                mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
                mc.player.setPitch((float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
            }

            case Protocol.INST_SWING -> {
                if (off + 1 > p.length) return p.length;
                int hand = p[off] & 0xFF; off++;
                mc.player.swingHand(hand == 1 ? Hand.OFF_HAND : Hand.MAIN_HAND);
            }

            case Protocol.INST_SET_SLOT -> {
                if (off + 1 > p.length) return p.length;
                int slot = p[off] & 0xFF; off++;
                mc.player.getInventory().selectedSlot = MathHelper_clamp(slot, 0, 8);
            }

            case Protocol.INST_SNEAK -> {
                if (off + 1 > p.length) return p.length;
                mc.player.setSneaking(p[off] != 0); off++;
            }

            case Protocol.INST_SPRINT -> {
                if (off + 1 > p.length) return p.length;
                mc.player.setSprinting(p[off] != 0); off++;
            }

            case Protocol.INST_VELOCITY -> {
                if (off + 12 > p.length) return p.length;
                float vx = readFloat(p, off); off += 4;
                float vy = readFloat(p, off); off += 4;
                float vz = readFloat(p, off); off += 4;
                mc.player.setVelocity(vx, vy, vz);
            }

            case Protocol.INST_USE_ITEM -> {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
            }

            case Protocol.INST_GAMMA -> {
                if (off + 4 > p.length) return p.length;
                float val = readFloat(p, off); off += 4;
                mc.options.getGamma().setValue(Math.max(0.0, Math.min((double) val, 1.0)));
            }

            case Protocol.INST_CHAT -> {
                if (off >= p.length) return p.length;
                int len = p[off] & 0xFF; off++;
                if (off + len > p.length) return p.length;
                String text = new String(p, off, len, StandardCharsets.UTF_8);
                off += len;
                mc.player.networkHandler.sendPacket(
                        new ChatMessageC2SPacket(text, Instant.now(), 0L, null, null));
            }

            case Protocol.INST_STOP_ALL -> {
                mc.player.setSneaking(false);
                mc.player.setSprinting(false);
            }

            default -> {}
        }
        return off;
    }

    // --- helpers ---
    private static int readInt(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }
    private static float readFloat(byte[] b, int off) {
        return Float.intBitsToFloat(readInt(b, off));
    }
    private static double readDouble(byte[] b, int off) {
        long lo = 0;
        for (int i = 0; i < 8; i++) lo |= ((long) (b[off + i] & 0xFF)) << (8 * i);
        return Double.longBitsToDouble(lo);
    }
    private static int MathHelper_clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}
