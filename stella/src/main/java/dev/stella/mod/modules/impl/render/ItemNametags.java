package dev.stella.mod.modules.impl.render;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.Render3DEvent;
import dev.stella.api.utils.math.MathUtil;
import dev.stella.api.utils.render.Render2DUtil;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.ColorSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import dev.stella.mod.modules.settings.impl.EnumSetting;
import dev.stella.stella;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.Formatting;
import org.lwjgl.opengl.GL11;
import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import java.util.Objects;

public class ItemNametags extends Module {
    public static ItemNametags INSTANCE;

    // 基础设置
    private final BooleanSetting enabled = this.add(new BooleanSetting("Enabled", true));
    private final SliderSetting range = this.add(new SliderSetting("Range", 30.0, 1.0, 100.0, 1.0));
    private final SliderSetting scale = this.add(new SliderSetting("Scale", 0.5, 0.1, 2.0, 0.1));

    // 显示选项
    private final BooleanSetting showCount = this.add(new BooleanSetting("ShowCount", true));
    private final BooleanSetting showName = this.add(new BooleanSetting("ShowName", true));
    private final BooleanSetting showDurability = this.add(new BooleanSetting("ShowDurability", false));
    private final BooleanSetting showEnchants = this.add(new BooleanSetting("ShowEnchants", false));

    // 语言选项
    private final BooleanSetting useChinese = this.add(new BooleanSetting("Chinese", true));
    private final EnumSetting<TextCase> textCase = this.add(new EnumSetting<>("TextCase", TextCase.UpperCase, () -> !useChinese.getValue()));
    
    public enum TextCase {
        UpperCase,    // 全大写
        LowerCase,    // 全小写
        TitleCase     // 首字母大写
    }

    // 颜色设置
    private final ColorSetting textColor = this.add(new ColorSetting("TextColor", new Color(255, 255, 255, 255)));
    private final ColorSetting bgColor = this.add(new ColorSetting("BackgroundColor", new Color(0, 0, 0, 100)));
    private final ColorSetting rareColor = this.add(new ColorSetting("RareColor", new Color(255, 85, 255, 255)));
    private final ColorSetting epicColor = this.add(new ColorSetting("EpicColor", new Color(85, 255, 255, 255)));

    // 渲染选项
    private final BooleanSetting rect = this.add(new BooleanSetting("Rectangle", true));
    private final BooleanSetting depth = this.add(new BooleanSetting("Depth", false));

    public ItemNametags() {
        super("ItemNametags", Category.Render);
        this.setChinese("物品标签");
        INSTANCE = this;
    }

