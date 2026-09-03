package dev.stella.executer;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.stella.executer.gui.ClickGuiScreen;
import dev.stella.executer.ipc.IpcHost;
import dev.stella.executer.ipc.Protocol;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class StellaExecuter implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Stella");
    private static IpcHost ipc;
    private static final Queue<byte[]> drawQueue = new ConcurrentLinkedQueue<>();

    private static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.stella.clickgui",
                GLFW.GLFW_KEY_G,
                "category.stella"
        ));

        try {
            ipc = new IpcHost();
            ipc.start(StellaExecuter::dispatch);
            LOGGER.info("IPC host ready");
        } catch (Exception e) {
            LOGGER.error("failed to start IPC host", e);
            return;
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // key bind check
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(ClickGuiScreen.getInstance());
                } else if (client.currentScreen instanceof ClickGuiScreen) {
                    client.currentScreen.close();
                }
            }

            // run module tick logic (independent of C++)
            if (client.world != null && client.player != null) {
                dev.stella.executer.modules.ModuleTicker.getInstance().tick(client);
            }

            if (client.world != null) {
                byte[] p = new byte[8];
                writeLong(p, 0, client.world.getTime());
                ipc.send(Protocol.EV_TICK, p);
            }
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            Vec3d cam = mc.gameRenderer.getCamera().getPos();
            float pitch = mc.gameRenderer.getCamera().getPitch();
            float yaw = mc.gameRenderer.getCamera().getYaw();
            float partial = mc.getRenderTickCounter().getTickDelta(false);

            byte[] p = new byte[36];
            writeDouble(p, 0, cam.x);
            writeDouble(p, 8, cam.y);
            writeDouble(p, 16, cam.z);
            writeFloat(p, 24, pitch);
            writeFloat(p, 28, yaw);
            writeFloat(p, 32, partial);
            ipc.send(Protocol.EV_RENDER_3D, p);
            executeDraw3D(context.matrixStack(), context.consumers());
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            int w = mc.getWindow().getScaledWidth();
            int h = mc.getWindow().getScaledHeight();
            byte[] p = new byte[12];
            writeInt(p, 0, w);
            writeInt(p, 4, h);
            writeFloat(p, 8, tickCounter.getTickDelta(false));
            ipc.send(Protocol.EV_RENDER_2D, p);
            dev.stella.executer.gui.hud.HudManager.getInstance().renderAll(drawContext, tickCounter.getTickDelta(false));
        });

        // chat command interception
        ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
            if (dev.stella.executer.command.CommandManager.getInstance().process(message)) {
                return false; // cancel the message
            }
            return true; // pass through
        });

        LOGGER.info("Stella driver initialized");
    }

    // ============================================================
    // IPC dispatch
    // ============================================================

    private static void dispatch(int opcode, byte[] payload) {
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (opcode) {
            case Protocol.OP_CONNECT -> {
                LOGGER.info("[IPC] native connected");
                ipc.resetTx();
                ipc.send(Protocol.EV_CONNECTED, new byte[0]);
            }

            // rendering: buffer draw commands
            case Protocol.CMD_DRAW_LINE, Protocol.CMD_DRAW_BOX,
                 Protocol.CMD_DRAW_TEXT, Protocol.CMD_DRAW_RECT,
                 Protocol.CMD_DRAW_RECT_F -> drawQueue.add(payload);
            case Protocol.CMD_CLEAR_RENDER -> drawQueue.clear();

            // actions
            case Protocol.CMD_SWING_HAND -> {
                if (mc.player != null) {
                    Hand hand = (payload.length > 0 && payload[0] == 1) ? Hand.OFF_HAND : Hand.MAIN_HAND;
                    mc.player.swingHand(hand);
                }
            }
            case Protocol.CMD_ATTACK -> {
                if (mc.player != null && mc.world != null && payload.length >= 4) {
                    int entityId = readInt(payload, 0);
                    Entity target = mc.world.getEntityById(entityId);
                    if (target != null) {
                        mc.interactionManager.attackEntity(mc.player, target);
                        mc.player.swingHand(Hand.MAIN_HAND);
                        ipc.send(Protocol.EV_ATTACK, payload);
                    }
                }
            }
            case Protocol.CMD_PLACE_BLOCK -> {
                if (mc.player != null && mc.interactionManager != null && payload.length >= 13) {
                    int x = readInt(payload, 0);
                    int y = readInt(payload, 4);
                    int z = readInt(payload, 8);
                    int face = payload[12] & 0xFF;
                    BlockPos pos = new BlockPos(x, y, z);
                    Direction dir = Direction.values()[MathHelper.clamp(face, 0, 5)];
                    BlockHitResult hit = new BlockHitResult(
                            Vec3d.ofCenter(pos).add(dir.getUnitVector().mul(0.5f).x(), dir.getUnitVector().mul(0.5f).y(), dir.getUnitVector().mul(0.5f).z()),
                            dir, pos, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            }
            case Protocol.CMD_BREAK_BLOCK -> {
                if (mc.interactionManager != null && payload.length >= 12) {
                    int x = readInt(payload, 0);
                    int y = readInt(payload, 4);
                    int z = readInt(payload, 8);
                    mc.interactionManager.attackBlock(new BlockPos(x, y, z), Direction.UP);
                }
            }
            case Protocol.CMD_SET_SLOT -> {
                if (mc.player != null && payload.length >= 1) {
                    mc.player.getInventory().selectedSlot = MathHelper.clamp(payload[0] & 0xFF, 0, 8);
                }
            }
            case Protocol.CMD_USE_ITEM -> {
                if (mc.interactionManager != null) {
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            }
            case Protocol.CMD_SNEAK -> {
                if (mc.player != null && payload.length >= 1) mc.player.setSneaking(payload[0] != 0);
            }
            case Protocol.CMD_SPRINT -> {
                if (mc.player != null && payload.length >= 1) mc.player.setSprinting(payload[0] != 0);
            }
            case Protocol.CMD_SET_VELOCITY -> {
                if (mc.player != null && payload.length >= 12) {
                    mc.player.setVelocity(readFloat(payload, 0), readFloat(payload, 4), readFloat(payload, 8));
                }
            }
            case Protocol.CMD_SEND_CHAT -> {
                if (mc.player != null && payload.length > 0) {
                    String text = new String(payload, StandardCharsets.UTF_8);
                    mc.player.networkHandler.sendPacket(new ChatMessageC2SPacket(text, Instant.now(), 0L, null, null));
                }
            }
            case Protocol.CMD_LOOK_AT -> {
                if (mc.player != null && payload.length >= 24) {
                    double tx = readDouble(payload, 0);
                    double ty = readDouble(payload, 8);
                    double tz = readDouble(payload, 16);
                    Vec3d eye = mc.player.getEyePos();
                    double dx = tx - eye.x, dy = ty - eye.y, dz = tz - eye.z;
                    mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
                    mc.player.setPitch((float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx*dx + dz*dz))));
                }
            }
            case Protocol.CMD_MOVE_TO -> {
                if (mc.player != null && payload.length >= 24) {
                    mc.player.setPosition(readDouble(payload, 0), readDouble(payload, 8), readDouble(payload, 16));
                }
            }
            case Protocol.CMD_SET_GAMMA -> {
                if (mc.options != null && payload.length >= 4) {
                    mc.options.getGamma().setValue((double) readFloat(payload, 0));
                }
            }
            case Protocol.CMD_STOP_ALL -> {
                if (mc.player != null) {
                    mc.player.setSneaking(false);
                    mc.player.setSprinting(false);
                }
                drawQueue.clear();
            }
            case Protocol.CMD_SET_MODULE -> {
                if (payload.length >= 2) {
                    int moduleId = payload[0] & 0xFF;
                    boolean enabled = payload[1] != 0;
                    var modules = dev.stella.executer.gui.ModuleManager.getInstance().getModules();
                    for (var mod : modules) {
                        if (mod.getModuleId() == moduleId) {
                            mod.setEnabled(enabled);
                            break;
                        }
                    }
                }
            }

            // queries
            case Protocol.CMD_GET_PLAYER_POS -> {
                if (mc.player != null) {
                    byte[] r = new byte[33];
                    Vec3d pos = mc.player.getPos();
                    writeDouble(r, 0, pos.x);
                    writeDouble(r, 8, pos.y);
                    writeDouble(r, 16, pos.z);
                    writeFloat(r, 24, mc.player.getPitch());
                    writeFloat(r, 28, mc.player.getYaw());
                    r[32] = mc.player.isOnGround() ? (byte) 1 : 0;
                    ipc.send(Protocol.EV_PLAYER_POS, r);
                }
            }
            case Protocol.CMD_GET_ENTITIES -> {
                if (mc.world != null && mc.player != null && payload.length >= 4) {
                    float range = readFloat(payload, 0);
                    Box box = mc.player.getBoundingBox().expand(range, range, range);
                    var entities = mc.world.getEntitiesByClass(LivingEntity.class, box,
                            e -> e != mc.player && e.isAlive() && !e.isRemoved());
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    baos.write(entities.size());
                    for (LivingEntity e : entities) {
                        writeIntTo(baos, e.getId());
                        writeIntTo(baos, Registries.ENTITY_TYPE.getRawId(e.getType()));
                        writeFloatTo(baos, (float) e.getX());
                        writeFloatTo(baos, (float) e.getY());
                        writeFloatTo(baos, (float) e.getZ());
                        writeFloatTo(baos, e.getHealth());
                        writeIntTo(baos, e instanceof PlayerEntity ? 1 : 0);
                    }
                    ipc.send(Protocol.EV_ENTITY_LIST, baos.toByteArray());
                }
            }
            case Protocol.CMD_GET_BLOCK_AT -> {
                if (mc.world != null && payload.length >= 12) {
                    int x = readInt(payload, 0), y = readInt(payload, 4), z = readInt(payload, 8);
                    var state = mc.world.getBlockState(new BlockPos(x, y, z));
                    byte[] r = new byte[5];
                    writeInt(r, 0, Registries.BLOCK.getRawId(state.getBlock()));
                    r[4] = 0;
                    ipc.send(Protocol.EV_BLOCK_AT, r);
                }
            }
            case Protocol.CMD_GET_RAYTRACE -> {
                if (mc.player != null) {
                    HitResult hit = mc.player.raycast(100.0, 1.0f, false);
                    byte[] r = new byte[18];
                    if (hit.getType() == HitResult.Type.BLOCK) {
                        BlockHitResult bhr = (BlockHitResult) hit;
                        r[0] = 1;
                        writeInt(r, 1, bhr.getBlockPos().getX());
                        writeInt(r, 5, bhr.getBlockPos().getY());
                        writeInt(r, 9, bhr.getBlockPos().getZ());
                        r[13] = (byte) bhr.getSide().ordinal();
                        writeInt(r, 14, -1);
                    } else if (hit.getType() == HitResult.Type.ENTITY) {
                        EntityHitResult ehr = (EntityHitResult) hit;
                        r[0] = 1;
                        writeInt(r, 1, (int) ehr.getEntity().getX());
                        writeInt(r, 5, (int) ehr.getEntity().getY());
                        writeInt(r, 9, (int) ehr.getEntity().getZ());
                        r[13] = 0;
                        writeInt(r, 14, ehr.getEntity().getId());
                    }
                    ipc.send(Protocol.EV_RAYTRACE, r);
                }
            }
            case Protocol.CMD_GET_PLAYERS -> {
                if (mc.world != null && mc.player != null && payload.length >= 4) {
                    float range = readFloat(payload, 0);
                    Box box = mc.player.getBoundingBox().expand(range, range, range);
                    var players = mc.world.getEntitiesByClass(PlayerEntity.class, box,
                            e -> e != mc.player && e.isAlive());
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    baos.write(players.size());
                    for (PlayerEntity p : players) {
                        writeIntTo(baos, p.getId());
                        byte[] nb = p.getName().getString().getBytes(StandardCharsets.UTF_8);
                        baos.write(nb.length);
                        baos.write(nb, 0, nb.length);
                        writeFloatTo(baos, (float) p.getX());
                        writeFloatTo(baos, (float) p.getY());
                        writeFloatTo(baos, (float) p.getZ());
                    }
                    ipc.send(Protocol.EV_PLAYER_LIST, baos.toByteArray());
                }
            }

            default -> LOGGER.warn("unknown opcode {} ({} bytes)", opcode, payload.length);
        }
    }

    // ============================================================
    // 3D rendering
    // ============================================================

    private static void executeDraw3D(MatrixStack matrices, VertexConsumerProvider vcp) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        while (!drawQueue.isEmpty()) {
            byte[] cmd = drawQueue.poll();
            if (cmd == null || cmd.length < 1) continue;
            int type = cmd[0] & 0xFF;
            if (type == Protocol.CMD_DRAW_LINE && cmd.length >= 29) {
                float x1 = readFloat(cmd, 1) - (float) cam.x;
                float y1 = readFloat(cmd, 5) - (float) cam.y;
                float z1 = readFloat(cmd, 9) - (float) cam.z;
                float x2 = readFloat(cmd, 13) - (float) cam.x;
                float y2 = readFloat(cmd, 17) - (float) cam.y;
                float z2 = readFloat(cmd, 21) - (float) cam.z;
                int c = readInt(cmd, 25);
                VertexConsumer vc = vcp.getBuffer(RenderLayer.LINES);
                matrices.push();
                var m = matrices.peek().getPositionMatrix();
                vc.vertex(m, x1, y1, z1).color((c>>16)&0xFF,(c>>8)&0xFF,c&0xFF,(c>>24)&0xFF);
                vc.vertex(m, x2, y2, z2).color((c>>16)&0xFF,(c>>8)&0xFF,c&0xFF,(c>>24)&0xFF);
                matrices.pop();
            } else if (type == Protocol.CMD_DRAW_BOX && cmd.length >= 29) {
                float x = readFloat(cmd, 1) - (float) cam.x;
                float y = readFloat(cmd, 5) - (float) cam.y;
                float z = readFloat(cmd, 9) - (float) cam.z;
                float w = readFloat(cmd, 13), h = readFloat(cmd, 17), d = readFloat(cmd, 21);
                int c = readInt(cmd, 25);
                drawBox(matrices, vcp, x, y, z, w, h, d, (c>>16)&0xFF,(c>>8)&0xFF,c&0xFF,(c>>24)&0xFF);
            }
        }
    }

    private static void drawBox(MatrixStack ms, VertexConsumerProvider vcp,
                                 float x, float y, float z, float w, float h, float d,
                                 int r, int g, int b, int a) {
        VertexConsumer vc = vcp.getBuffer(RenderLayer.LINES);
        ms.push();
        var m = ms.peek().getPositionMatrix();
        float x1=x+w, y1=y+h, z1=z+d;
        vc.vertex(m,x,y,z).color(r,g,b,a); vc.vertex(m,x1,y,z).color(r,g,b,a);
        vc.vertex(m,x1,y,z).color(r,g,b,a); vc.vertex(m,x1,y,z1).color(r,g,b,a);
        vc.vertex(m,x1,y,z1).color(r,g,b,a); vc.vertex(m,x,y,z1).color(r,g,b,a);
        vc.vertex(m,x,y,z1).color(r,g,b,a); vc.vertex(m,x,y,z).color(r,g,b,a);
        vc.vertex(m,x,y1,z).color(r,g,b,a); vc.vertex(m,x1,y1,z).color(r,g,b,a);
        vc.vertex(m,x1,y1,z).color(r,g,b,a); vc.vertex(m,x1,y1,z1).color(r,g,b,a);
        vc.vertex(m,x1,y1,z1).color(r,g,b,a); vc.vertex(m,x,y1,z1).color(r,g,b,a);
        vc.vertex(m,x,y1,z1).color(r,g,b,a); vc.vertex(m,x,y1,z).color(r,g,b,a);
        vc.vertex(m,x,y,z).color(r,g,b,a); vc.vertex(m,x,y1,z).color(r,g,b,a);
        vc.vertex(m,x1,y,z).color(r,g,b,a); vc.vertex(m,x1,y1,z).color(r,g,b,a);
        vc.vertex(m,x1,y,z1).color(r,g,b,a); vc.vertex(m,x1,y1,z1).color(r,g,b,a);
        vc.vertex(m,x,y,z1).color(r,g,b,a); vc.vertex(m,x,y1,z1).color(r,g,b,a);
        ms.pop();
    }

    // ============================================================
    // Module toggle IPC (called from ClickGUI)
    // ============================================================

    public static void sendModuleToggleToCpp(int moduleId, boolean enabled) {
        if (ipc != null) {
            byte[] p = new byte[2];
            p[0] = (byte) moduleId;
            p[1] = (byte) (enabled ? 1 : 0);
            ipc.send(Protocol.EV_MODULE_TOGGLE, p);
        }
    }

    public static void sendSettingToCpp(String key, String value) {
        if (ipc != null) {
            byte[] kb = key.getBytes(StandardCharsets.UTF_8);
            byte[] vb = value.getBytes(StandardCharsets.UTF_8);
            byte[] p = new byte[1 + kb.length + vb.length];
            p[0] = (byte) kb.length;
            System.arraycopy(kb, 0, p, 1, kb.length);
            System.arraycopy(vb, 0, p, 1 + kb.length, vb.length);
            ipc.send(Protocol.EV_SETTINGS_UPDATE, p);
        }
    }

    // ============================================================
    // byte helpers (little endian)
    // ============================================================

    static void writeInt(byte[] b, int off, int v) { b[off]=(byte)v; b[off+1]=(byte)(v>>>8); b[off+2]=(byte)(v>>>16); b[off+3]=(byte)(v>>>24); }
    static void writeFloat(byte[] b, int off, float v) { writeInt(b, off, Float.floatToRawIntBits(v)); }
    static void writeDouble(byte[] b, int off, double v) { writeLong(b, off, Double.doubleToRawLongBits(v)); }
    static void writeLong(byte[] b, int off, long v) { for(int i=0;i<8;i++) b[off+i]=(byte)(v>>>(8*i)); }
    static int readInt(byte[] b, int off) { return (b[off]&0xFF)|((b[off+1]&0xFF)<<8)|((b[off+2]&0xFF)<<16)|((b[off+3]&0xFF)<<24); }
    static float readFloat(byte[] b, int off) { return Float.intBitsToFloat(readInt(b, off)); }
    static double readDouble(byte[] b, int off) { return Double.longBitsToDouble(readLong(b, off)); }
    static long readLong(byte[] b, int off) { long v=0; for(int i=0;i<8;i++) v|=((long)(b[off+i]&0xFF))<<(8*i); return v; }
    static void writeIntTo(ByteArrayOutputStream s, int v) { s.write(v); s.write(v>>>8); s.write(v>>>16); s.write(v>>>24); }
    static void writeFloatTo(ByteArrayOutputStream s, float v) { writeIntTo(s, Float.floatToRawIntBits(v)); }
}
