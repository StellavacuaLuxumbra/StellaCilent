# Stella Client 优化实施计划

> 目标：对标 SunCat-Client，实现完整的 ClickGUI、模块系统、HUD、渲染、配置
> 最后更新：2026-10-04

---

## 状态变更（2026-10-04）：目标改为直接采纳 SunCat 源码

本计划原定"自行实现以对标 SunCat"的各阶段**已停止执行**，改为：

1. 将 `othercilent/SunCat-Client` 源码整体复制为 `stella/` 并品牌化重命名
   （`dev.suncat`→`dev.stella`、`suncat*`→`stella*`、资源合并到 `assets/stella/`）。
2. 把 Stella 原有特性（LuaJ 脚本运行时、共享内存 IPC）嫁接进 `stella/`，
   入口为模块 `StellaBridge`（默认开启）。
3. 删除全部混淆设施（protection / obfuscate.json / crazy-obfuscator /
   build.gradle 加密任务 / Lua XOR / RaspProtection），Lua 脚本改为明文加载。
4. 构建改用 `cd stella && gradlew.bat build`（Gradle 8.11 + fabric-loom 1.9.2），
   产物 `stella-3.0.0.jar`，已于 2026-10-04 编译通过。

因此下文阶段 1-8（自研渲染/模块/HUD/Mixin/配置）**不再适用**，仅在需要参考
实现细节、或为 `executer/` 做对照时查阅。新的待办见 `HANDOFF.md` 顶部
"2026-10-04 架构变更"一节。

---

## 阶段 1：渲染基础 + ClickGUI 视觉对齐（P0）

### 1.1 Render2DUtil.java — 2D 绘图原语

**文件**：`executer/src/main/java/dev/stella/executer/gui/render/Render2DUtil.java`

**实现方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| `fill` | `(DrawContext, x, y, x2, y2, color)` | 矩形填充 |
| `fillRect` | `(DrawContext, x, y, w, h, color)` | 矩形填充 (x,y,w,h) |
| `drawRoundedRect` | `(DrawContext, x, y, w, h, radius, color)` | 圆角矩形填充 |
| `drawRoundedStroke` | `(DrawContext, x, y, w, h, radius, color, segments)` | 圆角矩形描边 |
| `horizontalGradient` | `(DrawContext, x1,y1,x2,y2, startColor, endColor)` | 水平渐变 |
| `verticalGradient` | `(DrawContext, x1,y1,x2,y2, startColor, endColor)` | 垂直渐变 |
| `drawCircle` | `(DrawContext, cx, cy, radius, color, segments)` | 圆形 |
| `drawPill` | `(DrawContext, x, y, w, h, color)` | 胶囊形 |
| `drawGlow` | `(DrawContext, x, y, w, h, color)` | 发光效果 (4三角渐变) |
| `drawLine` | `(DrawContext, x1,y1, x2,y2, color, width)` | 线段 |
| `isHovered` | `(mx, my, x, y, w, h) → boolean` | 命中检测 |
| `drawGradientRoundedRect` | `(DrawContext, x,y,w,h, r, topColor, bottomColor)` | 渐变圆角 |

**圆角矩形实现**：
```
1. 绘制3个重叠矩形填充主体：
   - 中心条: (x+r, y, w-2r, h)
   - 左条:   (x, y+r, r, h-2r)
   - 右条:   (x+w-r, y+r, r, h-2r)
2. 绘制4个 TRIANGLE_FAN 四分之一圆：
   - 左上: center(x+r, y+r), 角度 PI → 3PI/2
   - 右上: center(x+w-r, y+r), 角度 3PI/2 → 2PI
   - 右下: center(x+w-r, y+h-r), 角度 0 → PI/2
   - 左下: center(x+r, y+h-r), 角度 PI/2 → PI
3. 每个角: 48 segments
```

### 1.2 ColorUtil.java — 颜色工具

