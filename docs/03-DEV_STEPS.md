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

## Phase 4 — v2.2.0 三项增强（需求 12-14，2026-08-15）
- [x] 4.1 **树节点 tooltip 明细（需求 12）**：`TreeData.TreeItem` 增加 producer（产出配方）；tooltip 追加"需要 X / 拥有 Y（不足红字）/ 由 X 合成 / 可合成性"。`./gradlew build` SUCCESS。
- [x] 4.2 **单次合成数量可配置（需求 13，会话内）**：`AutoCraftClient.amount`（默认 1，1-999，重启回 1）；树模式悬停最终产物**滚轮**调节 + **键盘 +/-** 调节（Shift ±10、Ctrl 翻倍/减半，提示"N"）；V=合 N 个即停（`ClientCraftChain.start` 加 targetAmount）+ **树按 N 倍展示** + **批量调度**（lookahead 攒料 + lastProducer 连续合成）；Shift+V/Ctrl+V 不变。`./gradlew build` SUCCESS。
- [x] ~~4.3 树面板拖动调宽（需求 14）~~：**已取消（2026-08-16 用户要求移除该功能）**，代码与设计已删除；宽度仍由 `treeSidebarWidth` 配置 + EMI 设置页调整。

## 兼容性原则整改（2026-08-16，用户要求"极强兼容性，不硬编码"）
- [x] C1 **去硬编码**：`ProductiveBeesSupport` 删硬编码实体 id / 伪造蜂笼 NBT（只写品种 type 作内部标记）；`TreeRenderer` 删 `"productivebees"` 前缀判断（用通用 `contains(":")`）。
- [x] C2 **不削真实量**：`Ae2Support.MAX_REASONABLE_STORED` 由 1e10 提到 `MAX_VALUE/4`（仅防溢出）。
- [x] C3 **深度放宽**：`canObtain` 深度 6→8（减少深度链误标红）。
- [x] C4 **宽度拖动移除**（TreeMode/EMI everything）；已知限制记录 docs/02 §11.4。
- [x] C5 **诊断日志清理**：mergedInventory 刷屏日志删除；getAddTarget/tree-btn/step 诊断保留为调试（会话收尾统一降级）。
- [ ] 4.4 **用户 runClient 验收**：tooltip 明细各字段；滚轮/± 调数量 + 树 N 倍 + V 合 N 个；批量调度（先攒中间再连合目标）；Shift/Ctrl 键行为不变。

## Phase 8 — v2.2.0 批量/Productive Bees/兼容性（2026-08-21）
- [x] 8.1 **每树独立合成数量**：`TreeManager` 每条目金额（落盘兼容旧档）；滚轮/± 只调悬停树；V 按树数量；树按各自 N 倍显示。
- [x] 8.2 **批量策略定稿（AE2）**：CRAFT_SHIFT 按需批量（需求 ≥ 一组才批量、余数单批精确不超量）；中间步需求视野=目标剩余全部批次（连续多组）；背包材料也批量（clientFill 堆叠放料优先 + handler.craft 回退）。
- [x] 8.3 **普通界面批量放料**：`getStacks(recipe, batch)` + `clientFill` 放 B 份堆叠进合成格 → shift 快速合成（服务端连续合成 B 批）。
- [x] 8.4 **Productive Bees 蜜蜂配方显示**（可选）：`ProductiveBeesSupport` 蜂笼标记持久化；树显示原始蜜蜂 EmiStack（原版 EMI 渲染路径）；蜜蜂↔蜂笼等价计数；tooltip 补名字行；门禁短路。
- [x] 8.5 **修复**：AE2 光标放回同步冷却（不卡鼠标）；左键点击不被界面切换吞（防盗误触只拦右键）；AE2 背包读取（playerSlots）；AE2 mergedInventory 刷屏日志清理。
- [x] 8.6 **兼容性原则整改**：CLAUDE.md 新增原则；去硬编码（蜂笼不伪造 entity、删 id 前缀）；防溢出钳制 MAX/4；canObtain 深度 8；宽度拖动移除。
- [x] 8.7 **用户 runClient 验收**：批量（AE2 多组零超量/背包批量/普通界面 shift 快速）、每树数量、Productive Bees 显示、光标不卡。（✅ 已验收 2026-08-22，随 v2.2.0 发布）

