# Stella Client 开发交接文档

> 最后更新: 2026-10-04

## 重要：2026-10-04 架构变更（以下第 1-10 章为旧架构，仅作历史参考）

**新架构：`stella/` = SunCat 改进版（主体） + Stella 的 Lua/IPC 特性（嫁接）**

### 变更概要

| 项 | 旧 | 新 |
|----|----|----|
| Java 主体 | `executer/`（自研双进程执行器） | `stella/`（SunCat 源码魔改，包 `dev.stella`，mod id `stella`，版本 3.0.0） |
| 混淆 | protection 包 / obfuscate.json / crazy-obfuscator / Lua XOR / RaspProtection | **全部删除**，Lua 明文加载，构建无加密步骤 |
| 品牌 | 执行器+ClickGUI | 保留 Stella 名称；`suncat*` → `stella*`（类/包/资源/accesswidener/mixins 全部重命名） |
| Lua/IPC 特性 | `executer/` 内 LuaScriptHost + IpcHost | 嫁接进 `stella/`：`dev.stella.brain.*` + `dev.stella.ipc.*`，由模块 `StellaBridge` 承载（默认开启） |
| git 体积 | build 产物、jar/exe 入库 | `.gitignore` 重写 + `git rm --cached`（跟踪文件 226→179，最大 57KB） |

### 关键路径（新）

- `stella/src/main/java/dev/stella/stella.java` — 主类（原 SunCat 主类，ModInitializer）
- `stella/src/main/java/dev/stella/mod/modules/impl/client/StellaBridge.java` — Lua + IPC 桥模块（onTick 推送 EV_WORLD_SNAPSHOT/EV_TICK、接收指令序列）
- `stella/src/main/java/dev/stella/brain/` — LuaScriptHost / LuaScriptLoader / InstructionEncoder / SnapshotParser
- `stella/src/main/java/dev/stella/ipc/` — Protocol / IpcHost / InstructionDecoder / WorldSnapshot（共享内存 `stella_ipc.bin`）
- `stella/src/main/resources/assets/stella/lua/` — 7 个明文 Lua 脚本
- `stella/src/main/resources/fabric.mod.json`、`stella.accesswidener`、`stella.mixins.json`

### 构建（已验证 BUILD SUCCESSFUL, 2026-10-04）

```
build.bat                      # 等价于 cd stella && gradlew.bat build 并部署
cd stella && gradlew.bat build # 产物 stella/build/libs/stella-3.0.0.jar
```

- 必须用 **Gradle 8.11 wrapper**（fabric-loom 1.9.2 要求 ≥8.11；系统 Gradle 8.10.2 不可用）
- `JAVA_HOME=C:\Program Files\Zulu\zulu-21`
- 部署目标：`D:\PCL\.minecraft\versions\StellaCilent\mods`
- `stella/lib/*.jar`（sodium/satin/malilib/baritone 等）不入库，构建前需存在

### 待办（P0 → P2）

1. **P0** 游戏内冒烟测试：StellaBridge 自动启用、Lua 脚本 tick、IPC 双向（与 `cilent.exe` 握手）
2. **P0** C++ 端 (`cilent/src/main.cpp`) 与新 `Protocol`/opcode 对齐验证
3. **P1** 决定旧 `executer/` 去留（Lua/IPC 已迁走，其 mixin/accessor 仍被引用需甄别后删除）
4. **P1** `git add` 新文件（`stella/`、`.gitignore` 等）并提交
5. **P2** 清理本文档旧章节与 `IMPLEMENTATION_PLAN.md` 中已失效的 executer 方案

> 以下第 1-10 章内容描述的是**已废弃的双进程 executer 架构**，仅供查阅历史设计（IPC 协议表、MC API 修改记录仍有效）。

## 一、项目概述

Stella Client 是一个 Minecraft 1.21.1 黑客客户端，采用**双进程架构**：

| 组件 | 语言 | 位置 | 角色 |
|------|------|------|------|
| 执行器 (executer) | Java | `executer/` | **驱动层** — 渲染原语 + 底层动作 + 世界查询 |
| 客户端 (cilent) | C++ | `cilent/` | **大脑** — 所有游戏逻辑、模块、AI、寻路等全部在 C++ 实现 |

**核心设计原则：Java 端暴露原始操作，C++ 控制一切游戏逻辑。**

参考项目：彗星客户端 (Meteor Client)、水影客户端 (LiquidBounce)、SunCat

## 二、架构图

