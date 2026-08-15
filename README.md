# Just Enough Calculation EX

> Minecraft 1.12.2 Forge 附加模组：增强 [Just Enough Calculation（JEC）](https://www.curseforge.com/minecraft/mc-mods/just-enough-calculation) 的 [GTCEu](https://github.com/GregTechCEu/GregTech) 配方导入体验，并提供合成树（BoM）视图。
>
> A Minecraft 1.12.2 Forge addon that improves [GTCEu](https://github.com/GregTechCEu/GregTech) recipe importing for [Just Enough Calculation (JEC)](https://www.curseforge.com/minecraft/mc-mods/just-enough-calculation) and adds a Bill-of-Materials style crafting tree.

**⚠️ AI 辅助开发声明 / AI-assisted development notice：本项目由 AI（LLM）辅助设计、编码与调试。**
**This project was designed, coded and debugged with AI (LLM) assistance.**

- **modid**: `jecaex`
- **当前版本 / Version**: `0.2.1`
- **平台 / Platform**: Minecraft `1.12.2` Forge

---

## 功能 / Features

### 1. GTCEu 配方导入修复 / GTCEu recipe import fixes

从 JEI/HEI 点击 "+" 导入 GTCEu 机器配方到 JEC 时：

- 修复 fuzzy-meta 折叠导致的机器显示为 "Unnamed" 空白方块；
- 非消耗品输入（电路、模具、透镜、工具等）正确进入催化剂槽，而不是原料槽；
- 按电压等级选择对应机器（例如 MV 配方显示 MV 机器，并跳过蒸汽机器）。

When importing GTCEu machine recipes from JEI/HEI:

- Fixes machines showing as "Unnamed" due to fuzzy-meta folding;
- Routes non-consumable inputs (circuits, molds, lenses, tools, ...) into catalyst slots;
- Picks the machine matching the recipe voltage tier (e.g. MV machine for MV recipes, skipping steam machines).

### 2. 合成树视图 / Crafting tree view

在 JEC 合成计算器中点击"合成树"按钮，打开全屏 BoM 风格配方树：

- 紧凑密集的自顶向下布局，父配方输入直接指向子配方；
- 颜色编码：催化剂=紫、可合成输入=绿、原材料=红、目标=蓝；
- 底部总原材料汇总（物品与流体）；
- 平移（拖拽）、缩放（滚轮）、视口裁剪；
- 折叠/显示催化剂按钮；
- Shift + 左键配方卡片：打开预填好的 JEC 配方编辑器。

Click the "合成树" button in the JEC crafting calculator to open a full-screen BoM-style recipe tree with pan/zoom, color coding, a bottom raw-material summary and a catalyst show/hide toggle.

---

## 依赖 / Requirements

- Minecraft `1.12.2` Forge
- [Just Enough Calculation](https://www.curseforge.com/minecraft/mc-mods/just-enough-calculation) `3.2.7`
- JEI `1.12.2`（运行时可用 HEI 替代 / HEI works as a runtime drop-in）
- [mixinbooter](https://www.curseforge.com/minecraft/mc-mods/mixin-booter)（late mixin loader）
- GTCEu（运行时依赖，通过反射访问 / runtime dependency, accessed via reflection）

---

## 安装 / Installation

1. 构建或下载本仓库 Releases 中的 `jecaex-<version>.jar`；
2. 与上述依赖一起放入 `.minecraft/mods`；
3. 启动游戏后，JEC 的 GT 配方导入修复自动生效；合成树按钮出现在 JEC 合成计算器中。

Build or download `jecaex-<version>.jar` from Releases, put it into `.minecraft/mods` together with the dependencies above.

---

## 构建 / Building

- JDK **25**（构建必需 / required）
- Gradle `9.7.0`（已随 Gradle Wrapper 提供 / bundled with the Gradle Wrapper）

Windows PowerShell:

```powershell
$env:JAVA_HOME = "C:/path/to/jdk-25"
.\gradlew.bat build --no-daemon
```

Linux/macOS:

```bash
./gradlew build --no-daemon
```

产物位于 `build/libs/jecaex-0.2.1.jar`。

> 注意：mixin 注解处理器会向 stderr 输出信息，PowerShell 可能显示非零 exit code；只要日志中出现 `BUILD SUCCESSFUL` 即构建成功。

The output jar is at `build/libs/jecaex-0.2.1.jar`. On Windows, the mixin annotation processor may write notes to stderr and PowerShell can show a non-zero exit code — **`BUILD SUCCESSFUL` in the log is the source of truth**.

---

## 使用 / Usage

- 在 JEI/HEI 中查看 GTCEu 配方并点击 "+" 导入 JEC；
- 在 JEC 合成计算器中点击 **合成树** 按钮打开配方树；
- 拖拽平移，滚轮缩放；
- Shift + 左键配方卡片将其预填到 JEC 配方编辑器；
- 右上角按钮折叠/展开催化剂。

---

## 技术说明 / Technical notes

- 使用 **`ILateMixinLoader`**（不是 `IEarlyMixinLoader`），mixin 目标为 JEC 的 GUI 类，必须在 late 阶段注册；
- 所有 GTCEu 访问均通过反射完成，不引入 GTCEu 编译期依赖，避免版本不匹配；
- 合成树界面参考了 EMI `BoMScreen` 的设计。

Uses `ILateMixinLoader` because the mixin targets JEC GUI classes. All GTCEu access is reflection-based to avoid a compile-time dependency; the crafting tree UI design is inspired by EMI's `BoMScreen`.

---

## 致谢 / Credits

- [Just Enough Calculation](https://www.curseforge.com/minecraft/mc-mods/just-enough-calculation)
- [HEI (Had Enough Items)](https://github.com/CleanroomMC/HadEnoughItems)
- [GTCEu](https://github.com/GregTechCEu/GregTech)
- [EMI](https://github.com/emilyploszaj/emi) — `BoMScreen` 的设计灵感 / design inspiration

---

## 许可证 / License

本项目使用 [MIT License](LICENSE)。

This project is licensed under the [MIT License](LICENSE).
