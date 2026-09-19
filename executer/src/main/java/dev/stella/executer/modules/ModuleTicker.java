package dev.stella.executer.modules;

import dev.stella.executer.gui.Module;
import dev.stella.executer.gui.ModuleManager;
import dev.stella.executer.brain.LuaScriptHost;
import dev.stella.executer.protection.RaspProtection;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ModuleTicker {

    private static ModuleTicker instance;
    private static boolean luaInitialized = false;
    private static boolean raspInitialized = false;

    public static final CopyOnWriteArrayList<Packet<?>> blinkPackets = new CopyOnWriteArrayList<>();
    private static Vec3d blinkStartPos;
    private static float blinkStartYaw, blinkStartPitch;
    public static boolean velocityActive = false;

    public static ModuleTicker getInstance() {
        if (instance == null) instance = new ModuleTicker();
        return instance;
    }

    public void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        if (!raspInitialized) {
            try { RaspProtection.initialize(); raspInitialized = true; } catch (Exception e) {}
        }
        if (RaspProtection.isTampered()) return;
        if (!luaInitialized) {
            try { dev.stella.executer.brain.LuaScriptLoader.loadAll(); luaInitialized = true; } catch (Exception e) {}
        }
        if (LuaScriptHost.isInitialized()) {
            try { LuaScriptHost.tick(mc); } catch (Exception e) {}
        }
        for (Module m : ModuleManager.getInstance().getModules()) {
            if (!m.isEnabled()) continue;
            switch (m.getName()) {
                case "KillAura" -> tickKillAura(mc, m);
                case "ESP" -> tickEsp(mc, m);
                case "Speed" -> tickSpeed(mc, m);
                case "Fly" -> tickFly(mc, m);
                case "Sprint" -> tickSprint(mc, m);
                case "Fullbright" -> tickFullbright(mc, m);
                case "NoFall" -> tickNoFall(mc, m);
                case "Velocity" -> tickVelocity(mc, m);
                case "Step" -> tickStep(mc, m);
                case "FastFall" -> tickFastFall(mc, m);
                case "Criticals" -> tickCriticals(mc, m);
                case "AutoLog" -> tickAutoLog(mc, m);
                case "Timer" -> tickTimer(mc, m);
                case "Freecam" -> tickFreecam(mc, m);
                case "CrystalAura" -> tickCrystalAura(mc, m);
                case "SelfTrap" -> tickSelfTrap(mc, m);
                case "Surround" -> tickSurround(mc, m);
                case "NoSlowDown" -> tickNoSlowDown(mc, m);
                case "AutoTotem" -> tickAutoTotem(mc, m);
                case "ChestStealer" -> tickChestStealer(mc, m);
                case "Scaffold" -> tickScaffold(mc, m);
                case "AutoArmor" -> tickAutoArmor(mc, m);
                case "AutoClicker" -> tickAutoClicker(mc, m);
                case "AimAssist" -> tickAimAssist(mc, m);
                case "FastBreak" -> tickFastBreak(mc, m);
                case "Reach" -> tickReach(mc, m);
                case "Blink" -> tickBlink(mc, m);
                case "AntiHunger" -> tickAntiHunger(mc, m);
                case "FastUse" -> tickFastUse(mc, m);
                case "AutoFish" -> tickAutoFish(mc, m);
            }
        }
        tickBlinkState(mc);
    }

    public static void onSendPacket(Packet<?> packet, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Module blinkModule = ModuleManager.getInstance().getModule("Blink");
        if (blinkModule != null && blinkModule.isEnabled()) {
            if (packet instanceof PlayerMoveC2SPacket || packet instanceof PlayerInteractEntityC2SPacket
                || packet instanceof PlayerInteractBlockC2SPacket || packet instanceof PlayerInteractItemC2SPacket
                || packet instanceof HandSwingC2SPacket || packet instanceof PlayerActionC2SPacket) {
                blinkPackets.add(packet);
                ci.cancel();
            }
        }
    }

    public static void onReceivePacket(Packet<?> packet) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Module velocityModule = ModuleManager.getInstance().getModule("Velocity");
        if (velocityModule != null && velocityModule.isEnabled()) {
            if (packet instanceof EntityVelocityUpdateS2CPacket velPacket) {
                if (velPacket.getEntityId() == mc.player.getId()) {
                    velocityActive = true;
                }
            }
        }
        Module blinkModule = ModuleManager.getInstance().getModule("Blink");
        if (blinkModule != null && blinkModule.isEnabled()) {
            if (packet instanceof PlayerPositionLookS2CPacket) {
                releaseBlink(mc);
            }
        }
    }

    // ========== Velocity ==========
    private void tickVelocity(MinecraftClient mc, Module m) {
        var hSetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Horizontal");
        var vSetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Vertical");
        float h = hSetting != null ? (float) hSetting.getValue() / 100f : 0f;
        float v = vSetting != null ? (float) vSetting.getValue() / 100f : 0f;
        if (velocityActive) {
            Vec3d vel = mc.player.getVelocity();
            mc.player.setVelocity(vel.x * h, vel.y * v, vel.z * h);
            velocityActive = false;
        }
    }

    // ========== Criticals ==========
    private void tickCriticals(MinecraftClient mc, Module m) {
        // crit is applied in tickKillAura before attack
    }

    public static void applyCriticals(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        Module m = ModuleManager.getInstance().getModule("Criticals");
        if (m == null || !m.isEnabled()) return;
        if (!mc.player.isOnGround()) return;
        if (mc.player.isInsideWaterOrBubbleColumn() || mc.player.isInLava()) return;
        if (mc.player.isFallFlying()) return;
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false));
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.04535, z, false));
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false));
    }

    // ========== Blink ==========
    private void tickBlink(MinecraftClient mc, Module m) {
        if (blinkStartPos == null) {
            blinkStartPos = mc.player.getPos();
            blinkStartYaw = mc.player.getYaw();
            blinkStartPitch = mc.player.getPitch();
        }
    }

    private void tickBlinkState(MinecraftClient mc) {
        Module m = ModuleManager.getInstance().getModule("Blink");
        if (m == null || !m.isEnabled()) {
            if (!blinkPackets.isEmpty()) {
                releaseBlink(mc);
            }
            blinkStartPos = null;
            return;
        }
    }

    public static void releaseBlink(MinecraftClient mc) {
        for (Packet<?> packet : blinkPackets) {
            mc.getNetworkHandler().sendPacket(packet);
        }
        blinkPackets.clear();
    }

    public static void cancelBlink(MinecraftClient mc) {
        if (blinkStartPos != null) {
            mc.player.setPosition(blinkStartPos);
            mc.player.setYaw(blinkStartYaw);
            mc.player.setPitch(blinkStartPitch);
        }
        blinkPackets.clear();
        blinkStartPos = null;
    }

    public static int getBlinkPacketCount() {
        return blinkPackets.size();
    }

    // ========== NoSlowDown - SunCat style: send slot packets ==========
    private int noSlowDelay = 0;
    private void tickNoSlowDown(MinecraftClient mc, Module m) {
        if (mc.player == null) return;
        noSlowDelay--;
        if (mc.player.isUsingItem() && !mc.player.isRiding() && !mc.player.isFallFlying()) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
            mc.itemUseCooldown = 0;
        }
    }

    // ========== Scaffold - SunCat style ==========
    private Vec3d scaffoldVec;
    private void tickScaffold(MinecraftClient mc, Module m) {
        if (mc.player == null || mc.world == null) return;
        int blockSlot = findBlockSlot(mc);
        if (blockSlot == -1) return;
        BlockPos placePos = mc.player.getBlockPos().down();
        if (!mc.world.getBlockState(placePos).getMaterial().isReplaceable()) return;
        Direction side = getPlaceSide(mc, placePos);
        if (side == null) {
            for (Direction dir : Direction.values()) {
                if (dir == Direction.UP) continue;
                BlockPos offset = placePos.offset(dir);
                if (mc.world.getBlockState(offset).isSolidBlock(mc.world, offset)) {
                    side = dir.getOpposite();
                    break;
                }
            }
        }
        if (side == null) return;
        int oldSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = blockSlot;
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, blockSlot < 9 ? blockSlot + 36 : blockSlot, 0, SlotActionType.SWAP, mc.player);
        BlockPos neighbor = placePos.offset(side);
        Vec3d hitVec = Vec3d.ofCenter(placePos).add(side.getUnitVector().mul(-0.5));
        BlockHitResult hit = new BlockHitResult(hitVec, side, neighbor, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, blockSlot < 9 ? blockSlot + 36 : blockSlot, 0, SlotActionType.SWAP, mc.player);
        var towerSetting = (dev.stella.executer.gui.setting.BooleanSetting) m.getSetting("Tower");
        if (towerSetting != null && towerSetting.getValue() && mc.options.jumpKey.isPressed() && mc.player.input.movementForward == 0 && mc.player.input.movementSideways == 0) {
            mc.player.setVelocity(0, 0.42, 0);
        }
    }

    private int findBlockSlot(MinecraftClient mc) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof BlockItem) return i;
        }
        return -1;
    }

    private Direction getPlaceSide(MinecraftClient mc, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            BlockPos neighbor = pos.offset(dir);
            BlockState state = mc.world.getBlockState(neighbor);
            if (state.isSolidBlock(mc.world, neighbor)) return dir;
        }
        return null;
    }

    // ========== AutoArmor - SunCat protection calc ==========
    private int autoArmorDelay = 0;
    private void tickAutoArmor(MinecraftClient mc, Module m) {
        if (mc.player == null) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen)) return;
        autoArmorDelay++;
        var delaySetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Delay");
        int delay = delaySetting != null ? (int) delaySetting.getValue() : 3;
        if (autoArmorDelay < delay) return;
        autoArmorDelay = 0;
        for (int armorSlot = 0; armorSlot < 4; armorSlot++) {
            var currentArmor = mc.player.getInventory().getArmorStack(armorSlot);
            int currentProt = getArmorProtection(currentArmor);
            int bestSlot = -1;
            int bestProt = currentProt;
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.isEmpty()) continue;
                if (stack.getItem() instanceof ArmorItem armor) {
                    if (armor.getSlotType().getEntitySlotId() == armorSlot) {
                        int prot = getArmorProtection(stack);
                        if (prot > bestProt) {
                            bestProt = prot;
                            bestSlot = i;
                        }
                    }
                }
            }
            if (bestSlot != -1) {
                mc.interactionManager.clickSlot(
                    mc.player.playerScreenHandler.syncId,
                    bestSlot < 9 ? bestSlot + 36 : bestSlot,
                    armorSlot + 5,
                    SlotActionType.SWAP, mc.player
                );
                return;
            }
        }
    }

    private int getArmorProtection(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) return -1;
        ArmorItem armor = (ArmorItem) stack.getItem();
        int prot = armor.getProtection();
        if (stack.hasEnchantments()) {
            var enchants = net.minecraft.enchantment.EnchantmentHelper.getEnchantments(stack);
            for (var entry : enchants) {
                String id = entry.getKey().toString();
                if (id.contains("protection")) prot += entry.getValue() * 2;
                else if (id.contains("blast_protection")) prot += entry.getValue();
                else if (id.contains("binding_curse")) return -999;
            }
        }
        return prot;
    }

    // ========== ChestStealer ==========
    private int chestStealerDelay = 0;
    private void tickChestStealer(MinecraftClient mc, Module m) {
        if (mc.currentScreen == null) return;
        var delaySetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Delay");
        int delay = delaySetting != null ? (int) delaySetting.getValue() : 1;
        chestStealerDelay++;
        if (chestStealerDelay < delay) return;
        chestStealerDelay = 0;
        var container = mc.player.currentScreenHandler;
        boolean found = false;
        for (int i = 0; i < container.slots.size(); i++) {
            ItemStack stack = container.getSlot(i).getStack();
            if (!stack.isEmpty() && isValuable(stack)) {
                mc.interactionManager.clickSlot(container.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                found = true;
                return;
            }
        }
        if (!found) {
            for (int i = 0; i < container.slots.size(); i++) {
                ItemStack stack = container.getSlot(i).getStack();
                if (!stack.isEmpty()) {
                    mc.interactionManager.clickSlot(container.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    return;
                }
            }
            mc.player.closeScreen();
        }
    }

    private boolean isValuable(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.TOTEM_OF_UNDYING || item == Items.END_CRYSTAL
            || item == Items.ENDER_PEARL || item == Items.EXPERIENCE_BOTTLE
            || item == Items.ENCHANTED_GOLDEN_APPLE || item == Items.GOLDEN_APPLE
            || stack.getItem() instanceof ArmorItem;
    }

    // ========== FastFall - SunCat style ==========
    private void tickFastFall(MinecraftClient mc, Module m) {
        if (mc.player == null) return;
        if (mc.player.isOnGround() || mc.player.isInsideWaterOrBubbleColumn() || mc.player.isInLava()) return;
        if (mc.player.isFallFlying()) return;
        if (mc.player.getVelocity().y >= 0) return;
        mc.player.setVelocity(mc.player.getVelocity().x, mc.player.getVelocity().y - 0.5, mc.player.getVelocity().z);
    }

    // ========== Freecam - SunCat style ==========
    private void tickFreecam(MinecraftClient mc, Module m) {
        // Freecam requires mixin hooks for camera - simplified version
    }

    // ========== AntiHunger - SunCat style: spoof ground ==========
    private void tickAntiHunger(MinecraftClient mc, Module m) {
        if (mc.player == null) return;
        var sprintSetting = (dev.stella.executer.gui.setting.BooleanSetting) m.getSetting("Sprint");
        boolean cancelSprint = sprintSetting != null && sprintSetting.getValue();
        if (cancelSprint && mc.player.isSprinting()) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        }
    }

    // ========== AutoTotem ==========
    private void tickAutoTotem(MinecraftClient mc, Module m) {
        if (mc.player == null) return;
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) return;
        var healthSetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Health");
        float threshold = healthSetting != null ? (float) healthSetting.getValue() : 8.0f;
        if (mc.player.getHealth() > threshold) return;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                mc.interactionManager.clickSlot(
                    mc.player.playerScreenHandler.syncId,
                    i < 9 ? i + 36 : i, 40, SlotActionType.SWAP, mc.player);
                return;
            }
        }
    }

    // ========== AutoClicker ==========
    private long autoClickerLastClick = 0;
    private void tickAutoClicker(MinecraftClient mc, Module m) {
        if (mc.currentScreen != null || !mc.options.attackKey.isPressed()) return;
        var minCps = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("MinCPS");
        var maxCps = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("MaxCPS");
        int min = minCps != null ? (int) minCps.getValue() : 10;
        int max = maxCps != null ? (int) maxCps.getValue() : 14;
        int cps = min + (int) (Math.random() * (max - min));
        long delay = 1000L / cps;
        long now = System.currentTimeMillis();
        if (now - autoClickerLastClick < delay) return;
        var hit = mc.crosshairTarget;
        if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
            Entity target = ((EntityHitResult) hit).getEntity();
            if (target instanceof LivingEntity le && le.isAlive()) {
                applyCriticals(mc);
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                autoClickerLastClick = now;
            }
        }
    }

    // ========== AimAssist ==========
    private void tickAimAssist(MinecraftClient mc, Module m) {
        if (mc.currentScreen != null || !mc.options.attackKey.isPressed()) return;
        var speedSetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Speed");
        float speed = speedSetting != null ? (float) speedSetting.getValue() : 5.0f;
        var fovSetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("FOV");
        float fov = fovSetting != null ? (float) fovSetting.getValue() : 90.0f;
        LivingEntity bestTarget = null;
        float bestDist = 6.0f;
        Box box = mc.player.getBoundingBox().expand(6);
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !(entity instanceof LivingEntity le)) continue;
            if (!le.isAlive() || le.isRemoved()) continue;
            float dist = mc.player.distanceTo(le);
            if (dist < bestDist) {
                bestDist = dist;
                bestTarget = le;
            }
        }
        if (bestTarget == null) return;
        Vec3d eye = mc.player.getEyePos();
        Vec3d targetPos = bestTarget.getPos().add(0, bestTarget.getStandingEyeHeight() * 0.5, 0);
        double dx = targetPos.x - eye.x;
        double dy = targetPos.y - eye.y;
        double dz = targetPos.z - eye.z;
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float yawDiff = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
        float pitchDiff = targetPitch - mc.player.getPitch();
        if (Math.abs(yawDiff) > fov / 2f) return;
        float factor = speed * 0.02f;
        mc.player.setYaw(mc.player.getYaw() + yawDiff * factor);
        mc.player.setPitch(Math.max(-90, Math.min(90, mc.player.getPitch() + pitchDiff * factor)));
    }

    // ========== KillAura ==========
    private void tickKillAura(MinecraftClient mc, Module m) {
        var rangeSetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Range");
        float range = rangeSetting != null ? (float) rangeSetting.getValue() : 4.0f;
        var playersOnlySetting = (dev.stella.executer.gui.setting.BooleanSetting) m.getSetting("Players Only");
        boolean playersOnly = playersOnlySetting == null || playersOnlySetting.getValue();
        LivingEntity bestTarget = null;
        float bestDist = range;
        Box box = mc.player.getBoundingBox().expand(range, range, range);
        List<? extends LivingEntity> entities;
        if (playersOnly) {
            entities = mc.world.getEntitiesByClass(PlayerEntity.class, box, e -> e != mc.player && e.isAlive() && !e.isRemoved());
        } else {
            entities = mc.world.getEntitiesByClass(LivingEntity.class, box, e -> e != mc.player && e.isAlive() && !e.isRemoved());
        }
        for (LivingEntity e : entities) {
            float dist = mc.player.distanceTo(e);
            if (dist < bestDist) { bestDist = dist; bestTarget = e; }
        }
        if (bestTarget != null) {
            Vec3d eye = mc.player.getEyePos();
            double dx = bestTarget.getX() - eye.x;
            double dy = bestTarget.getY() + bestTarget.getStandingEyeHeight() * 0.5 - eye.y;
            double dz = bestTarget.getZ() - eye.z;
            mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
            mc.player.setPitch((float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
            Module critMod = ModuleManager.getInstance().getModule("Criticals");
            if (critMod != null && critMod.isEnabled()) applyCriticals(mc);
            mc.interactionManager.attackEntity(mc.player, bestTarget);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    // ========== Other modules ==========
    private void tickEsp(MinecraftClient mc, Module m) {}
    private void tickSpeed(MinecraftClient mc, Module m) {
        var speedSetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Speed");
        float speed = speedSetting != null ? (float) speedSetting.getValue() : 1.5f;
        if (mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0) {
            float yawRad = mc.player.getYaw() * 0.017453292f;
            float vx = -MathHelper.sin(yawRad) * speed * 0.2f;
            float vz = MathHelper.cos(yawRad) * speed * 0.2f;
            mc.player.setVelocity(vx, mc.player.getVelocity().y, vz);
        }
    }
    private void tickFly(MinecraftClient mc, Module m) {
        var speedSetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Speed");
        float speed = speedSetting != null ? (float) speedSetting.getValue() : 2.0f;
        double vy = 0;
        if (mc.options.jumpKey.isPressed()) vy = speed * 0.1;
        else if (mc.options.sneakKey.isPressed()) vy = -speed * 0.1;
        mc.player.setVelocity(mc.player.getVelocity().x, vy, mc.player.getVelocity().z);
    }
    private void tickSprint(MinecraftClient mc, Module m) {
        if (mc.player.input.movementForward > 0 && !mc.player.isSneaking()) mc.player.setSprinting(true);
    }
    private void tickFullbright(MinecraftClient mc, Module m) {
        var b = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Brightness");
        double val = b != null ? b.getValue() : 1.0;
        mc.options.getGamma().setValue(Math.min(val, 1.0));
    }
    private void tickNoFall(MinecraftClient mc, Module m) {
        if (mc.player.fallDistance > 2.5f) mc.player.setOnGround(true);
    }
    private void tickStep(MinecraftClient mc, Module m) {}
    private void tickAutoLog(MinecraftClient mc, Module m) {
        var h = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Health");
        float health = h != null ? (float) h.getValue() : 4.0f;
        if (mc.player.getHealth() <= health) mc.disconnect();
    }
    private void tickTimer(MinecraftClient mc, Module m) {}
    private void tickFastBreak(MinecraftClient mc, Module m) {}
    private void tickReach(MinecraftClient mc, Module m) {}
    private void tickFastUse(MinecraftClient mc, Module m) {
        var multSetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Multiplier");
        float mult = multSetting != null ? (float) multSetting.getValue() : 2.0f;
        if (mc.player.isUsingItem()) mc.itemUseCooldown = 0;
    }
    private void tickAutoFish(MinecraftClient mc, Module m) {
        if (!(mc.player.getMainHandStack().getItem() instanceof FishingRodItem)) return;
        var hookResult = mc.crosshairTarget;
        if (hookResult != null && hookResult.getType() == HitResult.Type.MISS) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private void tickCrystalAura(MinecraftClient mc, Module m) {
        var rangeSetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Range");
        float range = rangeSetting != null ? (float) rangeSetting.getValue() : 5.0f;
        Box box = mc.player.getBoundingBox().expand(range);
        List<EndCrystalEntity> crystals = mc.world.getEntitiesByClass(EndCrystalEntity.class, box, e -> e.isAlive() && !e.isRemoved());
        if (crystals.isEmpty()) return;
        EndCrystalEntity bestCrystal = null;
        float bestScore = -1;
        for (EndCrystalEntity crystal : crystals) {
            float dist = (float) mc.player.getPos().distanceTo(crystal.getPos());
            if (dist > range) continue;
            BlockPos cPos = crystal.getBlockPos();
            boolean valid = mc.world.getBlockState(cPos.down()).getBlock() == Blocks.OBSIDIAN || mc.world.getBlockState(cPos.down()).getBlock() == Blocks.BEDROCK;
            if (!valid) continue;
            float score = 10.0f - dist;
            if (score > bestScore) { bestScore = score; bestCrystal = crystal; }
        }
        if (bestCrystal != null) {
            Vec3d eye = mc.player.getEyePos();
            Vec3d cPos = bestCrystal.getPos();
            double dx = cPos.x - eye.x; double dy = cPos.y + 0.5 - eye.y; double dz = cPos.z - eye.z;
            mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
            mc.player.setPitch((float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
            mc.interactionManager.attackEntity(mc.player, bestCrystal);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private void tickSelfTrap(MinecraftClient mc, Module m) {
        if (mc.player == null || mc.world == null) return;
        BlockPos headPos = mc.player.getBlockPos().up();
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;
            BlockPos target = headPos.offset(dir);
            if (mc.world.getBlockState(target).getMaterial().isReplaceable()) {
                if (mc.player.getMainHandStack().getItem() == Items.OBSIDIAN || mc.player.getOffHandStack().getItem() == Items.OBSIDIAN) {
                    Direction placeDir = getPlaceSide(mc, target);
                    if (placeDir != null) {
                        BlockPos neighbor = target.offset(placeDir);
                        Vec3d hitVec = Vec3d.ofCenter(target).add(placeDir.getUnitVector().mul(-0.5));
                        BlockHitResult hit = new BlockHitResult(hitVec, placeDir, neighbor, false);
                        Hand hand = mc.player.getMainHandStack().getItem() == Items.OBSIDIAN ? Hand.MAIN_HAND : Hand.OFF_HAND;
                        mc.interactionManager.interactBlock(mc.player, hand, hit);
                        mc.player.swingHand(hand);
                    }
                }
            }
        }
    }

    private void tickSurround(MinecraftClient mc, Module m) {
        if (mc.player == null || mc.world == null) return;
        BlockPos playerPos = mc.player.getBlockPos();
        List<BlockPos> targets = new ArrayList<>();
        targets.add(playerPos.north()); targets.add(playerPos.south());
        targets.add(playerPos.east()); targets.add(playerPos.west());
        for (BlockPos target : targets) {
            if (mc.world.getBlockState(target).getMaterial().isReplaceable()) {
                if (mc.player.getMainHandStack().getItem() == Items.OBSIDIAN || mc.player.getOffHandStack().getItem() == Items.OBSIDIAN) {
                    Direction placeDir = getPlaceSide(mc, target);
                    if (placeDir != null) {
                        BlockPos neighbor = target.offset(placeDir);
                        Vec3d hitVec = Vec3d.ofCenter(target).add(placeDir.getUnitVector().mul(-0.5));
                        BlockHitResult hit = new BlockHitResult(hitVec, placeDir, neighbor, false);
                        Hand hand = mc.player.getMainHandStack().getItem() == Items.OBSIDIAN ? Hand.MAIN_HAND : Hand.OFF_HAND;
                        mc.interactionManager.interactBlock(mc.player, hand, hit);
                        mc.player.swingHand(hand);
                    }
                }
            }
        }
    }

    private void applyLuaConfig(Module m, Map<String, Object> config) {
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            dev.stella.executer.gui.setting.Setting setting = m.getSetting(entry.getKey());
            if (setting instanceof dev.stella.executer.gui.setting.SliderSetting s && entry.getValue() instanceof Number n) s.setValue(n.doubleValue());
            else if (setting instanceof dev.stella.executer.gui.setting.BooleanSetting b && entry.getValue() instanceof Boolean v) b.setValue(v);
        }
    }
}
