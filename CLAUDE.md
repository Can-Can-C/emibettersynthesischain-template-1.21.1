# 项目指南 — EMI Better Synthesis Chain

NeoForge 1.21.1 / EMI 1.1.24 的 EMI 附属 mod：重写配方树展示 + 自动合成。
在开始任何工作前，**先读本文件 + 当日开发日志 + 执行步骤文档**。

## 标准文件（必读路径）

| 文件 | 内容 |
|------|------|
| `docs/01-REQUIREMENTS.md` | 开发需求（与用户确认的功能清单、验收标准、变更记录） |
| `docs/02-TECHNICAL_DESIGN.md` | 技术设计（架构、EMI 内部类清单、Mixin/handler 方案、布局规范） |
| `docs/03-DEV_STEPS.md` | 分阶段执行步骤（勾选框 + 每步验证点） |
| `docs/04-STANDARDS.md` | 代码/文档/日志/构建规范 |
| `devlog/` | 每日开发日志（`devlog/YYYY-MM-DD.md`） |

## 工作说明

1. **会话开始**：读 `devlog/` 当日日志 + `docs/03-DEV_STEPS.md`，确认当前阶段与待办。
2. **开发遵循**：按 `docs/02-TECHNICAL_DESIGN.md` 实现；新功能先更新 `docs/01`（需求）与 `docs/02`（设计）再编码。
3. **会话结束**：更新 `devlog/<当日日期>.md`（完成 / 待办 / 问题与决策），并勾选 `docs/03` 对应项。
4. **构建/验证**：
   - 编译：`./gradlew build`（必须 BUILD SUCCESSFUL）。
   - 界面：`./gradlew runClient` 手动验证该阶段验证点。
5. **依赖（重要）**：EMI 是**本地 jar**（`libs/emi-1.1.24+1.21.1+neoforge.jar`），非 maven；`build.gradle` 用 `compileOnly` + `localRuntime files(...)`。AE2 也是**本地 jar**（`libs/appliedenergistics2-19.2.17.jar`，仅 `compileOnly`，运行时 mod 由 `run/mods` 加载）——仅用于合成终端取产物联动，`Ae2Support` 有 `ModList.isLoaded("ae2")` 门禁，**未装 AE2 不报错**。**不要**改回 maven 坐标（官方仓库本网络不可达）。
6. **Mixin 约束**：注入 EMI **内部类**（非 api 包优先），锁定 EMI 1.1.24；升级 EMI 版本必须回归测试所有注入点。
7. **安全（v2.0.0 起纯客户端）**：自动合成只发 vanilla 点击包，**防作弊由 vanilla 点击校验兜底**；不引入自定义 payload/服务端合成逻辑。新增可选 mod 联动（AE2）时保持门禁短路，未装该 mod 功能静默关闭。
8. **小步推进**：严格按 `docs/03` 分阶段，每阶段独立验证后再进入下一阶段；不一口气做太多。

## 兼容性原则（最高优先）
本 mod 是 **EMI 附属 + 可选 mod 联动**（AE2、Productive Bees 等），**兼容性是第一要求**。任

何第三方联动都必须遵守：

