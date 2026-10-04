package dev.stella.brain;

import org.luaj.vm2.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Parse binary WorldSnapshot → Lua tables for LuaJ VM.
 * Decodes the section-grouped format encoded by WorldSnapshot.java.
 */
public final class SnapshotParser {
    private SnapshotParser() {}

    /**
     * Parse binary snapshot data into a Lua table.
     * Format:
     * [worldTime:i64][playerCount:u8][entityCount:u16][crystalCount:u16][sectionCount:u16]
     * [player data...][entity data...][crystal data...]
     * [section headers + blocks...]
     */
    public static LuaTable parse(byte[] data) {
        if (data == null || data.length < 11) return new LuaTable();
        
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        LuaTable root = new LuaTable();
        
        try {
            long worldTime = buf.getLong();
            int playerCount = buf.get() & 0xFF;
            int entityCount = buf.getShort() & 0xFFFF;
            int crystalCount = buf.getShort() & 0xFFFF;
            int sectionCount = buf.getShort() & 0xFFFF;
            
            root.set("worldTime", LuaValue.valueOf(worldTime));
            root.set("playerCount", LuaValue.valueOf(playerCount));
            root.set("entityCount", LuaValue.valueOf(entityCount));
            root.set("crystalCount", LuaValue.valueOf(crystalCount));
            root.set("sectionCount", LuaValue.valueOf(sectionCount));
            
            LuaTable playersTable = new LuaTable();
            for (int i = 0; i < playerCount; i++) {
                if (buf.remaining() < 47) break;
                
                LuaTable player = new LuaTable();
                int entityId = buf.getInt();
                int posX = buf.getInt();
                int posY = buf.getInt();
                int posZ = buf.getInt();
                int motionX = buf.getInt();
                int motionY = buf.getInt();
                int motionZ = buf.getInt();
                float health = buf.getFloat();
                int armor = buf.getInt();
                byte handItem = buf.get();
                int metadata = buf.getInt();
                
                player.set("entityId", LuaValue.valueOf(entityId));
                player.set("posX", LuaValue.valueOf(posX / 32.0));
                player.set("posY", LuaValue.valueOf(posY / 32.0));
                player.set("posZ", LuaValue.valueOf(posZ / 32.0));
                player.set("motionX", LuaValue.valueOf(motionX / 100.0));
                player.set("motionY", LuaValue.valueOf(motionY / 100.0));
                player.set("motionZ", LuaValue.valueOf(motionZ / 100.0));
                player.set("health", LuaValue.valueOf(health));
                player.set("armor", LuaValue.valueOf(armor));
                player.set("handItem", LuaValue.valueOf(handItem));
                player.set("metadata", LuaValue.valueOf(metadata));
                
                playersTable.set(i + 1, player);
            }
            root.set("players", playersTable);
            
            LuaTable entitiesTable = new LuaTable();
            for (int i = 0; i < entityCount; i++) {
                if (buf.remaining() < 30) break;
                
                LuaTable entity = new LuaTable();
                byte type = buf.get();
                int entityId = buf.getInt();
                int posX = buf.getInt();
                int posY = buf.getInt();
                int posZ = buf.getInt();
                int motionX = buf.getInt();
                int motionY = buf.getInt();
                int motionZ = buf.getInt();
                float health = buf.getFloat();
                
                entity.set("type", LuaValue.valueOf(type));
                entity.set("entityId", LuaValue.valueOf(entityId));
                entity.set("posX", LuaValue.valueOf(posX / 32.0));
                entity.set("posY", LuaValue.valueOf(posY / 32.0));
                entity.set("posZ", LuaValue.valueOf(posZ / 32.0));
                entity.set("motionX", LuaValue.valueOf(motionX / 100.0));
                entity.set("motionY", LuaValue.valueOf(motionY / 100.0));
                entity.set("motionZ", LuaValue.valueOf(motionZ / 100.0));
                entity.set("health", LuaValue.valueOf(health));
                
                entitiesTable.set(i + 1, entity);
            }
            root.set("entities", entitiesTable);
            
            LuaTable crystalsTable = new LuaTable();
            for (int i = 0; i < crystalCount; i++) {
                if (buf.remaining() < 24) break;
                
                LuaTable crystal = new LuaTable();
                int posX = buf.getInt();
                int posY = buf.getInt();
                int posZ = buf.getInt();
                float health = buf.getFloat();
                int showBottom = buf.getInt();
                int age = buf.getInt();
                
                crystal.set("posX", LuaValue.valueOf(posX / 32.0));
                crystal.set("posY", LuaValue.valueOf(posY / 32.0));
                crystal.set("posZ", LuaValue.valueOf(posZ / 32.0));
                crystal.set("health", LuaValue.valueOf(health));
                crystal.set("showBottom", LuaValue.valueOf(showBottom));
                crystal.set("age", LuaValue.valueOf(age));
                
                crystalsTable.set(i + 1, crystal);
            }
            root.set("crystals", crystalsTable);
            
            LuaTable blocksTable = new LuaTable();
            int blockIndex = 0;
            
            for (int s = 0; s < sectionCount; s++) {
                if (buf.remaining() < 7) break;
                
                int chunkX = buf.getShort() & 0xFFFF;
                int chunkZ = buf.getShort() & 0xFFFF;
                int sectionY = buf.getShort() & 0xFFFF;
                int blockCount = buf.getShort() & 0xFFFF;
                
                for (int b = 0; b < blockCount; b++) {
                    if (buf.remaining() < 4) break;
                    
                    int localPos = buf.getShort() & 0xFFFF;
                    int blockId = buf.getShort();
                    
                    int localX = (localPos >> 0) & 0xF;
                    int localY = (localPos >> 4) & 0xF;
                    int localZ = (localPos >> 8) & 0xF;
                    
                    int absoluteX = chunkX * 16 + localX;
                    int absoluteY = sectionY * 16 + localY;
                    int absoluteZ = chunkZ * 16 + localZ;
                    
                    LuaTable block = new LuaTable();
                    block.set("x", LuaValue.valueOf(absoluteX));
                    block.set("y", LuaValue.valueOf(absoluteY));
                    block.set("z", LuaValue.valueOf(absoluteZ));
                    block.set("id", LuaValue.valueOf(blockId));
                    
                    blocksTable.set(++blockIndex, block);
                }
            }
            root.set("blocks", blocksTable);
            
        } catch (Exception e) {
            System.err.println("[SnapshotParser] Parse error: " + e.getMessage());
        }
        
        return root;
    }
}
