# 开发执行步骤（Dev Steps）

> 每阶段以 `./gradlew build` + `./gradlew runClient` 验证，并在 `devlog/` 记录。
> 原则：**小步快跑、稳定安全**；未完成验证点不进入下一阶段。

## Phase 0 — 项目基建 ✅
- [x] 修复构建（EMI 本地 jar、删不可达 maven）；mods.toml 启用 Mixin + emi 依赖。
- [x] 建立 `docs/`（01-04）、`devlog/`、项目 `CLAUDE.md`。
- [x] 验证：`./gradlew build` SUCCESS。

## Phase 1+2 — 合成树（✅ 完成）
- [x] **拦截树按钮**：`EmiApiMixin`（viewRecipeTree → 收藏页树视图，不弹 BoM；添加不关页面）。
- [x] **隔离模式**：`IEmiInternal` + `InternalHelperImpl`（EMI 内部类访问集中）；accessor 走"实例 cast 到接口"。
- [x] **树构建**：worklist 成本聚合（need/batch/excess/副产物）；配方选择 = `BoM.getRecipe`（尊重默认/取消）+ 跳过分解类；目标用"最后一步配方"；组件感知 key；标签未解析不拆解。
- [x] **渲染**：目标左 + 竖分割线 + 材料分行横排 + 副产物区；纯图标+数量；标签徽记；流体 mB 文本；悬停高亮+tooltip。
- [x] **交互**：左键查配方/标签选择；右键删树；滚动条拖动；添加自动滚底；滚轮仅树区；树区悬停屏蔽（不点穿/不重叠）。
- [x] **持久化**：目标 NBT（含组件）+ 最后一步配方 id；延迟到进世界解析；兼容旧格式。
- [x] **配方页树按钮**：激活纹理（在树中）+ 点击切换增删。
- [x] **NeoForge 范围放宽** `[21.0.0,)`。
- [x] **用户 runClient 验收**：栅栏示例布局数字、铁块不循环、标签选择、药水/时长、持久化、滚动、流体 mB、点穿/重叠（✅ 已验收，随 1.0.0 发布）。

## Phase 3 — 自动合成（✅ 完成；v1.0.0 服务端方案，v2.0.0 已被 Phase 5 纯客户端取代）
- [x] 3.1 `network/AutoCraftPayload` + `AutoCraftResultPayload` + `ModPayloads`：C2S `emibettersynthesischain:auto_craft`（recipeId + preferredProducers 默认配方映射 + repeat）；S2C 成功提示。
- [x] 3.2 客户端：自动合成快捷键（V / Shift+V）+ 悬停检测（树优先/EMI 坐标/槽位兜底）→ 找工作台配方（BoM 优先）→ 发包 + 沿链收集默认配方映射；非工作台配方静默。
- [x] 3.3 服务端 `AutoCraftHandler` + `AutoCraftChain`：资格（CraftingRecipe）→ 2×2/3×3+**打开工作台界面**（containerMenu=CraftingMenu，先查）→ **完整链条递归确保材料**（循环≤12，缺多少合多少批，含 tag 成员，深度≤6，中间 3×3 同样需打开工作台界面，默认配方优先）→ **模拟玩家合成**（材料摆进 2×2/3×3 合成格，assemble 取结果，余料/容器返还背包，材料随格清空）→ 成功提示；打开工作台/背包时走**可见合成链逐步显示**（每步进格→显示→取结果→下一步，关界面中止；V 得最终结果即停，Shift+V 连续合成）。
- [x] 3.4 `Config`：`treeSidebarWidth`；`autoCraftWorkbenchRadius` **已移除**（3×3 改判打开工作台界面）。
- [x] 3.5 客户端成功提示：`AutoCraftResultPayload`（S2C）+ `MessageOverlay`（容器屏顶部）；失败静默。
- [x] 3.6 **验证**：2×2 成功；3×3 打开/未打开工作台界面；非工作台不合成；**完整链条**（红色玻璃板）一次 V 完成；**可见逐步显示**（打开工作台，观察材料进格→结果显示→清格→下一步，约 1-2 秒）；**V 得最终结果即停 / Shift+V 连续合成**（一直合到材料用完）；**失败提示**（未悬停物品 / 非工作台配方 / 3×3 未打开界面 / 材料不足 → 红字）；**标红**：有前置原料链可合成的（玻璃板/红色染料）不标红，非工作台步骤（铁锭）标红；**默认配方**（红色染料设虞美人默认 → 用虞美人而非甜菜根）；**余料返还**（带容器配方的余料回背包）；**EMI 设置页**（分组设置 + 重置按钮）（✅ 已验收，随 1.0.0 发布）。

