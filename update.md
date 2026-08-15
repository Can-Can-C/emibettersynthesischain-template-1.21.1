# EMI Better Synthesis Chain — 自动合成改纯客户端（v2.0.0）实施计划

> 状态：已与用户逐条确认（grilling），计划已批准。此文件为项目内计划副本，供实现/追溯。

## Context（背景）

现状：自动合成 = 自定义 C2S/S2C payload + 服务端权威执行（`AutoCraftHandler`/`AutoCraftChain` 在服务端直接摆格/合中间料/产出），**必须服务端装本 mod**，且只认原版工作台/背包合成格，看不到 AE2 网络这类外部库存。

用户要做大更新（目标）：
1. **纯客户端自动合成**（同 EMI"+"：客户端模拟 vanilla 点击包，服务端装不装 mod 都能用）。
2. **支持 AE2 类物品检测与自动合成**（复用 EMI 的 handler 抽象，零 AE2 代码）。
3. **支持多模组合成界面自动合成**（只要该界面注册了 EMI `StandardRecipeHandler`）。
4. **改用 EMI 的防环逻辑**（祖先配方栈去重，替换启发式 `isReverse`）。

## 已锁定决策（grilling 逐条确认）

- **Q1** 纯客户端单一路径：只发 vanilla 点击包（`MultiPlayerGameMode.handleInventoryMouseClick` → `ServerboundContainerClickPacket`），服务端走原版容器校验。防作弊由 vanilla 点击校验兜住。
- **Q2** 保留**完整链条递归**：中间产物在当前合成格一步步合（可见），最后合目标；Shift+V 连续。
- **Q3/4** AE2 = **方案 (c)**：零 AE2 代码，靠 `EmiRecipeFiller.getFirstValidHandler(recipe, screen)` 自动用当前屏幕的 handler；检测跟随当前屏幕。
- **Q4** 树标红/判定改为**跟随当前屏幕库存**：有 handler → `handler.getInventory(screen)`（AE2 终端=网络+背包；工作台=背包）；无 handler → 回退 `EmiPlayerInventory.of(player)`。
- **Q8** 无 handler 界面（箱子/熔炉/无兼容模组机器）按 V → 红字"该界面不支持自动合成"，停止。
- **Q6** 删除全部服务端代码 + payload + 注册。
- **Q7** 防环改用 **EMI 祖先配方栈**（递归路径上 `previous.contains(recipe)` 即剪枝），替换 `isReverse`/`isDecomposition`。

## 架构（纯客户端）

```
按 V / Shift+V
  → AutoCraftClient.attemptAutoCraft(repeat)     客户端入口（不变：悬停检测 → 找配方）
  → 当前屏幕 handler = EmiRecipeFiller.getFirstValidHandler(recipe, screen)
      ├─ 无 handler → MessageOverlay("该界面不支持自动合成")，停止
      └─ 有 handler → 启动 ClientCraftChain（tick 驱动点击链）
          每步：解析下一步配方（祖先栈防环）→ CraftInventory 查料
            → 点击序列（清格→放料→等待→取结果）→ 下一步
  → V=得最终结果即停 / Shift+V=连续合到不能合
```

- **点击序列**（复用 EMI 逻辑）：对 `handler.getCraftingSlots(menu)` 各槽 `ClickType.QUICK_MOVE` 清格 → 对 `handler.getInputSources(menu)` 匹配材料槽 `PICKUP` 拾起 → 对合成格槽 `PICKUP` 放下 → 等 `Config.AUTO_CRAFT_SHOW_TICKS` → 对结果槽 `QUICK_MOVE` 取结果 → 等 `Config.AUTO_CRAFT_GAP_TICKS`。
- **材料检测**：`CraftInventory`（见下）聚合 `handler.getInventory(screen)`（含 AE2 网络/背包），供树标红、canCraft、链条预检统一使用。
- **成功/失败判定（客户端）**：开链前用 `CraftInventory` 预检 → 不足红字"材料不足"；目标步点结果槽后对比目标物品数量是否增加 → 增=成功"合成成功"、不增=中止"合成失败"。
- **默认配方优先**：不再有 payload 映射；链条解析中间配方直接用 `BoM.getRecipe(ingredient)`（客户端，尊重玩家默认），与树一致。
- **中止**：`ClientCraftChain` 每 tick 校验 `mc.screen`/菜单仍同屏，关界面即停；玩家动格子由 vanilla 点击自然处理（点不到即不生效）。