**文件**：`executer/src/main/java/dev/stella/executer/gui/render/ColorUtil.java`

**实现方法**：
- `fadeColor(start, end, progress) → int` — RGBA 线性插值
- `injectAlpha(color, alpha) → int` — 替换 alpha 通道: `color & 0xFFFFFF | alpha << 24`
- `hslToColor(h, s, l, a) → Color` — HSL 转 RGB
- `pulseColor(color, index, count, speed) → int` — 脉冲亮度

### 1.3 Animation.java — 动画系统

**文件**：`executer/src/main/java/dev/stella/executer/gui/render/Animation.java`

**实现**：
```java
public class Animation {
    private float value;
    private float target;
    private long lastTime;
    private long duration;  // ms
    private Easing easing;

    public float get(float target, long duration, Easing easing) {
        // 如果目标改变，开始动画
        // 使用 easing 函数插值
        return value;
    }
}

public enum Easing {
    LINEAR, CUBIC_IN_OUT, SINE_OUT, BACK_IN_OUT
}
```

### 1.4 ClickGuiScreen.java — 重写

**文件**：`executer/src/main/java/dev/stella/executer/gui/ClickGuiScreen.java`

**颜色方案**：
```java
// 默认 Dark 主题
static final int COL_BG = 0xE61E1E1E;           // 面板背景 30,30,30,236
static final int COL_HEADER = 0xD20078D4;       // 标题栏 0,120,212,210
static final int COL_MODULE_OFF = 0xE61E1E1E;   // 模块(关) 同背景
static final int COL_MODULE_ON = 0x960078D4;     // 模块(开) 0,120,12,150
static final int COL_HOVER = 0xC8323232;         // 悬停 50,50,50,200
static final int COL_TEXT = 0xDCDCDC;            // 普通文字 220,220,220
static final int COL_TEXT_ON = 0xFFFFFF;         // 开启文字 白
static final int COL_BORDER = 0x66333366;        // 边框 51,51,102
static final int COL_SEARCH_BG = 0xC81E1E1E;     // 搜索栏背景
static final int COL_SEARCH_BORDER = 0x96505050; // 搜索栏边框
```

**尺寸常量**：
```java
static final int CATEGORY_WIDTH = 95;
static final int CATEGORY_HEIGHT = 17;
static final int MODULE_BTN_HEIGHT = 13;
static final int PANEL_SPACING = 6;
static final int CORNER_RADIUS = 10;
static final int SEARCH_WIDTH = 200;
static final int SEARCH_HEIGHT = 20;
static final int SEARCH_RADIUS = 6;
```

**面板布局**：
```java
// 分类顺序
Category[] order = {COMBAT, MOVEMENT, RENDER, PLAYER, MISC};

// 居中计算
int totalWidth = order.length * (CATEGORY_WIDTH + PANEL_SPACING) - PANEL_SPACING;
int startX = (screenWidth - totalWidth) / 2;
int startY = screenHeight / 6;

// 每个面板高度 = 标题 + 模块数 * 按钮高度 + 间距
```

**动画**：
- 打开：400ms BackInOut，缩放 0.92→1.0，Y偏移 20→0
- 悬停：100ms CubicInOut
- 开关：160ms CubicInOut
- 搜索框聚焦：边框色过渡到 accent

**拖拽**：
- 鼠标按下标题栏 → 记录偏移
- 拖拽时更新面板位置
- 释放时停止拖拽

### 1.5 Panel.java — 分类面板组件

**文件**：`executer/src/main/java/dev/stella/executer/gui/component/Panel.java`

```java
class Panel {
    Category category;
    int x, y, width, totalHeight;
    boolean dragging;
    List<ModuleButton> buttons;

    void render(DrawContext ctx, int mouseX, int mouseY, TextRenderer font);
    boolean mouseClicked(int mouseX, int mouseY, int button);
    void drag(int dx, int dy);
}
```