```
┌─────────────────────────────────────────────────┐
│  Minecraft 1.21.1 (Fabric)                      │
│  ┌───────────────────────────────────────────┐  │
│  │  Java 执行器 (executer)                    │  │
│  │                                           │  │
│  │  ClickGUI (按键 G 打开)                    │  │
│  │    ↓ 用户切换模块                          │  │
│  │  EV_MODULE_TOGGLE → C++                    │  │
│  │                                           │  │
│  │  事件推送 ──────────────────→              │  │
│  │    EV_TICK / EV_RENDER_3D / EV_ATTACK ... │  │
│  │                                           │  │
│  │  ←────────────────── 指令执行              │  │
│  │    CMD_DRAW_LINE / CMD_ATTACK / ...       │  │
│  └──────────────────┬────────────────────────┘  │
│                     │ 共享内存 (stella_ipc.bin)   │
│                     │ 双向 SPSC 环形缓冲区        │
└─────────────────────┼───────────────────────────┘
                      │
┌─────────────────────┼───────────────────────────┐
│  cilent.exe         │                           │
│  ┌──────────────────┴────────────────────────┐  │
│  │  C++ 客户端                               │  │
│  │                                           │  │
│  │  接收 EV_MODULE_TOGGLE → 更新模块状态      │  │
│  │  接收事件 → 逻辑判断 → 发送指令            │  │
│  │  (ESP, KillAura, Fullbright...)           │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

## 三、文件结构

### executer/ (Java 驱动层 + ClickGUI)

```
executer/
├── build.gradle                Fabric Loom 1.7-SNAPSHOT, MC 1.21.1
├── gradle.properties           yarn=1.21.1+build.3, loader=0.16.5, fabric-api=0.102.0+1.21.1
├── settings.gradle
├── compile.bat                 手动编译脚本
├── compile_args.txt            javac 参数文件 (自动生成)
├── cp.txt                      classpath 列表 (自动生成)
└── src/main/
    ├── java/dev/stella/executer/
    │   ├── StellaExecuter.java          主类：IPC + 事件钩子 + 指令执行 + 渲染 + 按键绑定
    │   ├── gui/
    │   │   ├── Category.java            模块分类枚举 (COMBAT, MOVEMENT, RENDER, PLAYER, MISC)
    │   │   ├── Module.java              模块类 (name, category, moduleId, enabled)
    │   │   ├── ModuleManager.java       模块注册中心 (12个模块)
    │   │   └── ClickGuiScreen.java      ClickGUI 主屏幕 (按 G 打开, 可拖拽面板)
    │   └── ipc/
    │       ├── Protocol.java            协议常量 (与 protocol.h 同步)
    │       └── IpcHost.java             共享内存宿主 (MappedByteBuffer + SPSC Ring)
    └── resources/
        ├── assets/stella-executer/lang/en_us.json   按键翻译
        └── fabric.mod.json
```

### cilent/ (C++ 大脑)

```
cilent/
├── CMakeLists.txt              CMake 构建 (C++17)
├── src/
│   ├── main.cpp                IPC 客户端 + 控制台 UI + 模块状态 + 事件处理
│   ├── protocol.h              IPC 协议常量 (与 Java 端 Protocol.java 一致)
│   └── ipc/
│       ├── shared_memory.hpp   Win32 CreateFileMappingW + MapViewOfFile
│       └── ring_buffer.hpp     无锁 SPSC 环形缓冲区
└── build/
    └── stella_client.exe       ✅ MinGW g++ 8.2 编译通过