## 文件改动

**删除**（server/ 与 network/ 整个空掉）：
- `server/AutoCraftHandler.java`、`server/AutoCraftChain.java`
- `network/AutoCraftPayload.java`、`network/AutoCraftResultPayload.java`、`network/ModPayloads.java`

**新增**：
- `client/CraftInventory.java`：屏幕感知库存提供器——`availableFor(EmiRecipe)`（当前屏幕 handler → `handler.getInventory(screen)`；无 handler → `EmiPlayerInventory.of(player)`）；`count(EmiStack)`/`hasEnough(...)`/`canCraft(recipe)` 便捷封装；供 `InternalHelperImpl` 与链条共用。
- `client/ClientCraftChain.java`：tick 驱动点击链（替换服务端 AutoCraftChain）。字段：目标配方、repeat、当前 handler/menu、祖先配方栈、阶段/倒计时。每步：`pickNext()`（祖先栈 finder）→ `CraftInventory` 预检 → 点击序列 → 推进。

**修改**：
- `client/AutoCraftClient.java`：`attemptAutoCraft(repeat)` 改调 handler 分支 + 启动 `ClientCraftChain`；删除 `PacketDistributor.sendToServer`、`collectPreferredProducers`、`is2x2Craft`/`CraftingScreen` 门禁（handler 判定替代）；保留 V/Shift+V 键处理、悬停检测、`showFail`。
- `client/InternalHelperImpl.java`：`hasEnough`/`canObtain` 改读 `CraftInventory`（当前屏幕库存）；`findRecipe`/`buildTree` 防环由 `isReverse` 换成**祖先配方栈**（worklist 或递归携带 visited 配方集，路径重复即剪枝）；删除 `isReverse`（及不再使用的启发式）。
- `EMIBettersynthesischainClient.java`：构造函数注册 `ClientCraftChain.tick()` 到 `NeoForge.EVENT_BUS`（`ClientTickEvent`）——同 onMouseButton/onKeyInput 方式。
- `Config.java`：保留树间距 + `autoCraftShowTicks`/`autoCraftGapTicks`/`autoCraftFailureMessages`（链条读取从服务端迁到客户端链，值不变）。
- `mixin/ConfigScreenMixin.java`/`client/ConfigResetButton.java`：不变（设置项仍生效）。
- `client/MessageOverlay.java`：不变（纯客户端渲染 + show）。

## 复用（勿重写）
- `EmiRecipeFiller.getFirstValidHandler(recipe, screen)`、`handler.getInventory/getCraftingSlots/getInputSources`、`EmiPlayerInventory`、`MultiPlayerGameMode.handleInventoryMouseClick`、`ClickType`（均为 EMI/vanilla 已有，javap 实证）。
- `BoM.getRecipe`（默认配方）、`TreeRenderer.hitTest`/`getTreeHoveredStack`（悬停取物，已实现）。

## 验证（runClient）
1. 工作台 3×3：背包玻璃+虞美人，悬停红玻璃板按 V → 合成格依次可见"玻璃板×2→红染料→红玻璃板"，得最终结果即停。
2. 背包 2×2：木棍按 V → 在背包 2×2 格直接合成。
3. 箱子界面按 V → 红字"该界面不支持自动合成"。
4. 工作台材料不足按 V → 红字"材料不足"。
5. Shift+V → 连续合到材料用完。
6. 铁块↔铁锭：不循环（祖先栈）。
7. EMI 设置页：showTicks/gapTicks/失败提示开关仍生效。
8. （装 AE2 时）AE2 合成终端：网络有玻璃→树不标红、按 V 用网络材料合成；普通工作台→网络材料仍标红。

---

# 合成树增强 + 强制合成 + 原版树联动（批量特性，拟 v2.1.0）

> 状态：已与用户逐条 grilling 确认。本计划为项目内副本，供实现/追溯。
> 原则：新功能先更新 `docs/01`（需求）与 `docs/02`（设计）再编码；每步 `./gradlew build` 验证；小步推进。

## Context（背景）

在 v2.0.0（纯客户端自动合成）基础上，对合成树**展示/布局/交互**做一批增强，并新增**强制合成**快捷键与原版 EM 合成树界面联动。

## 已锁定决策（grilling 逐条确认）

