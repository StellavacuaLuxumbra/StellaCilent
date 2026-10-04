package dev.stella.mod.modules.impl.misc;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.ClientTickEvent;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.EnumSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import dev.stella.mod.modules.settings.impl.StringSetting;

import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AutoKouZi extends Module {

    // ── 模式枚举 ──────────────────────────────────────────────────────────────
    public enum Mode {
        Power,  // CiHui1.txt 骂人词库
        Safe    // CiHui2.txt 三字经
    }

    // ── 设置项 ────────────────────────────────────────────────────────────────
    private final EnumSetting<Mode> mode =
            this.add(new EnumSetting<>("Mode", Mode.Power));

    private final SliderSetting delay =
            this.add(new SliderSetting("Delay", 3.0, 0.0, 20.0, 1.0));

    private final BooleanSetting whisperMode =
            this.add(new BooleanSetting("WhisperMode", false));

    private final StringSetting targetPlayer =
            this.add(new StringSetting("TargetPlayer", ""));

    private final StringSetting whisperCmd =
            this.add(new StringSetting("WhisperCmd", "msg"));

    private final BooleanSetting commandMode =
            this.add(new BooleanSetting("CommandMode", false));

    // ── 内部状态 ──────────────────────────────────────────────────────────────
    private final List<String> messages = new ArrayList<>();
    private final Random random = new Random();
    private long lastSendTime = 0L;
    private Mode lastMode = null;

    // ─────────────────────────────────────────────────────────────────────────

    public AutoKouZi() {
        super("AutoKouZi", Module.Category.Misc);
        this.setChinese("自动扣字");
    }

    // ── 开启时初始化 ──────────────────────────────────────────────────────────
    @Override
    public void onEnable() {
        if (mc.player == null) {
            sendTip("错误：未进入游戏");
            this.toggle();
            return;
        }
        if (whisperMode.getValue() && targetPlayer.getValue().trim().isEmpty()) {
            sendTip("错误：私聊模式需填写目标玩家");
            this.toggle();
            return;
        }
        loadMessages();
        if (messages.isEmpty()) {
            sendTip("错误：词库为空");
            this.toggle();
            return;
        }
        lastMode = mode.getValue();
        lastSendTime = System.currentTimeMillis();
        sendTip("自动扣字已启动 | 词库共 " + messages.size() + " 条");
    }

    @Override
    public void onDisable() {
        sendTip("自动扣字已停止");
    }

    // ── 每 tick 触发 ──────────────────────────────────────────────────────────
    @EventListener
    public void onTick(ClientTickEvent event) {
        if (!event.isPre()) return;
        if (nullCheck()) return;
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        // 模式切换时重新加载词库
        if (mode.getValue() != lastMode) {
            lastMode = mode.getValue();
            loadMessages();
            if (messages.isEmpty()) {
                sendTip("错误：词库切换后为空");
                return;
            }
            sendTip("词库已切换，共 " + messages.size() + " 条");
        }

        long now = System.currentTimeMillis();
        long delayMs = (long) (delay.getValue() * 1000.0);
        if (now - lastSendTime < delayMs) return;

        try {
            if (messages.isEmpty()) return;

            String msg = messages.get(random.nextInt(messages.size()));
            String finalContent;

            if (whisperMode.getValue()) {
                // 私聊模式：/msg <目标> <内容>
                finalContent = "/" + whisperCmd.getValue().trim()
                        + " " + targetPlayer.getValue().trim()
                        + " " + msg;
            } else {
                finalContent = msg;
            }

            if (whisperMode.getValue() || commandMode.getValue()) {
                // 命令模式：去掉开头的 /
                String cmd = finalContent.startsWith("/")
                        ? finalContent.substring(1)
                        : finalContent;
                mc.getNetworkHandler().sendCommand(cmd);
            } else {
                // 普通聊天
                mc.getNetworkHandler().sendChatMessage(finalContent);
            }

            lastSendTime = now;

        } catch (Exception e) {
            sendTip("发送失败：" + e.getMessage());
        }
    }

    // ── 加载词库 ──────────────────────────────────────────────────────────────
    /**
     * 加载顺序：
     * 1. 先找 .minecraft/stella/CiHui1.txt 或 CiHui2.txt（用户自定义）
     * 2. 找不到则从 jar 内置资源解压到上述路径
     */
    private void loadMessages() {
        messages.clear();
        String fileName = (mode.getValue() == Mode.Power) ? "CiHui1.txt" : "CiHui2.txt";

        // 存放到 .minecraft/stella/ 目录下
        File dir = new File(mc.runDirectory, "stella");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, fileName);

        if (!file.exists()) {
            extractResource(fileName, file);
        }

        try (BufferedReader reader = new BufferedReader(
                new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    messages.add(line.trim());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 从 jar 内 /assets/stella/texts/ 解压词库文件到磁盘。
     * 如果 jar 里也没有，则创建空文件。
     */
    private void extractResource(String resourceName, File destination) {
        try {
            InputStream is = getClass().getResourceAsStream(
                    "/assets/stella/texts/" + resourceName);
            if (is != null) {
                Files.copy(is, destination.toPath());
                is.close();
            } else {
                // jar 里没有内置词库时创建空文件，让用户自行填入
                destination.createNewFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── 工具方法 ──────────────────────────────────────────────────────────────
    private void sendTip(String text) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("[扣字机] " + text), false);
        }
    }
}
