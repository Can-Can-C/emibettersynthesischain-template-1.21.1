# 多版本开发策略（Multi-Version）

> 2026-08-22 建立。主线 `main` = MC 1.21.1 NeoForge（当前 v2.2.1）；目标分支 `1.20.1-forge`（MC 1.20.1 Forge）与 `26.1.2-neoforge`（MC 26.1.2 NeoForge + 非官方 EMI）。功能与主线**完全对齐**。

## 1. 版本矩阵

| 项 | main（现状） | 1.20.1-forge | 26.1.2-neoforge |
|----|--------------|--------------|-----------------|
| Minecraft | 1.21.1 | 1.20.1 | 26.1.2 |
| Loader | NeoForge 21.1.248 | **Forge**（javafml `[40,)`） | NeoForge（依赖形如 `26.1.x`，EMI jar 要求 `≥20.4.72-beta`） |
| 构建工具 | NeoGradle（`net.neoforged.moddev` 2.0.143）+ Gradle 9.2.1 | **ForgeGradle 6**（要求 **Gradle 8.x**，wrapper 须独立指向 8.x） | NeoGradle（moddev 插件需支持 26.1.2） |
| 映射 | parchment 1.21.1-2024.11.17 | official（或 parchment 1.20.1 若有） | official / parchment 26.1.2 若无则 official |
| EMI jar（libs/） | `emi-1.1.24+1.21.1+neoforge.jar` | `emi-1.1.24+1.20.1+forge.1.jar` | `emi-1.1.24-63e8aef+26.1.2+neoforge.jar` |
| EMI 内部类 | 1.1.24 基线 | **与基线同名同构**（已核验 jar 内 dev/emi/emi/{screen,bom,config,api,registry} 关键类齐全） | **与基线同名同构**（同上） |
| 配置类 | `ModConfigSpec`（neoforge.common） | `ForgeConfigSpec`（net.minecraftforge.common） | `ModConfigSpec`（若 26.1.2 包路径未变） |
| ModList | `net.neoforged.fml.ModList` | `net.minecraftforge.fml.ModList` | `net.neoforged.fml.ModList`（验证） |
| 发包（AE2 取产物） | `net.neoforged.neoforge.network.PacketDistributor` | `net.minecraftforge.network.PacketDistributor`（`SERVER.noArg().send(...)`） | neoforge（验证 API） |
| 物品组件 | DataComponents（`isSameItemSameComponents`/`hashItemAndComponents`） | **无组件体系**：NBT/Tag（`isSameItemSameTags`、`ItemStack.hashCrc` 或自实现） | DataComponents（26.1.2 语义可能再变，编译实证） |
| mods.toml | `neoforge.mods.toml` | `META-INF/mods.toml`（javafml 40） | `neoforge.mods.toml` |

## 2. 事实核查（2026-08-22，jar 实证）

- 两个目标 EMI jar 均为 **1.1.24 同源**：`dev/emi/emi/screen/ConfigScreen`（含 `$Mutator`）、`EmiScreenManager`（含 `$SidebarPanel`）、`RecipeScreen`、`bom/BoM`、`registry/EmiRecipeFiller`、`screen/widget/config/EnumWidget`、`api/stack/EmiStack` 等**全部存在** → mixins（EmiApiMixin/EmiScreenManagerMixin/ConfigScreenMixin/SidebarPanelMixin/BoM 系）与 `InternalHelperImpl` 的注入点大概率原样可用，仅需按 MC API 差异调整。
- 1.20.1 jar：`modLoader=javafml, loaderVersion=[40,)`；可选依赖 JEI（无关）。
- 26.1.2 jar：与 1.21.1 同构（`neoforge.mods.toml` + mixins 配置），要求 NeoForge `≥20.4.72-beta`。

## 3. 分支与仓库策略