### Q1 — 产物列左右可调
- 新增设置：**产物列在左还是在右**（布尔/枚举），默认**右**。
- 产物在右时，材料区从左侧镜像排布（`rowX` 改为贴左边起排，分割线在产物列左侧）。
- 用户已明确：**"物品信息会遮挡物品"这条不做**（忽略）。

### Q2 — 背景透明可调
- 默认**完全透明**：`TreeRenderer.render` 去掉自绘深背景 `g.fill(0xCC0B0B0B)`，透出下层（收藏夹/面板原本背景），与收藏夹一致。
- 新增设置开关：**透明 / 不透明**（不透明 = 恢复现有深色背景）。

### Q3 — 顶部总材料行
- 树**顶部新增一行"底层总材料汇总"**：把当前各叶节点（递归展开到底、但库存已足的不再展开）所需量聚合显示。
- 下面**保留完整分层**（中间产物如金胡萝卜仍显示）。不是折叠成两行，而是"顶部概览 + 完整分层"。
- 数量取各材料聚合后的 `need`（总需量，多次引用会累加）。

### Q4 — Shift+树按钮 → 原版 BoMScreen + 左侧产物缩略条
- **共存**：普通点树按钮 = 维持现有"收藏页内容合成树"；**Shift+点 = 打开原版 EM 合成树界面**。
- 原版树界面 = `dev.emi.emi.screen.BoMScreen`（`EmiApi.viewRecipeTree` 原分支，现被 `EmiApiMixin` 拦截）。Shift 分支放行/手动 `new BoMScreen(containerScreen)`。
- 在 BoMScreen **左侧叠我们自绘的产物缩略条**（来自 `TreeManager` 的多目标，只画各树最终产物图标）。
- **点击某产物** → `BoM.setGoal(<该产物最终配方>)` + 调 `BoMScreen.recalculateTree()`（已验证存在）→ 原版界面联动展示该产物合成树；缩略条高亮当前选中。
- 展示交给原版（EMI BoM 样式），我们不自绘原版树。

### Q5 — Ctrl+V 强制合成
- 新增 **Ctrl+V = 强制合成一次**（自然到停）。
- **全链尽力而为**：目标能合就合目标；否则遍历所有中间产物，**只合成材料足够的那步，缺料跳过**；直到没有任何一步可推进才**静默停**（不弹"材料不足"红字）。
- 现有 `pickNext` 需扩展：不只沿"第一个缺料输入"一条路径找，而是**枚举缺料输入对应的中间产物、挑任一能 `canCraft` 的**。
- 不新增 Ctrl+Shift+V 连续档（强制模式本身一次到自然停）。
- `AutoCraftClient.onKeyInput` 现排除 Ctrl+V（只认 plain/shift），需放开 Ctrl 判定。

### Q6 — NBT 同种物品两槽问题（**本次不修，仅记录**）
- **已确认存在**：`ClientCraftChain.findSource` 与 `CraftInventory.count` 均用 `isSameItemSameComponents`（含 NBT）匹配。当配方需要 2 个同种物品分放两槽、且两个物品 NBT 不同（如两把耐久不同的镐）时，第一个找到放入后，第二个因 NBT 不匹配找不到源 → 报"材料不足"中止（即"只放一个就停"）。
- **本次不修**。后续若要修：`findSource` 改按 item 类型（`isSameItem` 忽略 NBT，与配方 `Ingredient.test` 一致），并评估 `CraftInventory.count`/`canCraft` 预检是否同步统一口径。

## 文件改动（拟）

**新增：**
- 新增 1-2 个 `Config` 项：`treeGoalSide`（产物列左右）、`treeBackgroundTransparent`（背景透明）。
- （Q4）BoMScreen 左侧缩略条：新增 `client/BoMTreeSidebar`（或类似）自绘组件 + 交互。

**修改：**
- `client/TreeRenderer.java`：产物列镜像布局（Q1）、背景透明分支（Q2）、顶部总材料行渲染 + hitTest（Q3）。
- `client/InternalHelperImpl.java` / `TreeData.java`：`buildTrees` 产出"总材料行"数据（Q3）。
- `mixin/EmiApiMixin.java`：拦截 `viewRecipeTree`，检测 **Shift** → 走原版 BoMScreen 分支（Q4）。
- `client/AutoCraftClient.java`：`Ctrl+V` 判定 + 强制合成模式透传 (`attemptAutoCraft(force)`；`ClientCraftChain` 加 `force` 标记)（Q5）。
- `client/ClientCraftChain.java`：`pickNext`/`placeStep` 支持强制"尽力而为跳过缺料"（Q5）。
- `mixin/ConfigScreenMixin.java` / `client/ConfigResetButton.java`：新增设置项接入 EMI 设置页 + 重置（Q1/Q2）。