## Phase 9 — UI 更新（线条配色 / 树括号 / S / 层级序号，2026-08-21 会话 2）
- [x] 9.1 **线条配色青绿 + 预设可配置**：`client/TreeColorPreset`（纯枚举：teal/blue/purple/white/gold/red/green/gray，base 亮色 + deep 深色阶）；`Config.treeLineColor`（连接线/括号/序号/S）+ `treeDividerColor`（分割线）；EMI 设置页 `EnumWidget` 下拉（`mixin/TreeColorEnum` 实现 EMI `ConfigEnum`，业务代码零 EMI 依赖）+ lang。`./gradlew build` SUCCESS。
- [x] 9.2 **树左侧"["括号**：每棵树 1px 竖线（`bx=px+PAD-2`），顶/底各突出 2px + 3px 横帽，括住一棵配方（含副产物）。
- [x] 9.3 **总材料行左侧 S(sum) 标记** + **层级序号 1、2、…**（rows+directInputs 自上而下，普通数字不带圆圈，跳过空行）；材料行统一右移 `GUTTER=9` 留出左侧通道（layout/contentHeight/drawDecor 三处一致）。
- [ ] 9.4 **用户 runClient 验收**：默认青绿线条；设置页改配色落盘即生效（重启仍在）；每棵树括号完整括住；总材料行有 S；各层左侧序号自上而下 1、2、…；产物列左/右、背景透明/不透明、数字大小等既有设置不受影响。

## 验收与发布（v2.2.0，2026-08-22）
- **验收**：8.7（批量/每树数量/Productive Bees/光标）+ 9.4（青绿配色/设置页下拉落盘/括号/S/层级序号/既有设置回归）全部通过；本次 runClient 期间发现并修复"点击 EMI 设置页崩溃"（`TreeColorEnum` 移出 `mixin` 受管控包，见 devlog 2026-08-21 会话 2）。
- **构建**：`./gradlew build` → BUILD SUCCESSFUL；产物 `build/libs/emibettersynthesischain-2.2.0.jar`（`gradle.properties` `mod_version=2.2.0`）。

## Phase 10 — 修复"AE/背包中存储物读取不准"（v2.2.0 发布后回测，2026-08-22）
- [x] 10.1 **树缓存刷新检测扩展**：`maybeRefreshOnInventoryChange` 原只哈希玩家背包 36 格 + 菜单类名 → AE 网络内容变化/背包容器内挪动物品检测不到，树标红/可合成判定停留旧快照。修复：每 5 tick 追加①当前容器菜单全部槽位内容哈希；②`Ae2Support.networkSignature()`（ME 终端门禁，网络条目组合哈希）。`./gradlew build` SUCCESS。
- [x] 10.2 **合并库存扩展到所有 ME 终端**：`mergedInventory` 门禁由 `CraftingTermMenu` 放宽为 `MEStorageMenu`（javap 实证继承关系，覆盖普通存储/合成/无线终端）——普通 ME 终端看树也能识别网络存储。`./gradlew build` SUCCESS。
- [x] 10.3 **用户 runClient 复测验收通过（2026-08-22）**：AE 普通/合成终端与背包中动存储，树标红/拥有量即时跟上；自动合成前后数量正确。剩余已知误差（AE2 repo 同步滞后数 tick、同种不同 NBT 不合并计数 Q6）记录在案。

## 验收与发布（v2.2.1，2026-08-22 hotfix）
- **验收**：10.3 "AE/背包中存储物读取不准"修复复测通过（树缓存刷新检测扩展 + 合并库存扩展到所有 ME 终端）。
- **构建**：`./gradlew build` → BUILD SUCCESSFUL；产物 `build/libs/emibettersynthesischain-2.2.1.jar`（`gradle.properties` `mod_version=2.2.1`）。

## Phase 11 — 多版本开发（需求 15，2026-08-22 启动）
- 策略见 `docs/05-MULTI_VERSION.md`；分支：`1.20.1-forge` / `26.1.2-neoforge`（同仓多分支，main 主线保留）。
- [x] 11.1 **分支与空骨架**：✅ 双分支 `./gradlew build` 均 BUILD SUCCESSFUL——
  - `1.20.1-forge`：ForgeGradle 6 + **Gradle 8.7（JDK 17；FG6 不兼容 Gradle 9）** + Forge `1.20.1-47.4.5` + 官方 EMI 1.20.1 jar；mods.toml 硬编码（expand 模板解析中文/全角字符会失败）；`[40,)` loader。
  - `26.1.2-neoforge`：moddev **2.0.144** + NeoForge `26.1.2.97`（maven.neoforged.net 时通时断，需重试容忍）+ Gradle 9.2.1 + 非官方 EMI jar；`@Mod` 包路径未变；toolchain JDK 25。