1. **不硬编码第三方内容**：禁止用"美化/猜测"的方式适配第三方数据（如按 id 前缀美化品种名、猜 NBT 结构、硬编码 id/名称规则）——第三方内容会因**数据包 / 版本 / 新增内容**变化，硬编码不通用、易破坏。**反例**：Productive Bees 蜜蜂名曾用 `fixBeeDisplayName` 手写规则美化（已删除）——改用对方**官方 API**（`BeeEmiStack.getTooltip()/getName()` 的翻译机制）。
2. **优先使用对方官方机制**：适配第三方 mod 时，优先调它的**公开 API / 官方表示**（原始 `EmiStack`、官方转换器、官方数据），与原版/官方表现保持一致；**不做推测性转换**（如虚拟 EmiStack 无法转 ItemStack 时，用对方提供的表示，不自造）。
3. **门禁短路**：所有可选联动必须 `ModList.isLoaded(<modid>)` 门禁，方法体惰性引用第三方类——**未装该 mod 不加载其类、功能静默关闭、不报错**（如 `Ae2Support`、`ProductiveBeesSupport`）。
4. **本地 jar 编译依赖**：第三方联动用 `compileOnly` + `libs/` 本地 jar（不引 maven）；运行时由 `run/mods` 加载；**不打进 mod 包**。
5. **升级回归**：升级 EMI 或任一联动 mod 版本，必须回归所有注入点与联动路径；第三方新版本可能改变内部行为（数据缺失、api 变动）。
6. **表现层与数据层一致**：能显示对方原生表示时**不要转成自己的近似表示**；确需转换时走官方转换器（如 AE2 的 `EmiStackHelper`），并在设计文档记录依据（javap 实证）。

## 技术要点速查
- 树按钮：`EmiScreenManager.tree` → `EmiApi.viewRecipeTree()`（`mixin/EmiApiMixin` 拦截点）。
- **EMI 内部访问隔离**：业务代码只依赖 `client/IEmiInternal`（接口）+ `TreeData`（纯业务模型）；EMI 内部类引用只在 `client/InternalHelperImpl.java` 与 `mixin/` 中；访问 EMI 私有成员用 `@Accessor` 接口 mixin（"目标实例 cast 到接口"）。升级 EMI 只改这些地方。
- **树数据**：`InternalHelperImpl.buildTrees`（`BoM.getRecipe` 选配方 + worklist 成本聚合，跳过分解类）；`TreeData` = goal/directInputs/rows/byproducts。`BoM.tree` 仅用于 `getAddTarget` 取目标。
- **收藏页**：`SidebarType.FAVORITES`；`EmiScreenManager.focusSidebarType(...)`；面板渲染 `SidebarPanel.render`。
- **滚轮/悬停**：`EmiScreenManagerMixin` 拦截 `mouseScrolled`（仅树区消费）与 `getHoveredStack`（树区返回 EMPTY，防点穿/重叠）。
- **自动合成（v2.0.0 纯客户端）**：`V`=一次（得最终结果即停）/ `Shift+V`=连续。客户端 `AutoCraftClient` 悬停 → 找当前界面 EMI handler（`EmiRecipeFiller.getFirstValidHandler`）→ 启动 `ClientCraftChain`（tick 驱动，用 `MultiPlayerGameMode.handleInventoryMouseClick` 发 vanilla 点击包在当前合成格一步步合成：放料 `clientFill(NONE)`→取结果）。**服务端无需装本 mod**。库存检测 `CraftInventory`（`handler.getInventory`，AE2 终端=网络+背包；树标红跟随当前界面）。无 handler 界面提示"不支持"。防环用 **EMI 祖先配方栈**。消息 `MessageOverlay`。
- **AE2 取产物（`client/Ae2Support`）**：`CraftingTermSlot.mayPickup()` 恒 false → vanilla 点击/handler.craft 取不走；改发 `InventoryActionPacket(CRAFT_ITEM)`（AE2 action 机制）+ 逐格 QUICK_MOVE 清格（**勿用** `clearToPlayerInventory`：`setItemDirect` 不触发 `slotsChanged` → 结果槽残留）。AE2 未装时 `ModList.isLoaded("ae2")` 门禁短路。
- **渲染 z 深度**：物品图标 `renderFakeItem` z=32 + 深度测试；自绘数字/拥有量必须 `pose.translate(z=200)`（同 EMI `renderAmount`），否则被图标"叠住"。
- **性能**：树构建 hasEnough 复用 `InternalHelperImpl.invSnapshot`（一次库存快照）；产出配方候选 `producerCache` 缓存；`TreeRenderer` 拥有量渲染用一次快照——改树构建/渲染勿退回"每节点重建快照"。
