package dev.stella.core.impl;

import dev.stella.mod.modules.impl.client.ClientSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 加载画面图片管理器
 * 用于在加载画面随机显示图片
 */
public class LoadingImageManager {
    private static final Logger LOG = LoggerFactory.getLogger("Stella-LoadingImage");
    private static LoadingImageManager INSTANCE;
    
    private final List<LoadingImage> images = new ArrayList<>();
    private LoadingImage currentImage;
    private final Random random = new Random();
    private boolean initialized = false;
    private boolean initializing = false;
    public boolean isInitialized() { return initialized; }
    public boolean isInitializing() { return initializing; }

    // 配置选项
    private boolean enabled = true;
    private int displayMode = 0; // 0=单张随机，1=多张随机，2=轮播
    private long lastSwitchTime = 0;
    private long switchInterval = 3000; // 轮播间隔（毫秒）
    
    private LoadingImageManager() {
    }
    
    public static LoadingImageManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LoadingImageManager();
        }
        return INSTANCE;
    }
    
    /**
     * 初始化加载图片（异步版本）
     */
    public void init() {
        if (this.initialized || this.initializing) {
            return;
        }
        this.initializing = true;

        // 使用异步线程加载图片，避免阻塞主线程
        Thread loadThread = new Thread(() -> {
            try {
                // 加载 8 张图片
                for (int i = 1; i <= 8; i++) {
                    Identifier textureId = Identifier.of("stella", "loading/loading_" + i + ".png");
                    LoadingImage image = this.loadImage(textureId);
                    if (image != null) {
                        synchronized (this.images) {
                            this.images.add(image);
                        }
                        LOG.info("[LoadingImage] Loaded image: loading_{}.png", i);
                    }
                }

                synchronized (this.images) {
                    if (!this.images.isEmpty()) {
                        this.selectRandomImage();
                        this.initialized = true;
                        LOG.info("[LoadingImage] Initialized with {} images", this.images.size());
                    } else {
                        LOG.warn("[LoadingImage] No images found!");
                    }
                }
            } catch (Exception e) {
                LOG.error("[LoadingImage] Failed to initialize", e);
            } finally {
                this.initializing = false;
            }
        }, "Stella-LoadingImage-Loader");
        loadThread.setDaemon(true);
        loadThread.start();
    }

    /**
     * 立即同步加载（用于需要立即显示的场景）
     */
    public void initSync() {
        if (this.initialized) {
            return;
        }
        try {
            for (int i = 1; i <= 8; i++) {
                Identifier textureId = Identifier.of("stella", "loading/loading_" + i + ".png");
                LoadingImage image = this.loadImage(textureId);
                if (image != null) {
                    this.images.add(image);
                }
            }
            if (!this.images.isEmpty()) {
                this.selectRandomImage();
                this.initialized = true;
            }
        } catch (Exception e) {
            LOG.error("[LoadingImage] Failed to sync initialize", e);
        }
    }
    
    /**
     * 加载单张图片
     */
    private LoadingImage loadImage(Identifier textureId) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            
            // 尝试从资源管理器加载
            Resource resource = mc.getResourceManager().getResource(textureId).orElse(null);
            if (resource == null) {
                return null;
            }
            
            try (InputStream input = resource.getInputStream()) {
                NativeImage nativeImage = NativeImage.read(input);
                if (nativeImage == null) {
                    return null;
                }
                
                NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
                
                return new LoadingImage(
                    textureId,
                    texture,
                    nativeImage.getWidth(),
                    nativeImage.getHeight()
                );
            }
        } catch (IOException e) {
            LOG.error("[LoadingImage] Failed to load image: {}", textureId, e);
            return null;
        }
    }
    
    /**
     * 随机选择一张图片
     */
    public void selectRandomImage() {
        if (this.images.isEmpty()) {
            return;
        }
        
        // 确保每次选择都不一样（除非只有一张图片）
        if (this.images.size() > 1) {
            int newIndex;
            do {
                newIndex = this.random.nextInt(this.images.size());
            } while (this.currentImage != null && 
                     newIndex == this.images.indexOf(this.currentImage));
            
            this.currentImage = this.images.get(newIndex);
        } else {
            this.currentImage = this.images.get(0);
        }
        
        this.lastSwitchTime = System.currentTimeMillis();
    }
    
    /**
     * 更新轮播（如果启用）
     */
    public void update() {
        // 从 ClientSetting 获取配置
        ClientSetting setting = ClientSetting.INSTANCE;
        if (setting != null && setting.loadingImageMode.getValue() == ClientSetting.LoadingMode.Slideshow) {
            long now = System.currentTimeMillis();
            if (now - this.lastSwitchTime >= this.switchInterval) {
                this.selectRandomImage();
            }
        }
    }
    
    /**
     * 获取当前图片
     */
    public LoadingImage getCurrentImage() {
        return this.currentImage;
    }
    
    /**
     * 计算适配屏幕的渲染参数
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @return 渲染参数 [x, y, width, height]
     */
    public float[] calculateRenderBounds(int screenWidth, int screenHeight, LoadingImage image) {
        if (image == null) {
            return new float[]{0, 0, screenWidth, screenHeight};
        }
        
        float imageAspect = (float)image.width / (float)image.height;
        float screenAspect = (float)screenWidth / (float)screenHeight;
        
        float renderWidth, renderHeight, startX, startY;
        
        // Contain 模式：保持宽高比，完整显示图片
        if (imageAspect > screenAspect) {
            // 图片更宽，以宽度为基准
            renderWidth = screenWidth;
            renderHeight = screenWidth / imageAspect;
        } else {
            // 图片更高，以高度为基准
            renderHeight = screenHeight;
            renderWidth = screenHeight * imageAspect;
        }
        
        // 居中显示
        startX = (screenWidth - renderWidth) / 2.0f;
        startY = (screenHeight - renderHeight) / 2.0f;
        
        return new float[]{startX, startY, renderWidth, renderHeight};
    }
    
    /**
     * 设置是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * 是否启用
     */
    public boolean isEnabled() {
        ClientSetting setting = ClientSetting.INSTANCE;
        boolean enabled = setting != null && setting.loadingImage.getValue();
        return enabled && this.initialized && this.currentImage != null;
    }
    
    /**
     * 设置显示模式
     * @param mode 0=单张随机，1=多张随机，2=轮播
     */
    public void setDisplayMode(int mode) {
        this.displayMode = mode;
    }
    
    /**
     * 设置轮播间隔
     */
    public void setSwitchInterval(long intervalMs) {
        this.switchInterval = intervalMs;
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        for (LoadingImage image : this.images) {
            if (image.texture != null) {
                image.texture.close();
            }
        }
        this.images.clear();
        this.currentImage = null;
        this.initialized = false;
    }
    
    /**
     * 加载图片数据类
     */
    public static class LoadingImage {
        public final Identifier textureId;
        public final NativeImageBackedTexture texture;
        public final int width;
        public final int height;
        
        public LoadingImage(Identifier textureId, NativeImageBackedTexture texture, int width, int height) {
            this.textureId = textureId;
            this.texture = texture;
            this.width = width;
            this.height = height;
        }
    }
}