### 1.6 ModuleButton.java — 模块按钮组件

**文件**：`executer/src/main/java/dev/stella/executer/gui/component/ModuleButton.java`

```java
class ModuleButton {
    Module module;
    int x, y, width, height;
    Animation hoverAnim;
    Animation toggleAnim;

    void render(DrawContext ctx, int mouseX, int mouseY, TextRenderer font);
    boolean mouseClicked(int mouseX, int mouseY, int button);
}
```

### 1.7 搜索栏

**实现**：
- 位置：水平居中, Y=28
- 尺寸：200×20px, 圆角6px
- 背景：`COL_SEARCH_BG`
- 边框：聚焦时 accent 色，否则灰色
- 占位符："Search modules..." 灰色
- 输入：监听键盘事件，过滤模块名
- 光标：1px白色，500ms闪烁

### 1.8 雪花粒子（可选）

**实现**：
- 粒子数：120
- 速度：38
- 大小：1.8
- 透明度：160
- 风力：10
- 对象池回收
- sin/cos 查找表加速

---

## 阶段 2：模块设置系统（P0）

### 2.1 Setting 基类

**文件**：`executer/src/main/java/dev/stella/executer/gui/setting/Setting.java`

```java
public abstract class Setting {
    protected String name;
    protected BooleanSupplier visibility;  // null = always visible

    public String getName();
    public boolean isVisible();
}
```

### 2.2 BooleanSetting

**文件**：`executer/src/main/java/dev/stella/executer/gui/setting/BooleanSetting.java`

```java
public class BooleanSetting extends Setting {
    private boolean value;
    private boolean defaultValue;
    private BooleanSetting parent;  // 可选父级展开
    private boolean open;

    public boolean getValue();
    public void setValue(boolean v);
    public void toggle();
    public boolean hasParent();
    public boolean isOpen();
    public void setOpen(boolean open);
}
```

### 2.3 SliderSetting

**文件**：`executer/src/main/java/dev/stella/executer/gui/setting/SliderSetting.java`

```java
public class SliderSetting extends Setting {
    private double value;
    private double defaultValue;
    private double minValue;
    private double maxValue;
    private double increment;
    private String suffix;  // 后缀如 "blocks", "cps"

    public double getValue();
    public void setValue(double v);
    public String getDisplayValue();  // "16.0 blocks"
}
```

### 2.4 EnumSetting

**文件**：`executer/src/main/java/dev/stella/executer/gui/setting/EnumSetting.java`

```java
public class EnumSetting<T extends Enum<T>> extends Setting {
    private T value;
    private T defaultValue;

    public T getValue();
    public void setValue(T v);
    public T[] getValues();  // 所有枚举选项
    public int getOrdinal();
}
```

### 2.5 ColorSetting

**文件**：`executer/src/main/java/dev/stella/executer/gui/setting/ColorSetting.java`

```java
public class ColorSetting extends Setting {
    private int value;  // ARGB int
    private int defaultValue;
    private boolean rainbow;

    public int getColor();
    public boolean isRainbow();
    public void setRainbow(boolean r);
}
```

### 2.6 BindSetting

**文件**：`executer/src/main/java/dev/stella/executer/gui/setting/BindSetting.java`

```java
public class BindSetting extends Setting {
    private int value;  // GLFW key code, -1 = none
    private int defaultValue;
    private boolean holding;

    public int getValue();
    public void setValue(int key);
    public String getKeyName();  // "G", "RIGHT_SHIFT", "NONE"
}
```

### 2.7 模块设置注册

