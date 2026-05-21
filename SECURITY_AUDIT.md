# OpenZen 后门与安全审计报告

审计对象：`acmuhan/openzen`  
本地路径：`/opt/data/workspace/openzen-workflow/openzen`  
审计时间：2026-05-21  
构建产物：`build/libs/examplemod-1.0.0.jar`  
构建 SHA256：`7874a0daa180a4cdd1a22d9ce21cbb12c0493478b5d34c5ee5b7baeb4db6cb6f`

## 1. 结论摘要

本轮进行了三路并发静态审计：

1. 网络、外传、C2、Webhook、QQ、上传下载。
2. 本地系统滥用：命令执行、文件读写、截屏、剪贴板、键盘记录、持久化。
3. ASM、Java Agent、Reflection、Unsafe、AntiDebug。

结论：

- 未发现明确的远程 C2、HTTP 外传、Webhook、QQ 上传、Socket 后门。
- 未发现截屏上传、剪贴板读取、传统 keylogger、自启动持久化、反弹 shell。
- 但项目存在一组高风险隐蔽能力链：
  - Java Agent。
  - ASM runtime patch。
  - `Instrumentation` 全局暴露。
  - `Unsafe` 和反射绕过封装。
  - Minecraft mod 列表自隐藏。
  - AntiDebug 反分析、进程枚举、外部命令执行。

一句话判断：

> 暂未发现“上传截图/文件到外部服务器”的实锤，但 `AntiDebug`、自隐藏、Agent 改类这一套很脏，建议清理后再放心使用。

## 2. 高风险发现

### 2.1 AntiDebug 反调试与命令执行，高危

文件：

```text
src/main/java/shit/zen/utils/misc/AntiDebug.java
```

证据：

- 枚举本机进程：

```text
AntiDebug.java:23
ProcessHandle.allProcesses()
```

- 检测调试/分析工具：

```text
AntiDebug.java:22
ZenlessZoneZero, HTTPDebugger, ida64, ida
```

- 命中后调用 `Unsafe.freeMemory` 释放硬编码地址，疑似故意崩溃或反分析：

```text
AntiDebug.java:25-28
UnsafeUtil.getUnsafe().freeMemory(1163911367127L)
```

- 执行外部命令：

```text
AntiDebug.java:31-34
Runtime.getRuntime().exec("cmd.exe /c \"" + list3.get(0) + "\"")
```

- 无条件再次释放硬编码地址：

```text
AntiDebug.java:38-41
```

关联文件：

```text
src/main/java/shit/zen/utils/misc/GamePathLocator.java
src/main/java/shit/zen/utils/misc/UnsafeUtil.java
```

`GamePathLocator` 会读取 Windows 用户目录中的米哈游日志并定位 `ZenlessZoneZero.exe`：

```text
%USERPROFILE%/AppData/LocalLow/miHoYo
绝区零/Player.log
ZenlessZoneZero.exe
```

风险判断：

- Minecraft 客户端中定位并启动其他游戏 exe 的行为非常异常。
- 反调试、进程枚举、非法内存释放、命令执行组合属于高危隐蔽行为。
- 即使当前未发现外部调用点，保留在代码中仍是高风险隐患。

建议：

- 删除或彻底禁用 `AntiDebug.java`。
- 删除或彻底禁用 `GamePathLocator.java`。
- 清理对 `UnsafeUtil.getUnsafe().freeMemory(...)` 的异常使用。

---

### 2.2 Minecraft mod 列表中主动隐藏自身，高危

文件：

```text
src/main/java/shit/zen/patch/MinecraftPatch.java
```

证据：

```text
MinecraftPatch.java:44
ModList.get().getMods().removeIf(modInfo -> modInfo.getModId().equals("hey"));
```

```text
MinecraftPatch.java:45-53
遍历 ModList.get().getModFiles()，移除包含 modId hey 的 modFile
```

风险判断：

- 这是明确的自隐藏行为。
- 会降低用户、调试工具、检测工具对该 mod 的可见性。
- 与 Java Agent、ASM patch 组合后，隐蔽性显著增强。

建议：

- 删除隐藏自身的逻辑。
- 如需要隐藏仅用于研究，应默认关闭并在 README 中明确说明。

---

