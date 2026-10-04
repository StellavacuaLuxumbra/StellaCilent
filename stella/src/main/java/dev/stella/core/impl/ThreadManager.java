/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  by.radioegor146.nativeobfuscator.Native
 *  com.google.common.collect.Lists
 *  net.minecraft.client.network.AbstractClientPlayerEntity
 *  net.minecraft.entity.Entity
 *  net.minecraft.util.math.BlockPos
 */
package dev.stella.core.impl;

import com.google.common.collect.Lists;
import dev.stella.stella;
import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.ClientTickEvent;
import dev.stella.api.events.impl.UpdateEvent;
import dev.stella.api.utils.Wrapper;
import dev.stella.api.utils.render.JelloUtil;
import dev.stella.api.utils.world.BlockUtil;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.impl.client.ClientSetting;
import dev.stella.mod.modules.impl.combat.AutoAnchor;
import dev.stella.mod.modules.impl.combat.AutoCrystal;
import dev.stella.mod.modules.impl.render.HoleESP;
import dev.stella.mod.modules.impl.render.PlaceRender;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

public class ThreadManager
implements Wrapper {
    public static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    public static ClientService clientService;
    public volatile Iterable<Entity> threadSafeEntityList = Collections.emptyList();
    public volatile List<AbstractClientPlayerEntity> threadSafePlayersList = Collections.emptyList();
    public volatile boolean tickRunning = false;

    public ThreadManager() {
        this.init();
    }

    public void init() {
        stella.EVENT_BUS.subscribe(this);
        clientService = new ClientService();
        clientService.setName("stellaClientService");
        clientService.setDaemon(true);
        clientService.start();
    }

    public Iterable<Entity> getEntities() {
        return this.threadSafeEntityList;
    }

    public List<AbstractClientPlayerEntity> getPlayers() {
        return this.threadSafePlayersList;
    }

    public void execute(Runnable runnable) {
        EXECUTOR.execute(runnable);
    }

    @EventListener(priority=200)
    public void onEvent(ClientTickEvent event) {
        stella.POP.onUpdate();
        stella.SERVER.onUpdate();
        if (event.isPre()) {
            JelloUtil.updateJello();
            this.tickRunning = true;
            BlockUtil.placedPos.forEach(pos -> PlaceRender.INSTANCE.create((BlockPos)pos));
            BlockUtil.placedPos.clear();
            stella.PLAYER.onUpdate();
            if (!Module.nullCheck()) {
                stella.EVENT_BUS.post(UpdateEvent.INSTANCE);
            }
        } else {
            this.tickRunning = false;
            if (ThreadManager.mc.world == null || ThreadManager.mc.player == null) {
                return;
            }
            this.threadSafeEntityList = Lists.newArrayList((Iterable)ThreadManager.mc.world.getEntities());
            this.threadSafePlayersList = Lists.newArrayList((Iterable)ThreadManager.mc.world.getPlayers());
        }
        if (!clientService.isAlive() || clientService.isInterrupted()) {
            clientService = new ClientService();
            clientService.setName("stellaService");
            clientService.setDaemon(true);
            clientService.start();
        }
    }

    public class ClientService
    extends Thread {
        @Override
        public void run() {
            while (true) {
                try {
                    while (true) {
                        if (ThreadManager.this.tickRunning) {
                            // Tick 运行时短暂休眠，避免占用 CPU
                            Thread.sleep(5);
                            continue;
                        }
                        // Tick 未运行时执行模块逻辑
                        AutoCrystal.INSTANCE.onThread();
                        HoleESP.INSTANCE.onThread();
                        AutoAnchor.INSTANCE.onThread();
                        
                        // 每轮循环后短暂休眠，避免 busy loop
                        Thread.sleep(10);
                    }
                }
                catch (InterruptedException e) {
                    // 中断异常正常处理
                    Thread.currentThread().interrupt();
                    break;
                }
                catch (Exception e) {
                    e.printStackTrace();
                    if (ClientSetting.INSTANCE.debug.getValue()) {
                        CommandManager.sendMessage("\u00a74An error has occurred [Thread] Message: [" + e.getMessage() + "]");
                    }
                    // 错误后添加冷却时间，避免频繁报错
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }
}