**每个模块添加设置**：
```java
// ESP
new SliderSetting("Range", 16.0, 1.0, 64.0, 0.5, "blocks")
new EnumSetting<>("Mode", Mode.BOX)  // BOX, OUTLINE, TWO_D
new ColorSetting("Color", 0x0078D4)

// KillAura
new SliderSetting("Range", 4.0, 1.0, 6.0, 0.1, "blocks")
new SliderSetting("CPS", 10.0, 1.0, 20.0, 1.0, "")
new EnumSetting<>("Mode", Mode.SINGLE)  // SINGLE, SWITCH
new BooleanSetting("Players Only", true)

// Speed
new SliderSetting("Speed", 1.5, 0.5, 5.0, 0.1, "x")
new EnumSetting<>("Mode", Mode.VANILLA)  // VANILLA, PACKET

// ... 其他模块类似
```

---

## 阶段 3：C++ 模块 tick 循环（P1）

### 3.1 main.cpp 修改

**新增**：
```cpp
// 模块 tick 循环（每收到 EV_TICK 执行）
void ModuleTick(stella::Ring& tx) {
    // ESP
    if (g_modules[0]) {
        SendGetEntities(tx, 16.0f);
        // 收到 EV_ENTITY_LIST 后自动绘制 CMD_DRAW_BOX
    }
    // KillAura
    if (g_modules[1]) {
        SendGetEntities(tx, 4.0f);
        // 收到最近实体后 SendAttack(entityId)
    }
    // Fullbright
    if (g_modules[2]) {
        SendSetGamma(tx, 1000.0f);
    }
    // Speed
    if (g_modules[3]) {
        // 根据玩家朝向计算速度向量
        // SendSetVelocity(tx, vx, vy, vz);
    }
    // Sprint
    if (g_modules[11]) {
        SendSprint(tx, true);
    }
}
```

### 3.2 新增 C++ 命令函数

```cpp
bool SendBreakBlock(stella::Ring& tx, int32_t x, int32_t y, int32_t z);
bool SendUseItem(stella::Ring& tx);
bool SendMoveTo(stella::Ring& tx, double x, double y, double z);
bool SendGetInventory(stella::Ring& tx);
bool SendAutoDamage(stella::Ring& tx, int32_t entityId);
```

### 3.3 实体查找优化

```cpp
struct EntityInfo {
    int id;
    int type;
    float x, y, z;
    float health;
    bool isPlayer;
};

// 收到 EV_ENTITY_LIST 后解析到 vector<EntityInfo>
// 按距离排序，找最近实体
float DistanceTo(EntityInfo& e, float px, float py, float pz);
EntityInfo* FindNearest(vector<EntityInfo>& list, float px, float py, float pz);
```

### 3.4 ESP 实现

```cpp
// 在 EV_RENDER_3D 事件处理中：
if (g_modules[0]) {  // ESP
    for (auto& e : lastEntityList) {
        float dx = e.x - camX, dy = e.y - camY, dz = e.z - camZ;
        float dist = sqrt(dx*dx + dy*dy + dz*dz);
        if (dist < 64.0f) {
            // 绘制线框盒
            SendDrawBox(tx, e.x - 0.5f, e.y, e.z - 0.5f, 1.0f, 1.8f, 1.0f, 0xFF00FF00);
        }
    }
}
```

---

## 阶段 4：HUD 系统（P1-P2）

### 4.1 HudModule 基类

**文件**：`executer/src/main/java/dev/stella/executer/gui/hud/HudModule.java`

```java
public abstract class HudModule {
    protected SliderSetting x, y;
    protected EnumSetting<Corner> corner;

    public enum Corner { LEFT_TOP, RIGHT_TOP, LEFT_BOTTOM, RIGHT_BOTTOM }

    public abstract void render(DrawContext ctx, TextRenderer font, float tickDelta);

    public int getRenderX(int screenWidth, int elementWidth) {
        if (corner.getValue().isRight()) return screenWidth - elementWidth - (int)x.getValue();
        return (int)x.getValue();
    }

    public int getRenderY(int screenHeight, int elementHeight) {
        if (corner.getValue().isBottom()) return screenHeight - elementHeight - (int)y.getValue();
        return (int)y.getValue();
    }
}
```