### 2.3 Java Agent 与 ASM runtime patch 能力强，高风险基础设施

相关文件：

```text
build.gradle
src/main/java/asm/patchify/loader/PatchAgent.java
src/main/java/asm/patchify/loader/PatchClassFileTransformer.java
src/main/java/asm/patchify/loader/PatchTransformer.java
```

证据：

```text
build.gradle:87-93
-javaagent:${tasks.jar.archiveFile.get().asFile.absolutePath}
-Djdk.attach.allowAttachSelf=true
```

```text
build.gradle:195-199
Premain-Class: asm.patchify.loader.PatchAgent
Agent-Class: asm.patchify.loader.PatchAgent
Can-Retransform-Classes: true
Can-Redefine-Classes: true
```

```text
PatchAgent.java:62-63
inst.addTransformer(transformer, true)
```

```text
PatchAgent.java:87-90
inst.retransformClasses(target)
```

风险判断：

- 该设计允许 JVM 运行时重写已加载类。
- 能绕过常规源码路径植入逻辑。
- 目前注册 patch 是静态列表，但框架本身可被新增 patch 滥用。

建议：

- 严格审计所有 `@Transform`、`@WrapInvoke`、`@Overwrite` patch。
- 发布版尽量不要开放 `agentmain` 与 self attach。
- 仅保留必要的 `premain` 能力。

---

### 2.4 `Instrumentation` 被放入全局 `System.properties`

文件：

```text
src/main/java/asm/patchify/loader/PatchAgent.java
```

证据：

```text
PatchAgent.java:21
asm.patchify.instrumentation
```

```text
PatchAgent.java:40
System.getProperties().put(INSTRUMENTATION_KEY, inst)
```

```text
PatchAgent.java:47-49
从 System properties 取回 Instrumentation
```

风险判断：

- `Instrumentation` 是高权限对象。
- 放在全局 properties 中缺少访问控制。
- 同 JVM 内其他恶意模块可以取回该对象并注册自己的 transformer。

建议：

- 不要把 `Instrumentation` 放进全局 `System.properties()`。
- 使用受控私有持有方式，并限制访问路径。

---

### 2.5 网络包收发被全局 Hook

文件：

```text
src/main/java/shit/zen/patch/ConnectionPatch.java
```

证据：

```text
ConnectionPatch.java:24
onPacketReceive(Packet<?> packet)
```

```text
ConnectionPatch.java:39
onPacketSend(Packet<?> packet)
```

```text
ConnectionPatch.java:51-64
修改 channelRead0，入站包可被提前 RETURN
```

```text
ConnectionPatch.java:68-80
修改 sendPacket，出站包可被提前 RETURN
```

风险判断：

- 这是作弊客户端常见的包拦截机制。
- 未发现将包发往第三方服务器的证据。
- 但它可以观察、取消、改写 Minecraft 服务器通信。

建议：

- 对所有监听 `PacketEvent` 的模块做进一步逐项审计。
- 如果目标是干净研究版，应保留最小必要 hook。

## 3. 中风险发现

### 3.1 `ConfigCommand` 使用命令执行打开配置目录

文件：

```text
src/main/java/shit/zen/command/impl/ConfigCommand.java
```

证据：

```text
ConfigCommand.java:24
Runtime.getRuntime().exec("explorer " + ConfigManager.CONFIG_DIR.getAbsolutePath())
```

风险判断：

- 看起来只是打开配置目录，属于常见用途。
- 但字符串拼接执行命令仍不理想。

建议：

- 改用 Java Desktop API。
- 或至少避免 shell 字符串拼接。

---

### 3.2 `Disabler`、`FastUse` 等模块操控 Minecraft 协议包

相关文件：

```text
src/main/java/shit/zen/modules/impl/exploit/Disabler.java
src/main/java/shit/zen/modules/impl/movement/FastUse.java
src/main/java/shit/zen/utils/misc/PacketUtil.java
```

证据：

```text
PacketUtil.java:152-157
mc.player.connection.send(packet)
```

```text
Disabler.java:155-159
PacketUtil.sendQueued(new ServerboundPongPacket(0))
```

```text
Disabler.java:168-217
修改 ServerboundMovePlayerPacket yaw/pitch
```

