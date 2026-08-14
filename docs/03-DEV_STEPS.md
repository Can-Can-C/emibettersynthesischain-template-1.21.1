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

## Phase 3 — 自动合成（✅ 完成）
- [x] 3.1 `network/AutoCraftPayload` + `AutoCraftResultPayload` + `ModPayloads`：C2S `emibettersynthesischain:auto_craft`（recipeId + preferredProducers 默认配方映射 + repeat）；S2C 成功提示。
- [x] 3.2 客户端：自动合成快捷键（V / Shift+V）+ 悬停检测（树优先/EMI 坐标/槽位兜底）→ 找工作台配方（BoM 优先）→ 发包 + 沿链收集默认配方映射；非工作台配方静默。
- [x] 3.3 服务端 `AutoCraftHandler` + `AutoCraftChain`：资格（CraftingRecipe）→ 2×2/3×3+**打开工作台界面**（containerMenu=CraftingMenu，先查）→ **完整链条递归确保材料**（循环≤12，缺多少合多少批，含 tag 成员，深度≤6，中间 3×3 同样需打开工作台界面，默认配方优先）→ **模拟玩家合成**（材料摆进 2×2/3×3 合成格，assemble 取结果，余料/容器返还背包，材料随格清空）→ 成功提示；打开工作台/背包时走**可见合成链逐步显示**（每步进格→显示→取结果→下一步，关界面中止；V 得最终结果即停，Shift+V 连续合成）。
- [x] 3.4 `Config`：`treeSidebarWidth`；`autoCraftWorkbenchRadius` **已移除**（3×3 改判打开工作台界面）。
- [x] 3.5 客户端成功提示：`AutoCraftResultPayload`（S2C）+ `MessageOverlay`（容器屏顶部）；失败静默。
- [x] 3.6 **验证**：2×2 成功；3×3 打开/未打开工作台界面；非工作台不合成；**完整链条**（红色玻璃板）一次 V 完成；**可见逐步显示**（打开工作台，观察材料进格→结果显示→清格→下一步，约 1-2 秒）；**V 得最终结果即停 / Shift+V 连续合成**（一直合到材料用完）；**失败提示**（未悬停物品 / 非工作台配方 / 3×3 未打开界面 / 材料不足 → 红字）；**标红**：有前置原料链可合成的（玻璃板/红色染料）不标红，非工作台步骤（铁锭）标红；**默认配方**（红色染料设虞美人默认 → 用虞美人而非甜菜根）；**余料返还**（带容器配方的余料回背包）；**EMI 设置页**（分组设置 + 重置按钮）（✅ 已验收，随 1.0.0 发布）。

## 发布
- **v1.0.0**（2026-08-09）：功能全部完成并通过用户验收。构建：`./gradlew build` → BUILD SUCCESSFUL；产物 `build/libs/emibettersynthesischain-1.0.0.jar`。

## Phase 4（后续迭代，未列入 1.0.0）
- 树节点 tooltip 明细；单次合成数量可配置；树面板拖动调宽。

## 通用流程（每次会话）
1. 读 `devlog/` 当日日志 + 本文件定位阶段。
2. 按 `docs/02-TECHNICAL_DESIGN.md` 实现。
3. `./gradlew build` 通过；UI 用 `runClient` 手测。
4. 更新 `devlog/<日期>.md`（完成/待办/决策）；勾选本文件。