### 4.2 ArrayListHud — 模块列表

**文件**：`executer/src/main/java/dev/stella/executer/gui/hud/ArrayListHud.java`

```java
public class ArrayListHud extends HudModule {
    // 设置
    BooleanSetting lowerCase;
    BooleanSetting rightAlign;
    SliderSetting enableLength;  // 入场动画时长 ms

    // 每个模块的动画状态
    Map<Module, Animation> widthAnims;
    Map<Module, Animation> fadeAnims;

    @Override
    public void render(DrawContext ctx, TextRenderer font, float tickDelta) {
        // 按名称长度降序排序
        // 从右上角向下排列
        // 每个模块：
        //   1. 更新入场动画 (width: 0→textWidth, alpha: 0→1)
        //   2. 绘制背景 (accent色 半透明)
        //   3. 绘制文字 (白色)
        //   4. 绘制右侧竖条 (accent色)
    }
}
```

### 4.3 CoordsHud — 坐标显示

**文件**：`executer/src/main/java/dev/stella/executer/gui/hud/CoordsHud.java`

```java
public class CoordsHud extends HudModule {
    @Override
    public void render(DrawContext ctx, TextRenderer font, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        String text = String.format("XYZ §f%.1f, %.1f, %.1f", x, y, z);

        // 地狱维度转换
        if (mc.world.getRegistryKey() == World.NETHER) {
            text += String.format(" §7[§f%.1f, %.1f§7]", x * 8, z * 8);
        } else if (mc.world.getRegistryKey() == World.OVERWORLD) {
            // 检测附近地狱门，显示地狱坐标
        }

        int color = ClickGui.getAccentColor(0);
        ctx.drawTextWithShadow(font, text, getRenderX(...), getRenderY(...), color);
    }
}
```

### 4.4 FpsHud — 帧率

```java
public class FpsHud extends HudModule {
    @Override
    public void render(DrawContext ctx, TextRenderer font, float tickDelta) {
        String text = "FPS: " + MinecraftClient.getCurrentFps();
        ctx.drawTextWithShadow(font, text, x, y, ClickGui.getAccentColor(0));
    }
}
```

### 4.5 WaterMarkHud — 标题

```java
public class WaterMarkHud extends HudModule {
    SliderSetting yOffset;
    StringSetting title;

    @Override
    public void render(DrawContext ctx, TextRenderer font, float tickDelta) {
        String text = title.getValue();  // 默认 "Stella Client"
        // 绘制圆角背景 + 文字
        Render2DUtil.drawRoundedRect(ctx, x-4, y-2, w+8, h+4, 4, 0x80000000);
        ctx.drawTextWithShadow(font, text, x, y, ClickGui.getAccentColor(0));
    }
}
```

### 4.6 TargetHud — 最近目标

```java
public class TargetHud extends HudModule {
    @Override
    public void void render(DrawContext ctx, TextRenderer font, float tickDelta) {
        // 查找最近玩家实体
        // 显示: "Name HP:20.0 Dist:3.5"
        // 绘制圆角背景 + 血条
    }
}
```

### 4.7 ArmorHud — 盔甲条

### 4.8 DirectionHud — 朝向

---

## 阶段 5：Mixin 钩子（P2-P3）

### 5.1 添加 Mixin 支持

**修改** `executer/build.gradle`：
```gradle
dependencies {
    // ... existing
    annotationProcessor "net.fabricmc:sponge-mixin-0.8.7"
}

mixin {
    add sourceSets.main, "stella.refmap.json"
    config "stella.mixins.json"
}
```

**创建** `executer/src/main/resources/stella.mixins.json`：
```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "dev.stella.executer.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": [
    "MixinClientPlayerEntity",
    "MixinClientConnection",
    "MixinEntity",
    "MixinWorld",
    "MixinGameRenderer"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

### 5.2 Mixin 实现

**MixinClientPlayerEntity.java** — 移动/跳跃事件：
```java
@Mixin(ClientPlayerEntity.class)
public class MixinClientPlayerEntity {
    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void onSendMovementPackets(CallbackInfo ci) {
        // 发送移动事件到 EventBus
    }