```

## 四、共享内存 IPC 协议

### 文件路径
`%TEMP%\stella_ipc.bin`（可通过 `-Dstella.ipcfile=...` 覆盖）

### 操作码总表

#### C++ → Java (指令)

| opcode | 名称 | payload | 说明 |
|--------|------|---------|------|
| 0 | OP_CONNECT | 空 | 启动握手 |
| **渲染** ||||
| 50 | CMD_DRAW_LINE | x1f32 y1f32 z1f32 x2f32 y2f32 z2f32 coloru32 | 3D 线段 |
| 51 | CMD_DRAW_BOX | xf32 yf32 zf32 wf32 hf32 df32 coloru32 | 3D 线框盒 |
| 52 | CMD_DRAW_TEXT | xf32 yf32 utf8 coloru32 | 屏幕文字 |
| 53 | CMD_DRAW_RECT | xf32 yf32 wf32 hf32 coloru32 | 2D 矩形 |
| 54 | CMD_DRAW_RECT_F | xf32 yf32 wf32 hf32 coloru32 | 2D 实心矩形 |
| 55 | CMD_CLEAR_RENDER | 空 | 清空绘制缓冲 |
| **动作** ||||
| 10 | CMD_SWING_HAND | handu8 | 挥手 |
| 11 | CMD_ATTACK | entityIdI32 | 攻击实体 |
| 12 | CMD_PLACE_BLOCK | xBi32 yBi32 zBi32 faceu8 | 放置方块 |
| 13 | CMD_BREAK_BLOCK | xBi32 yBi32 zBi32 | 破坏方块 |
| 14 | CMD_SET_SLOT | slotu8 | 切换快捷栏 |
| 15 | CMD_USE_ITEM | 空 | 右键使用 |
| 16 | CMD_SNEAK | stateu8 | 潜行 |
| 17 | CMD_SPRINT | stateu8 | 疾跑 |
| 18 | CMD_SET_VELOCITY | xf32 yf32 zf32 | 设置速度 |
| 19 | CMD_SEND_CHAT | utf8 | 发送聊天 |
| 20 | CMD_LOOK_AT | xf64 yf64 zf64 | 旋转看向某点 |
| 21 | CMD_MOVE_TO | xf64 yf64 zf64 | 传送 |
| 22 | CMD_SET_GAMMA | valueF32 | 亮度 |
| 23 | CMD_AUTO_DAMAGE | entityIdI32 | 循环攻击 |
| 24 | CMD_STOP_ALL | 空 | 停止所有动作 |
| 25 | CMD_SET_MODULE | moduleIdU8 enabledU8 | 设置模块状态 |
| **查询** ||||
| 30 | CMD_GET_PLAYER_POS | 空 | 查询玩家位置 |
| 31 | CMD_GET_ENTITIES | rangeF32 | 查询附近实体 |
| 32 | CMD_GET_BLOCK_AT | xBi32 yBi32 zBi32 | 查询方块 |
| 33 | CMD_GET_RAYTRACE | 空 | 射线检测 |
| 34 | CMD_GET_PLAYERS | rangeF32 | 查询附近玩家 |
| 35 | CMD_GET_INVENTORY | 空 | 查询背包 |

#### Java → C++ (事件)

| opcode | 名称 | payload | 说明 |
|--------|------|---------|------|
| 100 | EV_CONNECTED | 空 | 确认握手 |
| 101 | EV_PONG | echo | 心跳回复 |
| **游戏事件** ||||
| 110 | EV_TICK | worldTimeI64 | 每 tick |
| 111 | EV_RENDER_3D | camXf64 camYf64 camZf64 pitchf32 yawf32 partialf32 | 3D 渲染帧 |
| 112 | EV_RENDER_2D | screenWi32 screenHi32 partialf32 | 2D HUD |
| 113 | EV_ATTACK | entityIdI32 | 攻击事件 |
| 114 | EV_DAMAGE | amountF32 | 受伤事件 |
| 115 | EV_CHAT | utf8 | 聊天消息 |
| 116 | EV_BLOCK_UPDATE | xBi32 yBi32 zBi32 blockIdI32 | 方块更新 |
| 117 | EV_DEATH | 空 | 死亡事件 |
| 118 | EV_MODULE_TOGGLE | moduleIdU8 enabledU8 | ★ 新增：用户在 GUI 切换模块 |
| **查询响应** ||||
| 130 | EV_PLAYER_POS | xF64 yF64 zF64 pitchF32 yawF32 onGroundU8 | 玩家位置 |
| 131 | EV_ENTITY_LIST | countI32 then [idI32 typeI32 xF32 yF32 zF32 healthF32 armorI32]... | 实体列表 |
| 132 | EV_BLOCK_AT | blockIdI32 metaU8 | 方块信息 |
| 133 | EV_RAYTRACE | hitU8 xBi32 yBi32 zBi32 faceU8 entityIdI32 | 射线结果 |
| 134 | EV_PLAYER_LIST | countI32 then [entityIdI32 nameLenU8 nameUtf8 xF32 yF32 zF32]... | 玩家列表 |
| 135 | EV_INVENTORY | countI32 then [slotU8 itemIdI32 countU8]... | 背包数据 |

### 模块 ID 映射

| moduleId | 模块名 | C++ 变量 |
|----------|--------|----------|
| 0 | ESP | `g_espEnabled` |
| 1 | KillAura | `g_killauraEnabled` |
| 2 | Fullbright | `g_fullbrightEnabled` |
| 3 | Speed | (未实现) |
| 4 | Fly | (未实现) |
| 5 | Scaffold | (未实现) |
| 6 | NoFall | (未实现) |
| 7 | AutoTotem | (未实现) |
| 8 | ChestStealer | (未实现) |
| 9 | ItemESP | (未实现) |
| 10 | Tracers | (未实现) |
| 11 | Sprint | (未实现) |

## 五、当前状态 — ✅ Java 编译通过 (手动 javac)

### Java 编译 — ✅ 通过 (0 errors)

使用 `javac @compile_args.txt` 手动编译成功。Gradle wrapper.jar 损坏无法使用，改为手动收集 classpath。

### C++ 编译 — ✅ 通过

## 六、已完成的工作

### ✅ C++ main.cpp 完全重写
- 修复了所有 v2 协议常量（旧代码使用了不存在的 kOpPing, kOpListModules 等）
- 实现了 ESP 模块（查询实体 + 绘制线框盒）
- 实现了 KillAura 模块（攻击最近实体）
- 实现了 Fullbright 模块（设置 gamma=1000）
- 实现了控制台命令：modules, toggle, range, pos, entities, say, ping, help, quit
- 添加了 EV_MODULE_TOGGLE 处理

### ✅ Java 协议层修复
- 修复了所有 35 个 MC 1.21.1 API 编译错误（见下方详细列表）
- 实现了 CMD_GET_INVENTORY 查询
- 分离了 3D/2D 绘制队列
- 修复了绘制命令的 opcode 前缀标记

### ✅ 删除了废弃文件
- module/ 目录
- modules/ 目录
- gui/ 目录

### ✅ Java 端精简为纯驱动层
- StellaExecuter.java — IPC + 事件钩子 + 指令执行 + 3D/2D 渲染
- ipc/Protocol.java — 协议常量
- ipc/IpcHost.java — 共享内存宿主

## 七、修复 MC 1.21.1 API 错误的详细记录

以下是 `gradle build` 报告的 35 个错误及修复方案，供参考：

| 行号 | 原错误 | 修复 |
|------|--------|------|
| 72 | `mc.getTickDelta()` | `context.tickCounter().getTickDelta(false)` |
| 76 | `context.getMatrices()` | `context.matrixStack()` |
| 76 | `context.getVertexConsumers()` | `context.consumers()` |
| 86 | `RenderTickCounter` 不能转 `float` | `tickCounter.getTickDelta(false)` |
| 153 | `dir.getUnitVector().multiply(0.5)` | `Vec3d.of(dir.getVector()).multiply(0.5)` |
| 201 | `ChatMessageC2SPacket(text,null,null,null,null)` | `mc.getNetworkHandler().sendChatMessage(text)` |
| 290 | `Registries.BLOCK.getId()` 返回 Identifier | `Registries.BLOCK.getRawId()` |
| 305 | `bhr.getDirection()` | `bhr.getSide()` |
| 357 | `Registries.ITEM.getId()` 返回 Identifier | `Registries.ITEM.getRawId()` |
| 396+ | `.next()` on VertexConsumer | 移除（1.21.1 无此方法） |

**关键 API 变更 (MC 1.21.1 yarn 1.21.1+build.3)**：
- `MinecraftClient` 无 `getTickDelta()`，需通过 `getRenderTickCounter().getTickDelta(false)`
- `WorldRenderContext`: `matrixStack()`, `consumers()`, `tickCounter()`
- `HudRenderCallback` 第二参数: `RenderTickCounter` (非 float)
- `VertexConsumer` 无 `next()`，调用 `vertex()` 开始新顶点
- `BlockHitResult`: `getSide()` (非 `getDirection()`)
- `Registry`: `getRawId()` 返回 int, `getId()` 返回 Identifier
- `ChatMessageC2SPacket` 是签名包，用 `sendChatMessage()` 代替

## 八、构建环境

| 项目 | 值 |
|------|-----|
| 系统 | Windows (win32) |
| C++ 编译器 | MinGW g++ 8.2.0 @ `D:\MinGW\bin\g++.exe` |
| CMake | 4.4.0 |
| Java | JDK 21 |
| Gradle | 已通过 Loom 自带 wrapper |

编译 C++：
```bash
cd F:\StellaCilent\cilent
g++ -std=c++17 -c src\main.cpp -o build\main.o -I src
g++ -std=c++17 build\main.o -o build\stella_client.exe -lkernel32 -luser32 -static
```

编译 Java：
```bash
cd F:\StellaCilent\executer