```text
FastUse.java:228-234
入站包可被取消并排队
```

风险判断：

- 这不是外传后门。
- 属于作弊、反作弊规避、协议操控功能。

建议：

- 如项目目标是研究反混淆，可以保留但明确标注。
- 如目标是安全干净客户端，应删除或隔离这些模块。

---

### 3.3 通用 IO 工具有任意读写能力

相关文件：

```text
src/main/java/shit/zen/utils/misc/IOUtil.java
src/main/java/shit/zen/utils/misc/ResourceUtil.java
```

风险判断：

- 当前未发现与网络外传组合。
- 主要用于配置、资源、图片、声音读取。
- 单独看不构成后门证据。

建议：

- 限定读取目录。
- 避免任意路径读取接口被模块滥用。

## 4. 网络与外传检查结果

未发现：

- 明确 HTTP 上传。
- Webhook。
- Discord、Telegram、QQ 外传。
- Socket C2。
- 文件上传链路。
- 截图上传链路。

Java 中实际网络请求相关代码很少，URL 主要出现在注释或 `URLDecoder` 场景。`mapping.srg` 中存在大量 `Upload`、`Download`、`Server`、`Token` 命中，但经判断为 Minecraft/Realms 映射名噪声，不代表项目实际上传下载。

## 5. 本地系统行为检查结果

未发现：

- 截屏或屏幕捕获：`Robot`、`createScreenCapture`。
- 剪贴板访问：`Clipboard`、`Toolkit.getSystemClipboard`。
- 传统 keylogger。
- 自启动持久化：注册表 Run、Startup、LaunchAgents、cron、schtasks。
- PowerShell、bash 反弹 shell。

发现的主要本地风险集中于：

- `AntiDebug.java`。
- `GamePathLocator.java`。
- `Runtime.exec`。
- `Unsafe.freeMemory`。

## 6. WebUI 静态资源

文件：

```text
src/main/resources/index.html
```

包含外部 CDN：

```text
https://apps.bdimg.com/libs/jquery/2.1.4/jquery.min.js
https://cdn.tailwindcss.com
https://fonts.googleapis.com
```

包含本地 API 字符串：

```text
/api/categories
/api/modules
/api/setSetting
/api/setModule
/api/getModule
```

但 Java 侧 WebUI 当前禁用：

```text
src/main/java/shit/zen/modules/impl/world/WebUI.java
WebUI is unavailable in this build.
```

判断：

- 当前更像残留前端资源。
- 未找到可用 HTTP server 实现。

## 7. 建议清理顺序

优先级从高到低：

1. 删除或禁用 `AntiDebug.java`。
2. 删除或禁用 `GamePathLocator.java`。
3. 删除 `MinecraftPatch` 中隐藏 mod 的逻辑。
4. 收紧 `PatchAgent`，不要全局暴露 `Instrumentation`。
5. 禁用 `agentmain` 与 self attach，仅保留必要 `premain`。
6. 审计所有 `@Transform`、`@WrapInvoke`、`@Overwrite` patch。
7. 统一 `mod_id`、`mods.toml`、`gradle.properties`，避免 `hey` 与 `examplemod` 混乱。
8. 对所有 `PacketEvent` 监听模块逐项复查。

## 8. 构建信息

构建环境：

```text
Zulu JDK 17.0.19
Gradle 8.8
Minecraft 1.20.1
Forge 47.4.20
```

构建命令：

```bash
cd /opt/data/workspace/openzen-workflow
./gradlew-zulu build --no-daemon --info --stacktrace
```

构建结果：

```text
BUILD SUCCESSFUL in 4m 49s
7 actionable tasks: 7 executed
```

产物：

```text
build/libs/examplemod-1.0.0.jar
SHA256: 7874a0daa180a4cdd1a22d9ce21cbb12c0493478b5d34c5ee5b7baeb4db6cb6f
Size: 13086151 bytes
```

## 9. 审计局限

- 本报告基于源码和资源静态审计。
- 未进行动态沙箱运行。
- 未对第三方依赖源码做完整审计。
- 未对最终 jar 做反编译差异审计。
- `index.html` 为单行压缩文件，定位只能到第 1 行。
- `mapping.srg` 噪声较多，已按上下文排除映射名误报。