    @Inject(method = "jump", at = @At("HEAD"))
    private void onJump(CallbackInfo ci) {
        // 发送跳跃事件
    }
}
```

**MixinClientConnection.java** — 数据包事件：
```java
@Mixin(ClientConnection.class)
public class MixinClientConnection {
    @Inject(method = "send(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"))
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        // 发送数据包事件
    }

    @Inject(method = "handlePacket", at = @At("HEAD"))
    private static void onHandlePacket(Packet<?> packet, PacketListener listener, CallbackInfo ci) {
        // 发送接收数据包事件
    }
}
```

---

## 阶段 6：配置系统（P2-P3）

### 6.1 ConfigManager.java

**文件**：`executer/src/main/java/dev/stella/executer/config/ConfigManager.java`

**存档格式**：
```
# 模块状态
ESP_state:false
KillAura_state:false
Fullbright_state:false

# 模块设置
ESP_Range:16.0
ESP_Mode:BOX
ESP_Color:6737151
KillAura_Range:4.0
KillAura_CPS:10.0
KillAura_Mode:SINGLE
Speed_Speed:1.5
Speed_Mode:VANILLA
```

**实现**：
```java
public class ConfigManager {
    private static final File CONFIG_DIR = new File("stella");
    private static final File OPTIONS_FILE = new File(CONFIG_DIR, "options.txt");

    public static void save() {
        // 遍历所有模块
        // 写入 ModuleName_state:true/false
        // 遍历每个模块的设置
        // 写入 ModuleName_SettingName:value
    }

    public static void load() {
        // 读取 options.txt
        // 解析每行 key:value
        // 应用到模块状态和设置
    }

    public static void saveNamed(String name) {
        // 保存到 cfg/<name>.cfg
    }

    public static void loadNamed(String name) {
        // 从 cfg/<name>.cfg 加载
    }

    public static List<String> listConfigs() {
        // 列出 cfg/ 下所有 .cfg 文件
    }
}
```

---

## 阶段 7：聊天命令（P3）

### 7.1 CommandManager.java

**文件**：`executer/src/main/java/dev/stella/executer/command/CommandManager.java`

**命令列表**：
| 命令 | 说明 | 用法 |
|------|------|------|
| `.toggle <module>` | 切换模块 | `.toggle esp` |
| `.bind <module> <key>` | 设置绑定 | `.bind killaura G` |
| `.config save <name>` | 保存配置 | `.config save combat` |
| `.config load <name>` | 加载配置 | `.config load combat` |
| `.tp <x> <y> <z>` | 传送 | `.tp 100 64 200` |
| `.help` | 帮助 | `.help` |

**实现**：通过 MixinChatHud 拦截聊天消息，检测前缀 `.`

---

## 阶段 8：其他 HUD 元素（P3）

### 8.1 InfoHud — 服务器信息
```
显示: "Server: localhost | Mem: 256/512MB | Ping: 45ms"
```

### 8.2 BiomeHud — 生物群系
```
显示: "Biome: Plains"
```

### 8.3 PacketHud — 数据包计数
```
显示: "Sent: 1234 | Recv: 5678"
```

---

## 文件清单（最终）

### 新增文件
```
executer/src/main/java/dev/stella/executer/
├── gui/
│   ├── render/
│   │   ├── Render2DUtil.java          [阶段1] 2D绘图原语
│   │   ├── ColorUtil.java             [阶段1] 颜色工具
│   │   └── Animation.java             [阶段1] 动画系统
│   ├── setting/
│   │   ├── Setting.java               [阶段2] 设置基类
│   │   ├── BooleanSetting.java        [阶段2]
│   │   ├── SliderSetting.java         [阶段2]
│   │   ├── EnumSetting.java           [阶段2]
│   │   ├── ColorSetting.java          [阶段2]
│   │   └── BindSetting.java           [阶段2]
│   ├── component/
│   │   ├── Panel.java                 [阶段1] 分类面板
│   │   ├── ModuleButton.java          [阶段1] 模块按钮
│   │   └── SearchBar.java             [阶段1] 搜索栏
│   └── hud/
│       ├── HudModule.java             [阶段4] HUD基类
│       ├── ArrayListHud.java          [阶段4] 模块列表
│       ├── CoordsHud.java             [阶段4] 坐标
│       ├── FpsHud.java                [阶段4] 帧率
│       ├── WaterMarkHud.java          [阶段4] 标题
│       ├── TargetHud.java             [阶段4] 目标
│       ├── ArmorHud.java              [阶段4] 盔甲
│       └── DirectionHud.java          [阶段4] 朝向
├── config/
│   └── ConfigManager.java            [阶段6] 配置管理
├── command/
│   └── CommandManager.java           [阶段7] 命令系统
├── event/
│   ├── Event.java                    [阶段5] 事件基类
│   ├── EventBus.java                 [阶段5] 事件总线
│   └── ClientEvents.java             [阶段5] 事件定义
└── mixin/
    ├── MixinClientPlayerEntity.java  [阶段5]
    ├── MixinClientConnection.java    [阶段5]
    ├── MixinEntity.java              [阶段5]
    ├── MixinWorld.java               [阶段5]
    └── stella.mixins.json            [阶段5]