- 同仓库**多分支**：`main`（1.21.1 主线，v2.2.x 继续演进）不动；从 main 拉 `1.20.1-forge`、`26.1.2-neoforge`。
- `libs/` 两个新 jar 已在 main 提交并随分支继承；各分支 `build.gradle` 只引用本分支对应 jar（未用 jar 保留在 libs/ 不影响）。
- 各分支 `./gradlew build` 独立验证；`runClient` 手动验收；发布时各分支独立打版本（主线 2.2.x 之后，分支可 1.20.1-1.0.0 / 26.1.2-1.0.0 起步）。

## 4. 已知差异 / 风险

### 4.1 1.20.1 Forge
- **组件体系缺失**：`ItemStack` 无 DataComponents。受影响的点：`InternalHelperImpl` 的刷新哈希（`hashItemAndComponents` → 换 `ItemStack.hashCrc()` 或 `hashCode`+getTag 组合）、`CraftInventory.count` 匹配（`isSameItemSameComponents` → `isSameItem` + `isSameItemSameTags`）、树持久化（NBT 存 tag 而非组件）、去重键 `hashItemAndComponents` 相关（`key()`）。
- **Loader API**：`ModList`/`PacketDistributor`/`ForgeConfigSpec`/注册事件（`FMLClientSetupEvent` 等）换 Forge 路径；EMI 设置页注入逻辑与 MC/EMI 无关部分原样。
- **构建**：ForgeGradle 6 需要 Gradle 8.x + JDK 17；当前 wrapper 是 Gradle 9.2.1 → **1.20.1-forge 分支必须换 wrapper**（改 `gradle-wrapper.properties` 指向 8.x，如 8.7）。
- **网络**：`maven.minecraftforge.net`（ForgeGradle/userdev/Forge maven）可达性待实测；不可达则需镜像/本地 userdev 方案（同 EMI 本地 jar 惯例）。
- AE2/Productive Bees for 1.20.1 需用户提供 jar（compileOnly + run/mods）。

### 4.2 26.1.2 NeoForge（非官方 EMI）
- **MC 跨度大（1.21.1 → 26.1.2）**：渲染/网络/注册等大量 API 变动；本 mod 用的是窄面（客户端 GUI、ContainerMenu/Slot、ItemStack、配方查询、发包），逐项编译实证。
- 非官方 EMI 重建可能改动 EMI 内部**方法签名/字段**（为适配新 MC）——mixins 注入点逐个用 javap 核对（`viewRecipeTree`/`mouseScrolled`/`getHoveredStack`/`SidebarPanel.render`/`ConfigScreen.init/addWidget`/`BoM` 字段）。
- 26.1.2 的 NeoGradle/moddev 插件版本、parchment 映射有无，构建期实测；无 parchment 则用 official（mojmap）。
- AE2/Productive Bees for 26.1.2 需用户提供 jar。

## 5. 步骤计划（对应 docs/03 Phase 11）

1. **11.x-1 分支与空骨架**：两分支各自把 build 工具链跑绿（`./gradlew build` 出空 mod jar，`mods.toml` 正确，EMI jar 编进 classpath）——**最大风险点先消灭**（ForgeGradle/Gradle8、26.1.2 moddev）。
2. **11.x-2 核心移植**：合成树数据/渲染/交互（EmiApiMixin、EmiScreenManagerMixin、SidebarPanelMixin、BoM 系、InternalHelperImpl、TreeRenderer/TreeData/TreeMode/TreeManager）→ 编译 + runClient 验收树展示。
3. **11.x-3 自动合成**：AutoCraftClient/ClientCraftChain/CraftInventory/MessageOverlay + 快捷键。
4. **11.x-4 配置与 UI**：Config/ConfigScreenMixin（含配色 EnumWidget）/ConfigResetButton/lang。
5. **11.x-5 联动**：Ae2Support、Productive Bees（分支 jar 门禁）、批量/每树数量。
6. **11.x-6 验收发布**：runClient 全量验收（对照主线 2.2.1），文档同步，分支发布上传。

> 每步 `./gradlew build` 必须 BUILD SUCCESSFUL；遇到 MC/EMI API 差异记入 devlog 与本文档（形成跨版本差异清单，供后续版本复用）。