    @EventListener
    public void onRender3D(Render3DEvent event) {
        if (!this.enabled.getValue() || mc.world == null || mc.player == null) {
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();
        
        if (this.depth.getValue()) {
            GL11.glDepthFunc(519);
        }

        RenderSystem.enableBlend();

        MatrixStack matrixStack = new MatrixStack();

        for (Entity entity : stella.THREAD.getEntities()) {
            if (!(entity instanceof ItemEntity)) continue;
            
            ItemEntity itemEntity = (ItemEntity) entity;
            
            if (!itemEntity.isAlive()) continue;
            if (itemEntity.getStack().isEmpty()) continue;
            if (mc.player.distanceTo(itemEntity) > this.range.getValue()) continue;

            String info = getItemInfo(itemEntity);
            Vec3d renderPosition = MathUtil.getRenderPosition(entity, event.tickDelta);
            
            double x = renderPosition.getX();
            double y = renderPosition.getY();
            double z = renderPosition.getZ();

            int width = mc.textRenderer.getWidth(info);
            float hwidth = (float) width / 2.0f;

            renderInfo(info, hwidth, itemEntity, x, y, z, camera, matrixStack);
        }

        if (this.depth.getValue()) {
            GL11.glDepthFunc(515);
        }

        RenderSystem.disableBlend();
    }

    private String getItemInfo(ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getStack();
        StringBuilder info = new StringBuilder();

        // 物品名称
        if (this.showName.getValue()) {
            String itemName = getDisplayName(stack);
            info.append(itemName);
        }

        // 数量
        if (this.showCount.getValue() && stack.getCount() > 1) {
            info.append(" §7x").append(stack.getCount());
        }

        // 耐久度
        if (this.showDurability.getValue() && stack.isDamageable()) {
            int durability = stack.getMaxDamage() - stack.getDamage();
            info.append(" §a[").append(durability).append("/").append(stack.getMaxDamage()).append("]");
        }

        // 附魔
        if (this.showEnchants.getValue()) {
            var enchantments = stack.getEnchantments();
            if (!enchantments.isEmpty()) {
                info.append(" §b[");
                boolean first = true;
                for (var entry : enchantments.getEnchantmentEntries()) {
                    if (!first) info.append(", ");
                    String enchantName = getEnchantName(entry.getKey().value().toString());
                    info.append(enchantName).append(" ").append(entry.getIntValue());
                    first = false;
                }
                info.append("]");
            }
        }

        return info.toString();
    }

    private String getDisplayName(ItemStack stack) {
        if (this.useChinese.getValue()) {
            // 尝试获取中文名称
            try {
                String name = stack.getName().getString();
                // 如果是英文且包含点号（如 minecraft.diamond_sword），尝试翻译
                if (name.contains(".") || name.matches("[a-z_]+")) {
                    return translateItemName(stack.getTranslationKey());
                }
                return name;
            } catch (Exception e) {
                return translateItemName(stack.getTranslationKey());
            }
        } else {
            // 英文模式
            String name = stack.getName().getString();
            // 如果不是标准格式，返回翻译后的名称
            if (name.contains(".") || name.matches("[a-z_]+")) {
                return translateItemNameEnglish(stack.getTranslationKey());
            }
            // 根据设置返回不同大小写格式
            return formatTextCase(name);
        }
    }

    private String translateItemName(String translationKey) {
        if (!this.useChinese.getValue()) {
            // 返回英文原名
            return translateItemNameEnglish(translationKey);
        }
        
        // 中文翻译映射表
        switch (translationKey) {
            // 矿石
            case "block.minecraft.diamond_ore": return "钻石矿石";
            case "block.minecraft.iron_ore": return "铁矿石";
            case "block.minecraft.gold_ore": return "金矿石";
            case "block.minecraft.emerald_ore": return "绿宝石矿石";
            case "block.minecraft.coal_ore": return "煤炭矿石";
            case "block.minecraft.lapis_ore": return "青金石矿石";
            case "block.minecraft.redstone_ore": return "红石矿石";
            
            // 宝石
            case "item.minecraft.diamond": return "钻石";
            case "item.minecraft.emerald": return "绿宝石";
            case "item.minecraft.iron_ingot": return "铁锭";
            case "item.minecraft.gold_ingot": return "金锭";
            case "item.minecraft.netherite_ingot": return "下界合金锭";
            case "item.minecraft.netherite_scrap": return "下界合金碎片";
            
            // 工具
            case "item.minecraft.diamond_sword": return "钻石剑";
            case "item.minecraft.diamond_pickaxe": return "钻石镐";
            case "item.minecraft.diamond_axe": return "钻石斧";
            case "item.minecraft.diamond_shovel": return "钻石锹";
            case "item.minecraft.diamond_hoe": return "钻石锄";
            case "item.minecraft.netherite_sword": return "下界合金剑";
            case "item.minecraft.netherite_pickaxe": return "下界合金镐";
            case "item.minecraft.netherite_axe": return "下界合金斧";
            case "item.minecraft.iron_sword": return "铁剑";
            case "item.minecraft.iron_pickaxe": return "铁镐";
            case "item.minecraft.golden_sword": return "金剑";
            
            // 盔甲
            case "item.minecraft.diamond_helmet": return "钻石头盔";
            case "item.minecraft.diamond_chestplate": return "钻石胸甲";
            case "item.minecraft.diamond_leggings": return "钻石护腿";
            case "item.minecraft.diamond_boots": return "钻石靴子";
            case "item.minecraft.netherite_helmet": return "下界合金头盔";
            case "item.minecraft.netherite_chestplate": return "下界合金胸甲";
            case "item.minecraft.netherite_leggings": return "下界合金护腿";
            case "item.minecraft.netherite_boots": return "下界合金靴子";
            case "item.minecraft.iron_helmet": return "铁头盔";
            case "item.minecraft.iron_chestplate": return "铁胸甲";
            case "item.minecraft.golden_helmet": return "金头盔";
            
            // 食物
            case "item.minecraft.apple": return "苹果";
            case "item.minecraft.golden_apple": return "金苹果";
            case "item.minecraft.enchanted_golden_apple": return "附魔金苹果";
            case "item.minecraft.bread": return "面包";
            case "item.minecraft.cooked_beef": return "熟牛肉";
            case "item.minecraft.cooked_porkchop": return "熟猪排";
            case "item.minecraft.cooked_chicken": return "熟鸡肉";
            case "item.minecraft.cooked_mutton": return "熟羊肉";
            
            // 药水
            case "item.minecraft.potion": return "药水";
            case "item.minecraft.splash_potion": return "喷溅药水";
            case "item.minecraft.lingering_potion": return "滞留药水";
            
            // 其他
            case "item.minecraft.totem_of_undying": return "不死图腾";
            case "item.minecraft.ender_pearl": return "末影珍珠";
            case "item.minecraft.ender_eye": return "末影之眼";
            case "item.minecraft.blaze_rod": return "烈焰棒";
            case "item.minecraft.ghast_tear": return "恶魂之泪";
            case "item.minecraft.shulker_shell": return "潜影贝壳";
            case "item.minecraft.nether_star": return "下界之星";
            case "item.minecraft.dragon_egg": return "龙蛋";
            case "item.minecraft.elytra": return "鞘翅";
            case "item.minecraft.trident": return "三叉戟";
            case "item.minecraft.nautilus_shell": return "鹦鹉螺壳";
            case "item.minecraft.heart_of_the_sea": return "海洋之心";
            
            // 方块
            case "block.minecraft.obsidian": return "黑曜石";
            case "block.minecraft.crying_obsidian": return "哭泣的黑曜石";
            case "block.minecraft.ancient_debris": return "远古残骸";
            case "block.minecraft.respawn_anchor": return "重生锚";
            case "block.minecraft.beacon": return "信标";
            case "block.minecraft.enchanting_table": return "附魔台";
            case "block.minecraft.ender_chest": return "末影箱";
            case "block.minecraft.shulker_box": return "潜影盒";
            case "block.minecraft.white_shulker_box": return "白色潜影盒";
            
            // 材料
            case "item.minecraft.blaze_powder": return "烈焰粉";
            case "item.minecraft.magma_cream": return "岩浆膏";
            case "item.minecraft.fermented_spider_eye": return "发酵蛛眼";
            case "item.minecraft.glistering_melon_slice": return "闪烁的西瓜片";
            case "item.minecraft.golden_carrot": return "金胡萝卜";
            case "item.minecraft.rabbit_foot": return "兔子脚";
            case "item.minecraft.rabbit_hide": return "兔子皮";
            case "item.minecraft.enderman_head": return "末影人头颅";
            
            default:
                // 默认返回物品原名（去掉前缀和下划线）
                return translationKey.replace("item.minecraft.", "")
                        .replace("block.minecraft.", "")
                        .replace("_", " ");
        }
    }

    private String getEnchantName(String enchantKey) {
        if (!this.useChinese.getValue()) {
            // 英文模式
            String name = enchantKey.replace("enchantment.minecraft.", "")
                    .replace("_", " ");
            return formatTextCase(name);
        }
        
        // 中文翻译
        switch (enchantKey) {
            case "enchantment.minecraft.sharpness": return "锋利";
            case "enchantment.minecraft.smite": return "亡灵杀手";
            case "enchantment.minecraft.bane_of_arthropods": return "节肢杀手";
            case "enchantment.minecraft.knockback": return "击退";
            case "enchantment.minecraft.fire_aspect": return "火焰附加";
            case "enchantment.minecraft.looting": return "抢夺";
            case "enchantment.minecraft.sweeping": return "横扫之刃";
            case "enchantment.minecraft.efficiency": return "效率";
            case "enchantment.minecraft.silk_touch": return "精准采集";
            case "enchantment.minecraft.unbreaking": return "耐久";
            case "enchantment.minecraft.fortune": return "时运";
            case "enchantment.minecraft.power": return "力量";
            case "enchantment.minecraft.punch": return "冲击";
            case "enchantment.minecraft.flame": return "火矢";
            case "enchantment.minecraft.infinity": return "无限";
            case "enchantment.minecraft.luck_of_the_sea": return "海之眷顾";
            case "enchantment.minecraft.lure": return "饵钓";
            case "enchantment.minecraft.protection": return "保护";
            case "enchantment.minecraft.fire_protection": return "火焰保护";
            case "enchantment.minecraft.feather_falling": return "摔落保护";
            case "enchantment.minecraft.blast_protection": return "爆炸保护";
            case "enchantment.minecraft.projectile_protection": return "弹射物保护";
            case "enchantment.minecraft.respiration": return "水下呼吸";
            case "enchantment.minecraft.aqua_affinity": return "水下速掘";
            case "enchantment.minecraft.thorns": return "荆棘";
            case "enchantment.minecraft.depth_strider": return "深海探索者";
            case "enchantment.minecraft.frost_walker": return "冰霜行者";
            case "enchantment.minecraft.soul_speed": return "灵魂疾行";
            case "enchantment.minecraft.swift_sneak": return "迅捷潜行";
            case "enchantment.minecraft.mending": return "经验修补";
            case "enchantment.minecraft.vanishing_curse": return "消失诅咒";
            case "enchantment.minecraft.binding_curse": return "绑定诅咒";
            case "enchantment.minecraft.loyalty": return "忠诚";
            case "enchantment.minecraft.impaling": return "穿刺";
            case "enchantment.minecraft.riptide": return "激流";
            case "enchantment.minecraft.channeling": return "引雷";
            case "enchantment.minecraft.multishot": return "多重射击";
            case "enchantment.minecraft.quick_charge": return "快速装填";
            case "enchantment.minecraft.piercing": return "穿透";
            default:
                return enchantKey.replace("enchantment.minecraft.", "").replace("_", " ");
        }
    }

    private void renderInfo(String info, float width, ItemEntity entity, double x, double y, double z, Camera camera, MatrixStack matrices) {
        Vec3d pos = camera.getPos();
        double eyeY = y + 0.5 + (double) this.scale.getValueFloat();
        
        float scale = (float) (-0.025f * this.scale.getValueFloat());
        
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0f));
        matrices.translate(x - pos.getX(), eyeY - pos.getY(), z - pos.getZ());
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrices.scale(scale, scale, -1.0f);