executer/src/main/resources/
└── stella.mixins.json                [阶段5]
```

### 修改文件
```
executer/src/main/java/dev/stella/executer/
├── StellaExecuter.java               [阶段1-7] 主入口扩展
└── gui/
    ├── Category.java                  [阶段1] 增加图标
    ├── Module.java                    [阶段2] 增加设置列表
    ├── ModuleManager.java             [阶段2] 模块注册扩展
    └── ClickGuiScreen.java            [阶段1] 完全重写

cilent/src/
├── main.cpp                           [阶段3] 模块tick循环
└── protocol.h                         [阶段3] 可能新增opcode
```

---

## 工作量估算

| 阶段 | 任务 | 天数 | 依赖 |
|------|------|------|------|
| 1 | Render2DUtil + ColorUtil + Animation | 1 | 无 |
| 1 | ClickGuiScreen 重写 + Panel + ModuleButton + SearchBar | 2 | 渲染基础 |
| 2 | Setting 系统 (6个类) | 1 | 无 |
| 2 | 模块设置注册 | 0.5 | Setting系统 |
| 3 | C++ 模块 tick 循环 | 1 | 无 |
| 4 | HUD 基类 + ArrayList + Coords + FPS + WaterMark | 1.5 | 渲染基础 |
| 4 | Target + Armor + Direction | 0.5 | HUD基类 |
| 5 | Mixin 环境配置 + 5个Mixin | 2 | Loom配置 |
| 6 | ConfigManager | 0.5 | Setting系统 |
| 7 | CommandManager + 6个命令 | 0.5 | Mixin |
| 8 | 雪花粒子 + 光谱配色 | 0.5 | 渲染基础 |
| **总计** | | **11天** | |

---

## 实施顺序

```
第1天: Render2DUtil + ColorUtil + Animation
第2天: ClickGuiScreen + Panel + ModuleButton + SearchBar
第3天: Setting系统 + 模块设置注册
第4天: C++ 模块 tick 循环
第5天: HUD基类 + ArrayList + Coords + FPS + WaterMark
第6天: Target + Armor + Direction
第7-8天: Mixin 环境 + Mixin实现
第9天: ConfigManager
第10天: CommandManager + 命令
第11天: 雪花粒子 + 光谱配色 + 测试
```