## 发布
- **v1.0.0**（2026-08-09）：功能全部完成并通过用户验收。构建：`./gradlew build` → BUILD SUCCESSFUL；产物 `build/libs/emibettersynthesischain-1.0.0.jar`。

## Phase 5 — v2.0.0 自动合成改纯客户端（✅ 完成）
- [x] 5.1 **删除服务端/网络代码**：`server/AutoCraftHandler`、`server/AutoCraftChain`、`network/AutoCraftPayload`、`AutoCraftResultPayload`、`ModPayloads` 整体删除（无自定义包）。
- [x] 5.2 **CraftInventory**（`client/`）：屏幕感知库存——`handler.getInventory(screen)`（AE2 终端=网络+背包 / 工作台=背包），无 handler 回退玩家背包；供树标红与链条预检。
- [x] 5.3 **ClientCraftChain**（`client/`）：tick 驱动点击链——EMI handler 定位合成格/输入源/结果槽，`MultiPlayerGameMode.handleInventoryMouseClick` 发 vanilla 点击包（清格→放料→取结果）；V/Shift+V；关界面中止。
- [x] 5.4 **AutoCraftClient 改造**：找 handler → 无则"该界面不支持自动合成" → 启动链条；删发包/默认配方映射/CraftingScreen 门禁。
- [x] 5.5 **InternalHelperImpl**：`hasEnough`/`canObtain` 改读 `CraftInventory`（跟随当前界面）；防环由 `isReverse` 换成 **EMI 祖先配方栈**（worklist `Agg.path` + 递归 `ancestors`）。
- [x] 5.6 注册 `ClientCraftChain.tick()` 到客户端 tick。
- [x] 5.7 **用户 runClient 验收**：工作台 3×3 完整链条（红玻璃板）一次 V；背包 2×2（木棍）；箱子界面红字"不支持"；材料不足红字；Shift+V 连续；铁块↔铁锭不循环；EMI 设置页仍生效；（装 AE2 时）合成终端用网络材料合成、树在终端看网络不标红、**结果槽产物可正常取走**（`Ae2Support` CRAFT_ITEM action）。**（✅ 已验收 2026-08-15）**

## Phase 6 — v2.1.0 批量特性（grilling 确认后按序实施）
- [x] 6.1 **Q1 产物列左右可调**：`Config.treeGoalSide`（默认 right）；`TreeRenderer` 镜像布局；EMI 设置页"产物在右"开关 + lang。
- [x] 6.2 **Q2 背景透明可调**：`Config.treeBackgroundTransparent`（默认 true）；`TreeRenderer` 透明分支；EMI 设置页开关 + lang。
- [x] 6.3 **Q3 顶部总材料行**：`TreeData.leafTotal` + `InternalHelperImpl.buildTree` 聚合叶节点 + `TreeRenderer` 首行渲染/高度同步。
- [x] 6.4 **Q5 Ctrl+V 强制合成**：`AutoCraftClient` 放开 Ctrl；`ClientCraftChain.force` 尽力而为/缺料跳过/静默停。
- [x] 6.5 **Q4 Shift 开原版 BoMScreen + 左侧缩略条**：`EmiApiMixin` Shift 分支 + 新增 `BoMScreenMixin`（render 缩略条 + mouseClicked 联动 `BoM.setGoal`/`recalculateTree`）。
- [x] 6.6 **放料改 EMI clientFill + Destination.NONE**：弃用手写逐格放料（右键取一半导致光标堆叠），改用 `EmiRecipeFiller.clientFill(NONE)` 一次性放置全部材料（光标受控、形状感知）；`canFit` 改用 bounding box 判定；`onShowEnd` 取结果后 `clearCursorIfHeld` 清光标。联调验证：工作台 3×3 完整链条成功、连续合成正常。
- [x] 6.7 **代码清理**：删除手写放料死代码 + 诊断日志，ClientCraftChain 从 563 行精简到 ~280 行。`./gradlew build` SUCCESS。
- [ ] **Q6 已知问题（本次不修，仅记录）**：`findSource`/`CraftInventory.count` NBT 严格匹配，需 2 个同种且 NBT 不同的物品分两槽时"只放一个就停"。

