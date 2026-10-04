package dev.stella.mod.modules.impl.client.hud;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.PacketEvent;
import dev.stella.api.utils.render.TextUtil;
import dev.stella.core.impl.FontManager;
import dev.stella.mod.modules.HudModule;
import dev.stella.mod.modules.impl.client.ClickGui;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.EnumSetting;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gui.DrawContext;

public class PacketHudModule extends HudModule {
    public static PacketHudModule INSTANCE;
    private final AtomicInteger sendCount = new AtomicInteger(0);
    private final AtomicInteger receiveCount = new AtomicInteger(0);
    private int lastSendCount = 0;
    private int lastReceiveCount = 0;
    
    private final BooleanSetting compact = this.add(new BooleanSetting("Compact", false));
    private final EnumSetting<ArrowDirection> arrowDirection = this.add(new EnumSetting<>("ArrowDirection", ArrowDirection.Left, () -> this.compact.isOpen()));
    private final BooleanSetting showTotal = this.add(new BooleanSetting("Total", false, () -> !this.compact.isOpen()));
    private final BooleanSetting lowerCase = this.add(new BooleanSetting("LowerCase", false));
    private final BooleanSetting perSecond = this.add(new BooleanSetting("PerSecond", true));

    public PacketHudModule() {
        super("PacketHUD", "", "数据包 HUD", 2, 110, Corner.RightTop);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.sendCount.set(0);
        this.receiveCount.set(0);
        this.lastSendCount = 0;
        this.lastReceiveCount = 0;
    }

    @Override
    public void onDisable() {
        this.sendCount.set(0);
        this.receiveCount.set(0);
        this.lastSendCount = 0;
        this.lastReceiveCount = 0;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (PacketHudModule.mc.player == null) {
            this.clearHudBounds();
            return;
        }

        String text = this.getPacketText();
        if (this.lowerCase.getValue()) {
            text = text.toLowerCase();
        }

        int w = HudSetting.useFont() ? (int)Math.ceil(FontManager.ui.getWidth(text)) : PacketHudModule.mc.textRenderer.getWidth(text);
        int h;
        if (HudSetting.useFont()) {
            h = (int)Math.ceil(FontManager.ui.getFontHeight());
        } else {
            Objects.requireNonNull(PacketHudModule.mc.textRenderer);
            h = 9;
        }

        int x = this.getHudRenderX(w);
        int y = this.getHudRenderY(h);

        int color = this.getHudColor(0.0);
        TextUtil.drawString(context, text, x, y, color, HudSetting.useFont(), HudSetting.useShadow());

        this.setHudBounds(x, y, Math.max(1, w), Math.max(1, h));
    }

    private String getPacketText() {
        int currentSend = this.sendCount.get();
        int currentReceive = this.receiveCount.get();

        int send = this.perSecond.getValue() ? (currentSend - this.lastSendCount) : currentSend;
        int receive = this.perSecond.getValue() ? (currentReceive - this.lastReceiveCount) : currentReceive;
        int total = send + receive;

        // 更新上一次的计数（用于计算每秒）
        if (this.perSecond.getValue()) {
            this.lastSendCount = currentSend;
            this.lastReceiveCount = currentReceive;
        }

        // 紧凑模式：packet: 2←1 或 packet: 1→2
        if (this.compact.getValue()) {
            String arrow = this.arrowDirection.getValue() == ArrowDirection.Left ? "←" : "→";
            if (this.arrowDirection.getValue() == ArrowDirection.Left) {
                return "packet: " + send + arrow + receive;
            } else {
                return "packet: " + receive + arrow + send;
            }
        }

        // 普通模式
        StringBuilder text = new StringBuilder();
        text.append("Send: ");
        text.append(send);
        
        if (this.showTotal.getValue()) {
            text.append(" | Total: ");
            text.append(total);
        }
        
        text.append(" | Recv: ");
        text.append(receive);

        return text.toString();
    }

    @EventListener
    public void onPacketSend(PacketEvent.Send event) {
        this.sendCount.incrementAndGet();
    }

    @EventListener
    public void onPacketReceive(PacketEvent.Receive event) {
        this.receiveCount.incrementAndGet();
    }

    private int getHudColor(double delay) {
        ClickGui gui = ClickGui.getInstance();
        return gui == null ? -1 : gui.getColor(delay).getRGB();
    }

    public enum ArrowDirection {
        Left,
        Right
    }
}