## 验证（runClient，逐条）
1. 设置"产物列=右"：力量药水树中目标在右、材料在左；切"左"镜像正常。
2. 背景透明开/关：树区背景与收藏夹一致 / 恢复深色。
3. 力量药水树：顶部总材料行 = 胡萝卜+金粒+火药+瓶（未持金胡萝卜时），下方保留分层。
4. 普通点树按钮 = 收藏页树视图不变；Shift+点 = 打开原版树界面 + 左侧产物缩略条，点产物联动切换。
5. Ctrl+V：缺料中间产物被跳过，仅合材料够的步骤，直至无步骤静默停。
6. 全部改动后 `./gradlew build` 必须 BUILD SUCCESSFUL。

---

# v2.1.0 联调修复（2026-08-15，已完成）+ AE2 联动（待办）

> 承接上方 Q1-Q6 批量特性计划；本小节记录联调发现的问题与修复，以及仍需的 AE2 联动。

## 已完成（联调修复）
- **UI**：数字大小可调（`treeNumberScale` + EMI 设置页"数字大小(%)"）；数量缩写 k/M/G/T；数量文字位置修复（图标右下角内侧）；总材料行 + 所有材料行超宽换行铺满（材料不越过分割线，不再遮挡右侧产物）；左上角拥有量不足红字。
- **数量感知**：`totalNeedOf`（同种材料多槽聚合总量）用于 `goalReady`/`canAfford`/`pickNext`/`findProducerToCraft`——修复"背包 1 个木板误判够"、"同种需多个只合一次"；`findRecipe` 叶子化改用累计总 need + 解叶子化；tag 子材料遍历具体成员递归（`BoM.getRecipe(tag)` 恒 null）。
- **放料**：`EmiRecipeFiller.clientFill(Destination.NONE)` 一次性放料（INVENTORY 会移走材料、CURSOR 留光标，均不可用）。
- **取产物**：`clickSlot`（`menu.slots.indexOf`/`container+containerSlot` 匹配菜单 index，模组 `Slot.index` 是容器 index 会越界）；`takeOutput` 分阶段（QUICK_MOVE 堆叠 → PICKUP → handler.craft fallback）；`onShowEnd` 用**背包数量增加**验证取走成功（模组 `output.hasItem` 可能恒 true 不刷新，不能依赖）。
- **精妙背包合成插件**：产物可取走、连续合成继续。
- 崩溃修复：handler 槽列表含 null、click 越界不终止链（辅助 fatal=false）。

## 已完成（2026-08-09 补，AE2 取产物 + 精妙背包堆叠）
- **AE2 合成终端取产物**（`client/Ae2Support.java` + `ClientCraftChain` AE2 分支）：
  - 根因（javap 实证 AE2 19.2.17）：`CraftingTermSlot.mayPickup()` **恒返回 false** → vanilla QUICK_MOVE/PICKUP 被拒；`AEBaseMenu.quickMoveStack` 同样检查 mayPickup → shift 也取不走；EMI handler.craft = `transferRecipe` 只放料。唯一途径是 AE2 action 机制：`InventoryActionPacket` → 服务端 `AEBaseMenu.doAction` → `CraftingTermSlot.doClick`（AE2 客户端 `AEBaseScreen.slotClicked` 对 CraftingTermSlot 的普通左键 = `CRAFT_ITEM`，shift = `CRAFT_SHIFT`，空格 = `CRAFT_ALL`，右键 = `CRAFT_STACK`）。
  - 实现：`build.gradle` 加 `compileOnly files("libs/appliedenergistics2-19.2.17.jar")`（本地 jar，同 EMI 方式；运行时 mod 仍由 run/mods 加载）；`Ae2Support` 用 `ModList.isLoaded("ae2")` 门禁 + 方法体惰性引用 AE2 类（未装 AE2 不加载不报错）；取产物发 `InventoryActionPacket(CRAFT_ITEM, slotIndex, 0)` = 左键点击结果槽，合成一份到光标 → `onShowEnd` 先把光标产物放回背包再验证（背包数量增加）；`WirelessCraftingTermMenu` 继承 CraftingTermMenu 自动覆盖。
  - 语义：CRAFT_ITEM 的 doClick 消耗**合成格材料**（makeItem = AppEngCraftingSlot.onTake）+ 网络有料时等价补充（craftItem 从网络提取，postCraft 补回合成格）→ 网络无料退化为纯合成格消耗，产物均正确进背包；无重复扣料。