## Phase 6.5 — v2.1.0 联调修复（2026-08-15）
- [x] 6.8 **UI**：数字大小可调（`treeNumberScale` + EMI 设置页）；数量缩写扩到 k/M/G/T；数量位置修复（右下角内侧）；总材料行 + 所有材料行超宽换行铺满（不遮挡右侧产物）；左上角拥有量不足红字。
- [x] 6.9 **数量感知**：`totalNeedOf` 同种多槽聚合；`findRecipe` 叶子化用总 need + 解叶子化；tag 子材料成员递归。修复"背包 1 木板不拆解/同种需多个只合一次"。
- [x] 6.10 **取产物（模组界面）**：`clickSlot`（menu.slots.indexOf/container+containerSlot 匹配菜单 index）；`takeOutput` 分阶段（QUICK_MOVE 堆叠 → PICKUP → handler.craft fallback）；`onShowEnd` 用背包数量验证取走成功（模组 `output.hasItem` 不刷新不卡链）；click 越界不终止链。
- [x] 6.11 **精妙背包合成插件**：产物可取走、连续合成继续（`./gradlew build` SUCCESS）。
- [x] 6.12 **AE2 联动模块**：`CraftingTermSlot.mayPickup()` 恒 false → 改发 `InventoryActionPacket(CRAFT_ITEM)`（AE2 action 机制）；`client/Ae2Support`（`ModList.isLoaded("ae2")` 门禁，未装短路）；取产物后逐格 QUICK_MOVE 清格（防网络补料残留 + 结果槽残留——`clearToPlayerInventory` 不触发 `slotsChanged`）；AE2 菜单验证等待 12 tick 防误重试。
- [x] 6.13 **精妙背包产物堆叠**：`placeCursorIntoInventory` 优先堆叠到同种未满槽（不再散落空槽）。
- [x] 6.14 **诊断日志清理**：删除 `ClientCraftChain` 12 处 + `AutoCraftClient` 3 处 EBS INFO 日志（保留 warn/debug）。
- [x] 6.15 **性能优化（大树卡顿）**：`hasEnough` 复用 `invSnapshot`（一次库存快照，不再每节点重建）；`producerCache`（产出配方候选缓存 + 预计算 perBatch/is2x2）；`TreeRenderer` 拥有量一次快照。`./gradlew build` SUCCESS。
- [x] 6.16 **数量数字被图标盖（深度测试）**：物品图标 z=32 + 深度测试剔除 z=0 数字 → `pose.translate(z=200)`（同 EMI `renderAmount`）。`./gradlew build` SUCCESS。
- [x] 6.17 **数字位置修正 + 合成速度**：右下角坐标公式 `(ICON-1)/sc - 尺寸`（原公式把尺寸误放分子 → 文字居中）；`showTicks` 移除隐藏 4 tick 下限、取产物验证等待 4→2 tick（最快 0.2s/步）。`./gradlew build` SUCCESS。
- [x] 6.18 **右键删树防误触（三层）**：面板 `bounds.contains` + `lastScreen` 界面切换检测 + `ScreenEvent` 置位 `screenTransition`——修复"关闭容器界面时 EMI 侧边栏 bounds 残留、下次打开界面误删树"。`./gradlew build` SUCCESS。
- [x] 6.19 **非工作台配方拦截**：链条仅接受工作台配方（`isWorkbenchRecipe`），非工作台默认配方（如熔炉）不再放料进合成格；提示"该配方无法在工作台内进行"。`./gradlew build` SUCCESS。
- [x] 6.15 **用户 runClient 验收（v2.1.0）**：产物列右/左镜像；背景透明；总材料行换行；Shift 开原版 BoMScreen + 缩略条联动；Ctrl+V 强制；V 只在树最终产物生效；数量缩写/拥有量/数字大小；原版工作台完整链条 + 连续合成；精妙背包/模组界面取产物；**新增回归**：AE2 终端取产物+清格、大树流畅度、合成速度、右键开关容器不误删树、非工作台默认配方提示。**（✅ 已验收 2026-08-15）**

## 验收与发布
- **v2.1.0**（2026-08-15）：用户 runClient 验收全部通过（5.7 + 6.15）。构建：`./gradlew build` → BUILD SUCCESSFUL；产物 `build/libs/emibettersynthesischain-2.1.0.jar`。

## Phase 4（后续迭代，未列入 1.0.0）
- 树节点 tooltip 明细；单次合成数量可配置；树面板拖动调宽。

## 通用流程（每次会话）
1. 读 `devlog/` 当日日志 + 本文件定位阶段。
2. 按 `docs/02-TECHNICAL_DESIGN.md` 实现。
3. `./gradlew build` 通过；UI 用 `runClient` 手测。
4. 更新 `devlog/<日期>.md`（完成/待办/决策）；勾选本文件。