- [x] 11.2 **核心移植（本分支包含全量模块：合成树/自动合成/配置 UI/联动占位）**：`./gradlew build` BUILD SUCCESSFUL。
  - **26.1.2 关键 API 差异（javap 实证，详见 devlog）**：`ResourceLocation`→`Identifier`；`GuiGraphics`→`GuiGraphicsExtractor`、`Screen.render`→`extractRenderState`；`drawString`→`text`、`renderTooltip`→`setTooltipForNextFrame`；pose 改 JOML `Matrix3x2fStack`（2D，无 z）；`ClickType`→`ContainerInput`+`handleContainerInput`；`KeyMapping(String,int,Category(Identifier))`；`Screen.hasControlDown/ShiftDown` 移除→GLFW；`RecipeBookMenu` grid getter 移除；`TagParser.parseCompoundFully`；`BuiltInRegistries.ITEM.get`→Optional；ItemStack 持久化走 CODEC（RegistryOps+NbtOps）；`Inventory.items` private。
- [x] 11.3 **自动合成（26.1.2，随核心移植一道编译）**：AutoCraftClient / ClientCraftChain / CraftInventory / MessageOverlay + 快捷键 —— 已在 11.2 全量源码中编译通过（同一批差异清单适配）。
- [x] 11.3b **26.1.2 冒烟启动通过（2026-08-22）**：启动到标题界面、全部 mixin 注入生效（`bo: tainted` 日志确认 EmiApi/ConfigScreen/BoMScreen/BoM）、keybind/client-ready 日志正常、无崩溃；`mouseClicked(MouseButtonEvent,Z)` / `mouseScrolled(DDD)Z`（修正误读为 4 参）输入 API 修复后无 Invalid descriptor；**合成提示修复**：`AbstractRecipeBookScreen.extractRenderState` 不调父类（InventoryScreen 路径）→ mixin 同时注入 AbstractContainerScreen + AbstractRecipeBookScreen。
- [x] 11.2d **26.1.2 功能验收通过（2026-08-22 用户 runClient）**：树显示（青绿线条/括号/S/层级序号/配色预设）正常；EMI 设置页"EMI Better Synthesis Chain"分组两个配色下拉正常；Shift+V 连续、Ctrl+V 强制、±/滚轮调数量正常；自动合成完整链条（含中间材料+批量）正常。**（✅ 已验收 2026-08-22）**
- [x] 11.4 **配置与 UI（26.1.2）**：Config / ConfigScreenMixin（含配色 EnumWidget）/ ConfigResetButton / lang —— 随核心移植编译并通过验收（设置页实测）。
- [ ] 11.5 **联动移植**：Ae2Support（分支 AE2 jar 门禁 + 发包路径）、Productive Bees、批量/每树数量。
- [ ] 11.6 **验收发布**：两分支 runClient 全量验收（对照主线 2.2.1），文档同步，分支独立版本发布并上传。

## Phase 7 — v2.1.1 AE2 终端网络拉料（需求 11）
- [x] 7.1 **实现**：`Ae2Support.mergedInventory`（自建"槽位+网络"合并库存，不依赖 AE2 的 exposeNetworkInventoryToEmi 配置，默认 false 时 handler.getInventory 不含网络）；`CraftInventory.currentScreenInventory/current` AE2 终端优先用合并库存；`ClientCraftChain.fillViaEmi` AE2 分支走 **AE2 原生 handler.craft（transferRecipe）**（服务端从背包+网络取料，替代 clientFill——其 getStacks 依赖 AE2 配置的 getInventory）；树标红 `producersOf` 与链条 `findProducerToCraft` 统一**只用默认配方**（BoM.getRecipe，断了就停）；`hasCraftingMenuOpen` 认可 AE2 合成格（3×3）。`./gradlew build` → BUILD SUCCESSFUL。
- [x] 7.2 **用户 runClient 验收通过**：AE2 终端、背包无料仅网络有料 → V 自动合成成功（材料直接网络进合成格→合成→取产物）；树按默认配方识别（默认链断即红）；中间层按默认配方链合成；Shift+V 连续；AE2 未装环境不受影响。**（✅ 已验收 2026-08-15）**

## 通用流程（每次会话）
1. 读 `devlog/` 当日日志 + 本文件定位阶段。
2. 按 `docs/02-TECHNICAL_DESIGN.md` 实现。
3. `./gradlew build` 通过；UI 用 `runClient` 手测。
4. 更新 `devlog/<日期>.md`（完成/待办/决策）；勾选本文件。