- **AE2 合成后"重新填充"修复**（用户反馈 bug）：CRAFT_ITEM 合成后 AE2 会把**网络等价材料补回合成格**（网络借料设计），合成格被重新填满、output 持续显示、配方材料无法正常消耗。修复：取产物后立即清空合成格。
- **AE2 结果槽残留修复**（用户反馈 bug"卡一个东西在产物框"）：清格初版用 `CraftingTermMenu.clearToPlayerInventory()`——它用 `setItemDirect` **绕过槽直接操作 InternalInventory，不触发 `slotsChanged`** → `updateCurrentRecipeAndOutput` 不执行 → 结果槽（CraftingTermSlot）**服务端残留上一产物**。改为**逐格 vanilla QUICK_MOVE 清格**（`Ae2Support.clearCraftingGrid`）：走 `CraftingMatrixSlot.remove` → `menu.slotsChanged` → 结果槽同步清空 + 材料经 `AEBaseMenu.quickMoveStack` 回背包（排除 FakeSlot/合成格，只进玩家背包）。另加 `resultRetryTicks()`：AE2 菜单取产物验证等待 4→12 tick（每步 CRAFT_ITEM + 清格最多 10 个包，防"同步未到 → 误重试 → 多合成一份"）。
- **精妙背包产物堆叠**：`placeCursorIntoInventory` 改为**优先堆叠**——光标产物先找同种且未满的输入槽（`findStackableInventorySource`）放回，找不到才放空槽；连续合成产物不再散落空槽。
- 验证：`./gradlew build` → **BUILD SUCCESSFUL**。

## 已完成（2026-08-09 补，性能优化 + 数字位置 + 日志清理）
- **合成量大时打开工作台卡顿修复（性能热点，javap/代码分析）**：
  - `InternalHelperImpl.hasEnough` 原来**每次调用都重建 EmiPlayerInventory 库存快照**（canObtain 递归每个节点每层一次 → 合成量大组合爆炸）。改为**树重建时构建一次快照**（`invSnapshot`，buildTrees 内刷新），hasEnough 全部复用。
  - `workbenchProducers` 原来每次递归都调 `EmiApi.getRecipeManager().getRecipesByOutput`（EMI 配方查询）+ `outputAmount`/`is2x2Recipe` 重复计算。改为 **`ProducerEntry` 缓存**（按材料键缓存配方候选，预计算 perBatch/is2x2；EMI 配方集合会话内静态），canObtain 直接消费。
  - `TreeRenderer` 拥有量检测原来**每个 Placed 每帧重建库存快照**（合成量大时渲染热点）。改为 render 开头构建**一次快照**（`invSnap`）共用。
- **数量数字位置修复（两轮）**：① 第一版数字 `translate(p.x, p.y+14)` + `drawString(y=1)` 未考虑字体高度，文字溢出图标下方；改为图标格内右下角坐标。② 用户反馈"物品叠在数字上方"——真正根因（javap 实证 + EMI `renderAmount` 反证）：`renderFakeItem` 物品模型 z=32 且**开启深度测试**，后画数字 z=0（`guiText` LEQUAL）深度更远被**深度测试剔除**，图标外露出的部分即用户看到的"数字在物品下方"。EMI 自己的数量渲染正是 `translate(0,0,200)`。修复：数量与拥有量 `translate(p.x, p.y, 200)`，数字完整可见于图标格内右下角。
- **诊断日志清理**（update.md 原待办）：删除 `ClientCraftChain` 全部 EBS INFO 调试日志（chain step/DONE/fillViaEmi/onShowEnd/VERIFIED/pickNext/findProducer）与 `AutoCraftClient` 3 处（key pressed/hovered/starting chain）；保留 warn/debug（click 越界/异常、fallback 失败、quiet stop）。
- 验证：`./gradlew build` → **BUILD SUCCESSFUL**。

## 待办
- **v2.1.0 用户 runClient 验收**（见 docs/03 5.7 / 6.15）：工作台/背包/箱子/AE2 终端全场景；含本次新增的 AE2 取产物 + 清格 + 性能优化回归。
