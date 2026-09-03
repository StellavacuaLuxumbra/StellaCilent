package dev.stella.executer.modules;

import dev.stella.executer.gui.Module;
import dev.stella.executer.gui.ModuleManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Runs all module tick logic on the Java side.
 * This is the independent brain — no C++ needed.
 */
public class ModuleTicker {

    private static ModuleTicker instance;

    public static ModuleTicker getInstance() {
        if (instance == null) instance = new ModuleTicker();
        return instance;
    }

    public void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

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
            }
        }
    }

    // ---- KillAura ----
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
            entities = mc.world.getEntitiesByClass(PlayerEntity.class, box,
                    e -> e != mc.player && e.isAlive() && !e.isRemoved());
        } else {
            entities = mc.world.getEntitiesByClass(LivingEntity.class, box,
                    e -> e != mc.player && e.isAlive() && !e.isRemoved());
        }

        for (LivingEntity e : entities) {
            float dist = mc.player.distanceTo(e);
            if (dist < bestDist) {
                bestDist = dist;
                bestTarget = e;
            }
        }

        if (bestTarget != null) {
            // look at target
            Vec3d eye = mc.player.getEyePos();
            double dx = bestTarget.getX() - eye.x;
            double dy = bestTarget.getY() + bestTarget.getStandingEyeHeight() * 0.5 - eye.y;
            double dz = bestTarget.getZ() - eye.z;
            mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
            mc.player.setPitch((float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
            // attack
            mc.interactionManager.attackEntity(mc.player, bestTarget);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    // ---- ESP (sends draw commands via IPC if connected) ----
    private void tickEsp(MinecraftClient mc, Module m) {
        // ESP rendering is handled by the C++ side via IPC draw commands
        // On Java-only mode, ESP is rendered via WorldRenderEvents
    }

    // ---- Speed ----
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

    // ---- Fly ----
    private void tickFly(MinecraftClient mc, Module m) {
        var speedSetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Speed");
        float speed = speedSetting != null ? (float) speedSetting.getValue() : 2.0f;

        double vy = 0;
        if (mc.options.jumpKey.isPressed()) vy = speed * 0.1;
        else if (mc.options.sneakKey.isPressed()) vy = -speed * 0.1;
        mc.player.setVelocity(mc.player.getVelocity().x, vy, mc.player.getVelocity().z);
    }

    // ---- Sprint ----
    private void tickSprint(MinecraftClient mc, Module m) {
        if (mc.player.input.movementForward > 0 && !mc.player.isSneaking()) {
            mc.player.setSprinting(true);
        }
    }

    // ---- Fullbright ----
    private void tickFullbright(MinecraftClient mc, Module m) {
        var brightnessSetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Brightness");
        double val = brightnessSetting != null ? brightnessSetting.getValue() : 1000.0;
        mc.options.getGamma().setValue(val);
    }

    // ---- NoFall ----
    private void tickNoFall(MinecraftClient mc, Module m) {
        if (mc.player.fallDistance > 2.5f) {
            mc.player.setOnGround(true);
        }
    }

    // ---- Velocity ----
    private void tickVelocity(MinecraftClient mc, Module m) {
        var hSetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Horizontal");
        var vSetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Vertical");
        float h = hSetting != null ? (float) hSetting.getValue() / 100f : 0f;
        float v = vSetting != null ? (float) vSetting.getValue() / 100f : 0f;

        Vec3d vel = mc.player.getVelocity();
        mc.player.setVelocity(vel.x * h, vel.y * v, vel.z * h);
    }

    // ---- Step ----
    private void tickStep(MinecraftClient mc, Module m) {
    }

    // ---- FastFall ----
    private void tickFastFall(MinecraftClient mc, Module m) {
        if (mc.player.fallDistance > 0.5f && mc.player.getVelocity().y < 0) {
            mc.player.setVelocity(mc.player.getVelocity().x, -0.5, mc.player.getVelocity().z);
        }
    }

    // ---- Criticals ----
    private void tickCriticals(MinecraftClient mc, Module m) {
        // simplified: mini-jump before attack
        if (mc.player.isOnGround() && !mc.player.isInsideWaterOrBubbleColumn()) {
            mc.player.jump();
            mc.player.setVelocity(mc.player.getVelocity().x * 0.5, 0.05, mc.player.getVelocity().z * 0.5);
        }
    }

    // ---- AutoLog ----
    private void tickAutoLog(MinecraftClient mc, Module m) {
        var healthSetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Health");
        float health = healthSetting != null ? (float) healthSetting.getValue() : 4.0f;
        if (mc.player.getHealth() <= health) {
            mc.disconnect();
        }
    }

    // ---- Timer ----
    private void tickTimer(MinecraftClient mc, Module m) {
        var multSetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Multiplier");
        float mult = multSetting != null ? (float) multSetting.getValue() : 2.0f;
        // timer manipulation - simplified
    }

    // ---- Freecam ----
    private void tickFreecam(MinecraftClient mc, Module m) {
        // simplified freecam: detach camera
    }

    // ---- CrystalAura ----
    private void tickCrystalAura(MinecraftClient mc, Module m) {
        var rangeSetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Range");
        float range = rangeSetting != null ? (float) rangeSetting.getValue() : 5.0f;

        var minDmgSetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("MinDmg");
        float minDmg = minDmgSetting != null ? (float) minDmgSetting.getValue() : 6.0f;

        var antiSelfSetting = (dev.stella.executer.gui.setting.BooleanSetting) m.getSetting("AntiSelf");
        boolean antiSelf = antiSelfSetting == null || antiSelfSetting.getValue();

        // find closest end crystal
        Box box = mc.player.getBoundingBox().expand(range);
        List<EndCrystalEntity> crystals = mc.world.getEntitiesByClass(EndCrystalEntity.class, box,
                e -> e.isAlive() && !e.isRemoved());

        if (crystals.isEmpty()) return;

        // find nearest player to any crystal (for damage estimation)
        EndCrystalEntity bestCrystal = null;
        float bestScore = -1;

        for (EndCrystalEntity crystal : crystals) {
            Vec3d crystalPos = crystal.getPos();
            float distToPlayer = (float) mc.player.getPos().distanceTo(crystalPos);
            if (distToPlayer > range) continue;

            // check if crystal is on obsidian or bedrock
            BlockPos cPos = crystal.getBlockPos();
            boolean validBase = mc.world.getBlockState(cPos.down()).getBlock() == Blocks.OBSIDIAN
                    || mc.world.getBlockState(cPos.down()).getBlock() == Blocks.BEDROCK;
            if (!validBase) continue;

            // estimate damage to nearest player
            float estimatedDmg = estimateCrystalDamage(mc, crystalPos, range);

            if (antiSelf && estimatedDmg > 0) {
                // skip if self-damage would be too high
                float selfDmg = estimateSelfDamage(mc, crystalPos);
                var maxSelfSetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("MaxSelfDmg");
                float maxSelfDmg = maxSelfSetting != null ? (float) maxSelfSetting.getValue() : 12.0f;
                if (selfDmg > maxSelfDmg) continue;
            }

            float score = estimatedDmg - distToPlayer * 0.5f;
            if (score > bestScore) {
                bestScore = score;
                bestCrystal = crystal;
            }
        }

        if (bestCrystal != null) {
            // look at crystal
            Vec3d eye = mc.player.getEyePos();
            Vec3d crystalPos = bestCrystal.getPos();
            double dx = crystalPos.x - eye.x;
            double dy = crystalPos.y + 0.5 - eye.y;
            double dz = crystalPos.z - eye.z;
            mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
            mc.player.setPitch((float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
            // attack crystal
            mc.interactionManager.attackEntity(mc.player, bestCrystal);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private float estimateCrystalDamage(MinecraftClient mc, Vec3d crystalPos, float range) {
        float bestDmg = 0;
        Box playerBox = mc.player.getBoundingBox().expand(range);
        List<PlayerEntity> players = mc.world.getEntitiesByClass(PlayerEntity.class, playerBox,
                e -> e != mc.player && e.isAlive() && !e.isRemoved());
        for (PlayerEntity p : players) {
            float dist = (float) p.getPos().distanceTo(crystalPos);
            if (dist < 1.0f) dist = 1.0f;
            float dmg = Math.max(0, (float) (6.0 * Math.pow(0.9, dist) + 1.0));
            if (dmg > bestDmg) bestDmg = dmg;
        }
        return bestDmg;
    }

    private float estimateSelfDamage(MinecraftClient mc, Vec3d crystalPos) {
        float dist = (float) mc.player.getPos().distanceTo(crystalPos);
        if (dist < 1.0f) dist = 1.0f;
        return Math.max(0, (float) (6.0 * Math.pow(0.9, dist) + 1.0));
    }

    // ---- SelfTrap ----
    private void tickSelfTrap(MinecraftClient mc, Module m) {
        if (mc.player == null || mc.world == null) return;

        var delaySetting = (dev.stella.executer.gui.setting.SliderSetting) m.getSetting("Delay");
        // delay is handled by tick timing, simplified here

        // place obsidian around player's head (y+1 level)
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos headPos = playerPos.up();

        List<BlockPos> targets = new ArrayList<>();
        targets.add(headPos.north());
        targets.add(headPos.south());
        targets.add(headPos.east());
        targets.add(headPos.west());

        for (BlockPos target : targets) {
            if (mc.world.getBlockState(target).isReplaceable()
                    && mc.world.getBlockState(target.down()).isSolidBlock(mc.world, target.down())) {
                if (mc.player.getMainHandStack().getItem() == Items.OBSIDIAN
                        || mc.player.getOffHandStack().getItem() == Items.OBSIDIAN) {
                    // find face to place on
                    Direction placeDir = findPlaceDirection(mc, target);
                    if (placeDir != null) {
                        BlockPos neighbor = target.offset(placeDir);
                        BlockHitResult hitResult = new BlockHitResult(
                                Vec3d.ofCenter(target), placeDir.getOpposite(), neighbor, false);
                        Hand hand = mc.player.getMainHandStack().getItem() == Items.OBSIDIAN
                                ? Hand.MAIN_HAND : Hand.OFF_HAND;
                        mc.interactionManager.interactBlock(mc.player, hand, hitResult);
                        mc.player.swingHand(hand);
                    }
                }
            }
        }
    }

    // ---- Surround ----
    private void tickSurround(MinecraftClient mc, Module m) {
        if (mc.player == null || mc.world == null) return;

        var expandSetting = (dev.stella.executer.gui.setting.BooleanSetting) m.getSetting("Expand");
        boolean expand = expandSetting != null && expandSetting.getValue();

        BlockPos playerPos = mc.player.getBlockPos();
        List<BlockPos> targets = new ArrayList<>();

        // base layer (foot level)
        targets.add(playerPos.north());
        targets.add(playerPos.south());
        targets.add(playerPos.east());
        targets.add(playerPos.west());
        // corners
        targets.add(playerPos.north().west());
        targets.add(playerPos.north().east());
        targets.add(playerPos.south().west());
        targets.add(playerPos.south().east());

        if (expand) {
            // expand one more layer
            for (BlockPos base : new ArrayList<>(targets)) {
                targets.add(base.north());
                targets.add(base.south());
                targets.add(base.east());
                targets.add(base.west());
            }
        }

        for (BlockPos target : targets) {
            if (mc.world.getBlockState(target).isReplaceable()
                    && mc.world.getBlockState(target.down()).isSolidBlock(mc.world, target.down())) {
                if (mc.player.getMainHandStack().getItem() == Items.OBSIDIAN
                        || mc.player.getOffHandStack().getItem() == Items.OBSIDIAN) {
                    Direction placeDir = findPlaceDirection(mc, target);
                    if (placeDir != null) {
                        BlockPos neighbor = target.offset(placeDir);
                        BlockHitResult hitResult = new BlockHitResult(
                                Vec3d.ofCenter(target), placeDir.getOpposite(), neighbor, false);
                        Hand hand = mc.player.getMainHandStack().getItem() == Items.OBSIDIAN
                                ? Hand.MAIN_HAND : Hand.OFF_HAND;
                        mc.interactionManager.interactBlock(mc.player, hand, hitResult);
                        mc.player.swingHand(hand);
                    }
                }
            }
        }
    }

    private Direction findPlaceDirection(MinecraftClient mc, BlockPos target) {
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            BlockPos neighbor = target.offset(dir);
            BlockState state = mc.world.getBlockState(neighbor);
            if (state.isSolidBlock(mc.world, neighbor)) {
                return dir;
            }
        }
        // try looking down from player
        BlockPos playerPos = mc.player.getBlockPos();
        if (target.getY() <= playerPos.getY()) {
            return Direction.UP;
        }
        return null;
    }
}