# 步骤 1: 用 PowerShell 收集 classpath (所有 loom-cache + gradle 缓存的 jar)
powershell -Command "$loom = Get-ChildItem -Path '.gradle\loom-cache' -Filter '*.jar' -Recurse | Where-Object { $_.FullName -notmatch 'sources|javadoc' } | Select-Object -ExpandProperty FullName; $gradle = Get-ChildItem -Path \"$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\" -Filter '*.jar' -Recurse | Where-Object { $_.FullName -notmatch 'sources|javadoc|linux|darwin|natives' } | Select-Object -ExpandProperty FullName; $all = $loom + $gradle; $cp = $all -join ';'; Write-Output $cp | Out-File -Encoding utf8 cp.txt"

# 步骤 2: 生成 argfile (javac 参数文件)
powershell -Command "$cp = (Get-Content 'cp.txt' -Raw) -replace '\r?\n',''; $lines = @('-d', 'build\classes\java\main', '-sourcepath', 'src\main\java', '-cp', $cp, 'src\main\java\dev\stella\executer\ipc\Protocol.java', 'src\main\java\dev\stella\executer\ipc\IpcHost.java', 'src\main\java\dev\stella\executer\StellaExecuter.java'); $content = $lines -join ' '; [System.IO.File]::WriteAllText('compile_args.txt', $content, (New-Object System.Text.UTF8Encoding $false))"