        // 绘制背景矩形
        if (this.rect.getValue()) {
            float f = -width - 2.0f;
            Objects.requireNonNull(mc.textRenderer);
            Render2DUtil.drawRect(matrices, f, -1.0f, (float) width * 2.0f + 3.0f, 9.0f + 1.0f, this.bgColor.getValue());
        }

        // 绘制文字
        drawWithShadow(matrices, info, -width, 0.0f, this.textColor.getValue());

        matrices.pop();
    }

    private void drawWithShadow(MatrixStack matrices, String info, float x, float y, Color color) {
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        mc.textRenderer.draw(info, x, y, color.getRGB(), false, matrices.peek().getPositionMatrix(), 
                immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
        immediate.draw();
    }

    @Override
    public String getInfo() {
        if (this.useChinese.getValue()) {
            return "中文";
        }
        switch (this.textCase.getValue()) {
            case UpperCase:
                return "ABC";
            case LowerCase:
                return "abc";
            case TitleCase:
                return "Abc";
        }
        return "Eng";
    }

    // 英文物品名称翻译（返回标准格式）
    private String translateItemNameEnglish(String translationKey) {
        String name = translationKey.replace("item.minecraft.", "")
                .replace("block.minecraft.", "")
                .replace("_", " ");
        return formatTextCase(name);
    }

    // 格式化文本大小写
    private String formatTextCase(String text) {
        switch (textCase.getValue()) {
            case UpperCase:
                return text.toUpperCase();
            case LowerCase:
                return text.toLowerCase();
            case TitleCase:
                return toTitleCase(text);
            default:
                return text;
        }
    }

    // 转换为首字母大写
    private String toTitleCase(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder();
        boolean upperCase = true;
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) {
                result.append(c);
                upperCase = true;
            } else if (upperCase) {
                result.append(Character.toUpperCase(c));
                upperCase = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        return result.toString();
    }
}
