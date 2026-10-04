package dev.stella.mod.modules.impl.combat;

import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.EnumSetting;

import java.util.ArrayList;
import java.util.List;

/**
 * CombatManager - 战斗模块互斥协调（Stella 改进项，修复 SunCat 已知严重冲突）
 *
 * SunCat 已知 bug：同时开启 活塞水晶(PistonCrystal) / 自动水晶(AutoCrystal) /
 * 自动重生锚(AutoAnchor) 等模块时，它们会互相抢占：
 *  1. 物品栏槽位（各自 InventorySwap/AutoSwap 切换到不同物品 → 放错方块/打错物品）
 *  2. 旋转权（多个 RotationEvent 同时 setTarget → 视角来回抽搐）
 *  3. 水晶破坏权（PistonCrystal/AutoCrystal 互相打断对方需要的水晶）
 * 结果是互相干扰、严重冲突。
 *
 * 本模块提供按优先级互斥（配置项）：
 *  - Mode = Smart（默认）：高优先级模块"当前有作业"时低优先级让位；
 *                          高优先级没有目标时自动让给下一级，按情况切换
 *  - Mode = Strict：只要更高优先级模块处于启用状态，低优先级完全不运行
 *  - Mode = Off：关闭互斥（SunCat 原始行为，会冲突）
 *
 * Priority 决定三个核心模块的优先顺序；TpAnchorAura 固定为最低（TpCrystalAura 已并入 AutoCrystal 的 TpAssist）。
 */
public class CombatManager extends Module {
    public static CombatManager INSTANCE;

    public enum Mode {
        Smart,
        Strict,
        Off
    }

    public enum Priority {
        AutoCrystal,
        AutoAnchor,
        PistonCrystal
    }

    private final EnumSetting<Mode> mode = this.add(new EnumSetting<>("Mode", Mode.Smart));
    private final EnumSetting<Priority> priority = this.add(new EnumSetting<>("Priority", Priority.AutoCrystal));

    public CombatManager() {
        super("CombatManager", "Keeps conflicting combat modules (AutoCrystal/AutoAnchor/PistonCrystal) from fighting each other", Module.Category.Combat);
        this.setChinese("\u6218\u6597\u534f\u8c03");
        INSTANCE = this;
    }

    /**
     * 返回 true 表示模块 m 本 tick 允许执行战斗动作（放置/破坏/旋转）。
     * 未启用互斥、只有一个冲突模块启用、或 m 不属于冲突组时恒为 true。
     */
    public static boolean allows(Module m) {
        CombatManager mgr = INSTANCE;
        if (mgr == null || m == null || m.isOff()) {
            return true;
        }
        Mode current = mgr.mode.getValue();
        if (current == Mode.Off) {
            return true;
        }
        List<Module> active = new ArrayList<>(4);
        for (Module candidate : mgr.order()) {
            if (candidate != null && candidate.isOn()) {
                active.add(candidate);
            }
        }
        if (active.size() <= 1) {
            return true;
        }
        int index = active.indexOf(m);
        if (index < 0) {
            return true;
        }
        if (current == Mode.Strict) {
            return index == 0;
        }
        for (int i = 0; i < index; ++i) {
            if (hasWork(active.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** 按 Priority 配置生成冲突组的优先级顺序（越靠前优先级越高）。 */
    private List<Module> order() {
        Module top;
        Module second;
        Module third;
        switch (this.priority.getValue()) {
            case AutoAnchor: {
                top = AutoAnchor.INSTANCE;
                second = AutoCrystal.INSTANCE;
                third = PistonCrystal.INSTANCE;
                break;
            }
            case PistonCrystal: {
                top = PistonCrystal.INSTANCE;
                second = AutoCrystal.INSTANCE;
                third = AutoAnchor.INSTANCE;
                break;
            }
            default: {
                top = AutoCrystal.INSTANCE;
                second = AutoAnchor.INSTANCE;
                third = PistonCrystal.INSTANCE;
                break;
            }
        }
        List<Module> order = new ArrayList<>(5);
        for (Module m : new Module[]{top, second, third, TpAnchorAura.INSTANCE}) {
            if (m != null && !order.contains(m)) {
                order.add(m);
            }
        }
        return order;
    }

    /** 高优先级模块当前是否"有作业"（Smart 模式让位判断依据）。 */
    private static boolean hasWork(Module m) {
        if (m instanceof AutoCrystal crystal) {
            return crystal.hasWork();
        }
        if (m instanceof AutoAnchor anchor) {
            return anchor.hasWork();
        }
        if (m instanceof PistonCrystal piston) {
            return piston.hasWork();
        }
        return true;
    }
}