# 步骤 3: 编译
javac @compile_args.txt

# 步骤 4: 打包 JAR
mkdir build\tmp_jar 2>nul
xcopy /E /Y /Q build\classes\java\main\* build\tmp_jar\
xcopy /E /Y /Q src\main\resources\* build\tmp_jar\
cd build\tmp_jar
jar cf ..\libs\stella-executer-1.0.0.jar .

# 步骤 5: 安装到 mods
copy /Y ..\libs\stella-executer-1.0.0.jar "%APPDATA%\.minecraft\mods\"
```

### 一键构建

```bash
cd F:\StellaCilent
build.bat
```

构建产物输出到 `build/` 文件夹：
- `build/stella_client.exe` — C++ 大脑
- `build/stella-executer-1.0.0.jar` — Java 驱动 + ClickGUI
- 同时自动复制 JAR 到 `%APPDATA%\.minecraft\mods\`

## 九、待完成 (优先级排序)

### P0 — 立即需要验证

1. **运行测试**：启动 Minecraft + cilent_client.exe，按 G 打开 ClickGUI，点击模块测试
2. **IPC 连通性**：ClickGUI 切换模块时发送 `EV_MODULE_TOGGLE` 到 C++，C++ 打印确认

### P1 — C++ 模块逻辑实现

3. ESP 模块 — 在 tick 中查询实体并绘制线框盒 (g_modules[0])
4. KillAura 模块 — 在 tick 中攻击最近实体 (g_modules[1])
5. Fullbright 模块 — 在 tick 中设置 gamma (g_modules[2])
6. Speed 模块 (g_modules[3]) — CMD_SET_VELOCITY
7. Fly 模块 (g_modules[4]) — CMD_SET_VELOCITY + 上升
8. Scaffold 模块 (g_modules[5]) — CMD_PLACE_BLOCK + 自动旋转
9. NoFall 模块 (g_modules[6]) — 每 tick 发送 CMD_SNEAK
10. AutoTotem 模块 (g_modules[7]) — CMD_SET_SLOT 切换图腾
11. Sprint 模块 (g_modules[11]) — CMD_SPRINT

### P2 — 渲染增强

12. Java 端 filled box 渲染
13. 2D HUD 绘制 (血量、坐标等)
14. 更多 Mixin hook（玩家加入/离开、物品拾取等）

### P3 — 配置持久化

15. C++ 侧 JSON/TOML 配置文件
16. Java 侧设置保存/加载

## 十、关键注意事项

1. **`cilent` 是用户指定的拼写**（不是 `client`），保持一致
2. **IPC 协议常量必须双端同步** — 修改 `Protocol.java` 时必须同步 `protocol.h`
3. **MinGW GCC 8.2 不支持 `std::thread`** — 使用 `CreateThread` (Win32 API)
4. **Java 端 MC API 版本** — yarn 1.21.1+build.3，注意 `VertexConsumer` 无 `next()` 方法
5. **Gradle wrapper.jar 损坏** — Java 编译使用手动 javac + argfile，见 `compile.bat`
6. **Java 端是纯驱动层** — 所有游戏逻辑由 C++ 控制，Java 只暴露原语
7. **参考代码位置** — `othercilent/SunCat-Client/` 有完整的 ClickGUI 实现可参考
