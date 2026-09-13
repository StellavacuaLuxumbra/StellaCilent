package dev.stella.executer.ipc;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkSection;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Serializes the full game state into a binary snapshot.
 * Uses chunk section iteration + filler block filtering for speed.
 *
 * New compact block encoding (section-grouped):
 *   [BLOCKS HEADER]
 *     sectionCount:  u16
 *   Per section:
 *     chunkX:  i16
 *     sectionY: i16
 *     blockCount: u16
 *   Per block in section:
 *     localPos:  u16  (x<<8 | z<<4 | y)  (each 0-15)
 *     blockId:   i16
 */
public final class WorldSnapshot {
    private WorldSnapshot() {}

    // Filler blocks to skip (stone, dirt, etc.) — massive data reduction
    private static final Set<Integer> FILLER_BLOCKS = new HashSet<>();
    static {
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.STONE));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.DEEPSLATE));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.DIRT));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.GRASS_BLOCK));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.COARSE_DIRT));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.PODZOL));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.NETHERRACK));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.END_STONE));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.SAND));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.RED_SAND));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.GRAVEL));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.TERRACOTTA));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.ANDESITE));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.DIORITE));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.GRANITE));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.TUFF));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.DRIPSTONE_BLOCK));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.CALCITE));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.SMOOTH_STONE));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.COBBLESTONE));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.MOSSY_COBBLESTONE));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.SMOOTH_STONE_SLAB));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.STONE_SLAB));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.STONE_BRICKS));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.CRACKED_STONE_BRICKS));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.MOSSY_STONE_BRICKS));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.PACKED_MUD));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.MUD_BRICKS));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.PACKED_ICE));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.SNOW_BLOCK));
        FILLER_BLOCKS.add(Registries.BLOCK.getRawId(Blocks.POWDER_SNOW));
    }

    private static boolean isFiller(int rawId) {
        return FILLER_BLOCKS.contains(rawId);
    }

    public static byte[] serialize(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return new byte[0];

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(131072);

            // --- dynamic range from render distance ---
            int renderChunks = mc.options.getClampedViewDistance();
            int blockRadiusXZ = renderChunks * 16;
            int blockRadiusY = 32; // ±32 blocks vertical (2 sections up, 2 down + partial)

            float scanRange = Math.max(blockRadiusXZ, 64.0f);
            Box scanBox = mc.player.getBoundingBox().expand(scanRange);

            List<LivingEntity> entities = mc.world.getEntitiesByClass(LivingEntity.class, scanBox,
                    e -> e != mc.player && e.isAlive() && !e.isRemoved());

            List<EndCrystalEntity> crystals = mc.world.getEntitiesByClass(EndCrystalEntity.class, scanBox,
                    e -> e.isAlive() && !e.isRemoved());

            // --- chunk-section-based block scan ---
            BlockPos playerPos = mc.player.getBlockPos();
            int playerChunkX = playerPos.getX() >> 4;
            int playerChunkZ = playerPos.getZ() >> 4;
            int chunkRadiusXZ = (blockRadiusXZ + 15) >> 4;

            ByteArrayOutputStream sectionBuf = new ByteArrayOutputStream(131072);
            int sectionCount = 0;

            for (int cx = -chunkRadiusXZ; cx <= chunkRadiusXZ; cx++) {
                for (int cz = -chunkRadiusXZ; cz <= chunkRadiusXZ; cz++) {
                    WorldChunk chunk = mc.world.getChunk(playerChunkX + cx, playerChunkZ + cz);
                    ChunkSection[] sections = chunk.getSectionArray();
                    int chunkBaseY = chunk.getBottomSectionCoord();
                    int chunkWorldX = chunk.getPos().x * 16;
                    int chunkWorldZ = chunk.getPos().z * 16;

                    for (int si = 0; si < sections.length; si++) {
                        ChunkSection section = sections[si];
                        if (section.isEmpty()) continue;

                        int sectionWorldY = (chunkBaseY + si) * 16;
                        int secTop = sectionWorldY + 16;
                        int secBot = sectionWorldY;
                        int scanTop = playerPos.getY() + blockRadiusY;
                        int scanBot = playerPos.getY() - blockRadiusY;
                        if (secBot > scanTop || secTop < scanBot) continue;

                        // Collect non-air, non-filler blocks for this section
                        ByteArrayOutputStream blockBuf = new ByteArrayOutputStream(512);
                        int blockInSection = 0;

                        for (int lx = 0; lx < 16; lx++) {
                            int bx = chunkWorldX + lx;
                            if (Math.abs(bx - playerPos.getX()) > blockRadiusXZ) continue;

                            for (int lz = 0; lz < 16; lz++) {
                                int bz = chunkWorldZ + lz;
                                if (Math.abs(bz - playerPos.getZ()) > blockRadiusXZ) continue;

                                int minLocalY = 0, maxLocalY = 16;
                                if (secBot < scanBot) minLocalY = Math.max(0, scanBot - sectionWorldY);
                                if (secTop > scanTop) maxLocalY = Math.min(16, scanTop - sectionWorldY);

                                for (int ly = minLocalY; ly < maxLocalY; ly++) {
                                    BlockState state = section.getBlockState(lx, ly, lz);
                                    if (state.isAir()) continue;
                                    int rawId = Registries.BLOCK.getRawId(state.getBlock());
                                    if (isFiller(rawId)) continue;

                                    // Compact: localPos = (x<<8 | z<<4 | y), blockId = i16
                                    int localPos = (lx << 8) | (lz << 4) | ly;
                                    writeShort(blockBuf, localPos);
                                    writeShort(blockBuf, rawId);
                                    blockInSection++;
                                }
                            }
                        }

                        if (blockInSection > 0) {
                            // Section header: chunkX(i16) + chunkZ(i16) + sectionY(i16) + blockCount(u16)
                            writeShort(sectionBuf, chunk.getPos().x);
                            writeShort(sectionBuf, chunk.getPos().z);
                            writeShort(sectionBuf, chunkBaseY + si);
                            writeShort(sectionBuf, blockInSection);
                            sectionBuf.write(blockBuf.toByteArray(), 0, blockBuf.size());
                            sectionCount++;
                        }
                    }
                }
            }

            // --- header ---
            writeLong(baos, mc.world.getTime());
            baos.write(1); // playerCount
            writeShort(baos, entities.size());
            writeShort(baos, crystals.size());
            writeShort(baos, sectionCount);

            // --- player ---
            writeInt(baos, mc.player.getId());
            writeDouble(baos, mc.player.getX());
            writeDouble(baos, mc.player.getY());
            writeDouble(baos, mc.player.getZ());
            writeFloat(baos, mc.player.getPitch());
            writeFloat(baos, mc.player.getYaw());
            writeFloat(baos, mc.player.getHealth());
            writeFloat(baos, mc.player.getArmor());
            baos.write(mc.player.isOnGround() ? 1 : 0);
            baos.write(mc.player.isSprinting() ? 1 : 0);
            baos.write(mc.player.isSneaking() ? 1 : 0);
            writeFloat(baos, mc.player.fallDistance);
            writeFloat(baos, (float) mc.player.getVelocity().x);
            writeFloat(baos, (float) mc.player.getVelocity().y);
            writeFloat(baos, (float) mc.player.getVelocity().z);
            writeInt(baos, mc.player.getMainHandStack().isEmpty() ? -1 :
                    Registries.ITEM.getRawId(mc.player.getMainHandStack().getItem()));
            writeInt(baos, mc.player.getOffHandStack().isEmpty() ? -1 :
                    Registries.ITEM.getRawId(mc.player.getOffHandStack().getItem()));
            baos.write(mc.player.getInventory().selectedSlot);

            // --- entities ---
            for (LivingEntity e : entities) {
                writeInt(baos, e.getId());
                writeShort(baos, Registries.ENTITY_TYPE.getRawId(e.getType()));
                writeFloat(baos, (float) e.getX());
                writeFloat(baos, (float) e.getY());
                writeFloat(baos, (float) e.getZ());
                writeFloat(baos, e.getHealth());
                writeFloat(baos, (float) e.getVelocity().x);
                writeFloat(baos, (float) e.getVelocity().y);
                writeFloat(baos, (float) e.getVelocity().z);
                baos.write(e instanceof PlayerEntity ? 1 : 0);
            }

            // --- crystals ---
            for (EndCrystalEntity c : crystals) {
                writeInt(baos, c.getId());
                writeFloat(baos, (float) c.getX());
                writeFloat(baos, (float) c.getY());
                writeFloat(baos, (float) c.getZ());
                BlockPos cb = c.getBlockPos();
                var baseState = mc.world.getBlockState(cb.down());
                byte baseType = 0;
                if (baseState.getBlock() == Blocks.OBSIDIAN) baseType = 1;
                else if (baseState.getBlock() == Blocks.BEDROCK) baseType = 2;
                baos.write(baseType);
            }

            // --- sections (grouped block data) ---
            sectionBuf.writeTo(baos);

            return baos.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private static void writeInt(ByteArrayOutputStream s, int v) {
        s.write(v); s.write(v >>> 8); s.write(v >>> 16); s.write(v >>> 24);
    }
    private static void writeLong(ByteArrayOutputStream s, long v) {
        for (int i = 0; i < 8; i++) s.write((int) (v >>> (8 * i)));
    }
    private static void writeFloat(ByteArrayOutputStream s, float v) {
        writeInt(s, Float.floatToRawIntBits(v));
    }
    private static void writeDouble(ByteArrayOutputStream s, double v) {
        writeLong(s, Double.doubleToRawLongBits(v));
    }
    private static void writeShort(ByteArrayOutputStream s, int v) {
        s.write(v); s.write(v >>> 8);
    }
}
