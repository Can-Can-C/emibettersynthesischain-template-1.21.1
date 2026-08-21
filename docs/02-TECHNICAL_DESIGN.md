# 技术设计文档（Technical Design）

> 项目：EMI Better Synthesis Chain（NeoForge 1.21.1 / EMI 1.1.24）
> 依赖事实基于本地 jar `libs/emi-1.1.24+1.21.1+neoforge.jar`（javap 实证）。EMI 锁定 1.1.24。

## 1. 架构总览

```
客户端
├─ mixin/EmiApiMixin            拦截 viewRecipeTree（添加/切换树视图）
├─ mixin/EmiScreenManagerMixin  滚轮路由 + 树区悬停屏蔽
├─ mixin/SidebarPanelMixin      收藏页渲染合成树
├─ mixin/RecipeTreeButtonWidgetMixin  配方页树按钮：切换增删 + 激活纹理
├─ mixin/BoMFlagMixin           标记 BoM.setGoal（区分添加/切换）
├─ mixin/AbstractContainerScreenMixin/Accessor  成功提示叠加绘制 + 容器屏 leftPos/topPos
├─ mixin/ConfigScreenMixin      EMI 设置页注入本 mod 设置组
├─ client/IEmiInternal           业务接口（隔离 EMI 内部访问）
├─ client/InternalHelperImpl     EMI 实现 + 树构建（成本聚合 + 可合成判定）
├─ client/TreeManager            多树集合 + 目标配方 + NBT 持久化
├─ client/TreeMode               开关/宽度/滚动（惯性+滚动条）
├─ client/TreeRenderer           行布局渲染
├─ client/ClientCraftChain     纯客户端点击链（EMI handler + vanilla 点击包，tick 驱动）
├─ client/CraftInventory       屏幕感知库存提供器（handler.getInventory / 玩家背包）
├─ client/AutoCraftClient      自动合成快捷键/悬停 → 找 handler → 启动链条
├─ client/MessageOverlay       合成成功/失败提示（容器屏顶部）
```

- 树显示为**客户端纯 UI**；自动合成为**纯客户端**（v2.0.0 起：用 vanilla 点击包模拟玩家点击，无自定义包、无服务端逻辑，服务端无需装本 mod）。
- Mixin 注入 EMI **内部类**，升级需复查（见 §8 风险）。

## 2. 依赖与构建（已落地）
- `build.gradle`：删除不可达的 `repo.sleeping.town`；EMI 用 `compileOnly files("libs/emi-1.1.24+1.21.1+neoforge.jar")` + `localRuntime files(...)`（`localRuntime` 只进 runClient，不打进 mod 包）。
- `neoforge.mods.toml`：启用 Mixin；`emi` 依赖（required, AFTER, CLIENT）；**neoforge 依赖 versionRange `[21.0.0,)`**（宽下限，配合 `minecraft_version_range=[1.21.1]` 可跑 21.1.x 任意补丁）。
- `emibettersynthesischain.mixins.json`：包 `com.cancan.emibettersynthesischain.mixin`，client 列表见 jar。

## 3. EMI 内部访问隔离（用户指定）
> 业务代码只依赖接口 + 纯业务模型；EMI 内部类引用集中在实现类与 mixin。

- `client/IEmiInternal.java`：定义业务需要的 EMI 能力（focusFavoritesSidebar / getHoveredItemStack / getAddTarget / getGoalRecipeId / getFavoritesPanelBounds / getMouseX·Y / isFavoritesPanel / getPanelBounds / widen·restoreFavoritesPanel / buildTrees）。
- `client/InternalHelperImpl.java`：`IEmiInternal` 实现（单例），**所有** EMI 内部类引用（`EmiScreenManager`、`BoM`、`EmiScreenManager$SidebarPanel` 等）只在此处。
- **accessor 限制（实证）**：`@Accessor/@Invoker` mixin 类会被 Mixin 消费，**不能从普通代码直接调用**（`NoClassDefFoundError`）；只能"目标实例 cast 到接口"（如 `RecipeButtonWidgetAccessor`）。纯静态类（`EmiScreenManager`）不适用该模式。

## 4. EMI 关键点速查（javap 实证 1.1.24）

| 用途 | 类 / 成员 |
|------|-----------|
| 树按钮（左下角）→ 树 | `EmiScreenManager.tree`（`SizedButtonWidget`）→ `EmiApi.viewRecipeTree()` |
| 配方页树按钮 | `dev.emi.emi.widget.RecipeTreeButtonWidget`（`getTextureOffset` +36=激活纹理、`mouseClicked`） |
| 配方按钮父类 | `RecipeButtonWidget`（`protected recipe/x/y`；`render` 用 `v + getTextureOffset`） |
| 收藏页 | `SidebarType.FAVORITES`；`EmiScreenManager.focusSidebarType/toggleSidebarType/getPanelFor/forceRecalculate` |
| 面板渲染 | `EmiScreenManager$SidebarPanel.render(EmiDrawContext,int,int,float)`、`getBounds()`、`getType()` |
| 面板宽度 | `SidebarSettings.LEFT.size()`（`IntGroup.values`） |
| 默认配方 | `BoM.getRecipe(ingredient)` = `addedRecipes`→`defaultRecipes`，`disabledRecipes` 中返回 null；`BoM.setGoal`、`BoM.tree` |
| 悬停 | `EmiApi.getHoveredStack(bool)`；`EmiScreenManager.getHoveredStack(IIZZ)`（内联遍历面板空间取 stack） |
| 滚轮路由 | `EmiScreenManager.mouseScrolled(double,double,double)` → `getHoveredPanel` → `panel.scroll(-deltas)`，返回 true 则 `MouseMixin` 取消原版 |
| 渲染 stack | `EmiDrawContext.drawStack(ingredient,x,y,flags)`；flags：`RENDER_ICON=1 / RENDER_AMOUNT=2 / RENDER_INGREDIENT=4 / RENDER_REMAINDER=8`（流体不受 RENDER_AMOUNT 控制） |
| 标签 | `TagEmiIngredient`（`public final TagKey<?> key`）；`EmiApi.displayRecipes(tag)` 有专门 TAG 处理 |
| 选择材料 | `RecipeScreen.resolve = ingredient` + `EmiApi.displayRecipes(...)`（出现 ResolutionButtonWidget，ctrl+单击选材料） |
| tooltip | `EmiStack.getTooltipText()`（List<Component>）→ `GuiGraphics.renderTooltip`；`EmiIngredient.getTooltip()`（List<ClientTooltipComponent>） |
| 组件感知 | `ItemStack.isSameItemSameComponents(a,b)`（isSameItem 只比 Item）；`ItemStack.hashItemAndComponents(stack)`（含组件哈希） |

## 5. 合成树实现

### 5.1 树数据（InternalHelperImpl.buildTrees → TreeData）
- 输入：`TreeManager` 每项目标物品 + 其"最后一步配方 id"。
- `TreeData`：`goal`（amount=配方输出量）、`directInputs`（目标配方直接输入，amount=配方消耗量）、`rows`（聚合材料行，最深→最浅）、`byproducts`（过量产出，amount=excess）。全部为 `EmiIngredient`（物品/流体/标签）。
- **成本聚合（worklist）**：need（总需要量）、`batch=ceil(need/单次产出)`、`produced=batch×单次产出`、`excess=produced-need`（>0 即副产物，排除目标）。迭代上限 3000 防环。
- **配方选择**：
  - **目标（最后一步）**：加入时查看的配方（`TreeManager` 存的 recipeId）→ `BoM.getRecipe` → `firstForwardRecipe`（回退正向产出配方）。**不需要默认配方**。
  - **中间材料**：`findRecipe` = `BoM.getRecipe`（尊重设为默认/取消默认）；**跳过"反向/分解类"配方**（`isReverse`：输出数量>输入数量且可逆，如 铁块→9铁锭、红石块→9红石），避免环。
  - **叶子化（数量感知）**：`findRecipe(content, needAmount, forbidden)` 用**累计总需要量** `needAmount` 判断"库存已够"（而非单次 `getAmount`）——避免"背包有 1 个木板就叶子化、不向下拆解"；`addNeed` 累加后若"叶子但总 need 超库存"则**解叶子化**重新拆解（Agg.recipe 非 final，batch 归零重展开）。
  - **标签**：`BoM.getRecipe(tag)` 解析了才拆解，否则叶子。
- **去重键 `key()`**：标签 `"tag:"+tagKey.location()`；物品/流体 `id#hashItemAndComponents(item)`（组件感知，区分不同药水/时长）。
- **节点"可合成"判定（`TreeItem.canCraft`，标红依据）**：`InternalHelperImpl.canObtain(content, amount, preferred, depth)` 客户端干跑（与服务端一致）——自身数量足够 → true；否则**任一**产出它的**工作台配方**（首选 BoM 默认，再补全部工作台产出配方，跳过分解类，含标签成员）的子材料链式可得 → true；批数=ceil(需要/单批产出)，深度≤6；**非工作台配方 / 中间 3×3 未打开工作台界面 → false（标红）**。目标与材料节点均按此判定。

### 5.2 布局与渲染（TreeRenderer）
```
[栅栏×3] │ [原木×2]                ← 最深
         │ [木板×6]
         │ [木板×4] [木棍×2]        ← 目标配方直接输入
─────────┴───────────────
副产物区： [木板×2] [木棍×2]
```
- 目标在左（竖分割线），材料区在右**从上到下分行横排**（最深→最浅，最后一行=直接输入）；副产物区在底部。
- **纯图标** + **右下角数量小字（0.5 倍自绘）**，无文字/箭头；目标与材料间**无连线**；已解析标签用**短连线**连到右侧"所选成员"图标；标签材料加 `RENDER_INGREDIENT`（标签徽记）。
- **数量显示（自绘，不用 EMI `RENDER_AMOUNT`）**：
  - 物品：`formatItemAmount`——1000 进制多级缩写 `k / M / G / T`（<1000 原样，一位小数去尾零）。
  - 流体：`formatFluidAmount`（`mB / L / K L / M L / G L`，<1000mB 用 mB，两位小数去尾零）。
  - **左上角拥有量**：非目标物品节点，当前拥有量（`CraftInventory.count`）< 需要量时左上角红字小字显示拥有量，足够时不显示。
  - **深度测试**：物品图标 `renderFakeItem` z=32 且开启深度测试，自绘数字/拥有量必须 `pose.translate(z=200)`（同 EMI `renderAmount` 做法）才能通过深度测试画在图标之上——否则数字被图标"叠住"只露出图标外部分。
- **总材料行（leafTotal）换行铺满**：种类过多时按材料区右界（= 分割线 `dividerX`，材料不越过分割线以免遮挡目标）换行铺满；`contentHeight` 用 `leafRowCount` 计物理行数保持一致。
- **紧凑间距**：`PAD=3`、`LEFT_COL=24`、`ROW_H=16`、`ITEM_GAP=4`、`TREE_GAP=8`、`BP_GAP=6`。
- **悬停**：悬停树时**所有 `canCraft=false` 节点叠红**（不可合成），悬停节点可合成白框/不可合成红框；物品栏样式 tooltip（`getTooltipText()` + `GuiGraphics.renderTooltip`，scissor 外绘制）。
- **滚动**：`TreeMode` 惯性（velocity 衰减）+ 可拖动滚动条 + 钳制到 `[0,maxScroll]`（用未滚动内容高度算）+ 添加新树自动滚到底。

### 5.3 交互（EMIBettersynthesischainClient 的 InputEvent.MouseButton.Post）
- **左键点物品** → `EmiApi.displayRecipes(content)`；点标签 → `RecipeScreen.resolve = tag` + `displayRecipes(tag)`（可选择具体材料）。
- **左键拖滚动条滑块** → `TreeMode.startDrag/stopDrag`（RELEASE 无条件 stopDrag，拖出面板也能结束）。
- **右键点最终产物列** → `TreeManager.remove(index)`。
- **防误触（三层）**：① PRESS 前 `bounds.contains(mx,my)`（鼠标须在收藏面板内）；② `mc.screen != lastScreen`（每 tick 记录）跳过——`InputEvent.MouseButton.Post` 在 vanilla 处理**之后**触发，右键方块打开容器时 bounds 已变；③ `ScreenEvent.Opening/Closing` 置位 `screenTransition`，界面切换后的下一次 PRESS 跳过——覆盖"关闭容器界面时 EMI 侧边栏 bounds 残留过期面板、下次打开界面误删树"的根因（代价：界面切换后第一次树操作被吞，需再点一次）。

### 5.4 滚轮与悬停屏蔽（EmiScreenManagerMixin）
- `mouseScrolled`：仅当鼠标在收藏面板（树区）上时 `TreeMode.addScroll(-amount)` + `return true` 消费；否则放行 EMI/原版。
- `getHoveredStack(IIZZ)`：树模式且鼠标在收藏面板上 → 返回 `EmiStackInteraction.EMPTY`（避免点穿到下层收藏页、tooltip 重叠）。

### 5.5 添加入口
- 配方页树按钮 `RecipeTreeButtonWidget`：激活纹理（getTextureOffset +36，产物在树中时）；点击切换加入/删除（加入时确保页面打开 + 记录 recipeId）。
- EMI keybind（viewStackTree / 点树按钮）→ `EmiApi.viewRecipeTree` 被拦截 → `getAddTarget`（配方界面读 BoM.tree.goal.ingredient，其余取悬停）+ `getGoalRecipeId` → `TreeManager.add`。
- `BoMFlagMixin` 标记 `BoM.setGoal`：`viewRecipeTree` 时"刚 setGoal = 添加（不关页面）；否则 = 左下角切换开关"。

### 5.6 持久化（TreeManager）
- `config/emibettersynthesischain/trees.json`：每条目 = 目标物品完整 NBT（含组件）+ 最后一步配方 id。
- 读取延迟到进世界后（`resolvePending`，需 `RegistryAccess`）；兼容旧纯 id 格式。

## 6. 配置项（Config.java）
| 键 | 类型 | 默认 | 说明 |
|----|------|------|------|
| `treeSidebarWidth` | int | 10 | 树模式下左侧栏物品列数（宽） |
| `treeRowSpacing` | int | 16 | 合成树行高（px） |
| `treeItemGap` | int | 4 | 同行物品间距（px） |
| `treeTreeGap` | int | 8 | 树间垂直间距（px） |
| `treeByproductGap` | int | 6 | 副产物区上方间距（px） |
| `treeRedMarking` | bool | true | 悬停树时材料不足节点是否标红 |
| `treeGoalSide` | enum | right | 产物列在左还是右（`left`/`right`，默认右，v2.1.0 Q1） |
| `treeBackgroundTransparent` | bool | true | 合成树背景是否透明（默认透明；关闭恢复深色，v2.1.0 Q2） |
| `treeNumberScale` | double | 0.5 | 图标上数字（数量/拥有量/流体用量）缩放倍数（0.25-1.0） |
| `autoCraftShowTicks` | int | 6 | 可见合成链每步显示时长（tick） |
| `autoCraftGapTicks` | int | 2 | 可见合成链步间间隔（tick） |
| `autoCraftFailureMessages` | bool | true | 无法合成时是否红字提示 |

`~~autoCraftWorkbenchRadius~~` 已移除：v1.0.0 改判"打开工作台界面"，v2.0.0 起改由 EMI handler 判定（当前界面有无支持配方的 handler）。

### 6.1 EMI 设置页注入（ConfigScreenMixin）
- **参考**：`emi-plus-plus-2` 的 `ConfigScreenMixin`（EMI 1.1.24 同版本实证）。
- **原理**：EMI 设置页 `ConfigScreen.init()` 反射 `EmiConfig` 静态字段渲染条目，附属无法直接注册配置。做法是 Mixin `ConfigScreen`：`@ModifyArg` 拦截 `init` 里 `addWidget(ListWidget)`，往 `ListWidget` 追加 `GroupNameWidget`/`SubGroupNameWidget`（分组头）+ `IntWidget`/`BooleanWidget`（值控件，`ConfigScreen.Mutator` 读写本 mod `Config`），并把 `root.children`/`widget.parentGroups`/`sub.children` 互相连好（可折叠、可搜索）。`@Inject addJumpButtons` TAIL 加跳转按钮（`ConfigJumpButton` → `jump("ebs")`）。
- **落盘**：`Mutator.setValue` 里 `Config.X.set(v)` 后调 `Config.save()`（`ModConfigSpec.save()` 写回 `config/emibettersynthesischain-common.toml`）。
- **重置按钮**：组尾 `ConfigResetButton`（`client/`，非 mixin 包；`ConfigEntryWidget` + `EmiPort.newButton`）→ `Config.resetAll()` 恢复全部默认并落盘。

## 7. 目录结构
```
src/main/java/com/cancan/emibettersynthesischain/
├─ EMIBettersynthesischain.java        @Mod（注册配置）
├─ EMIBettersynthesischainClient.java  客户端 setup + 鼠标/滚轮交互 + 快捷键注册
├─ Config.java                         配置（treeSidebarWidth）
├─ client/
│  ├─ IEmiInternal.java                隔离接口（EMI 能力抽象）
│  ├─ InternalHelperImpl.java          EMI 实现 + 树构建（成本聚合 + canObtain）
│  ├─ TreeData.java                    纯业务模型（goal/directInputs/rows/byproducts）
│  ├─ TreeManager.java                 多树 + 目标配方 + NBT 持久化（trees.json）
│  ├─ TreeMode.java                    开关/宽度/滚动（惯性+滚动条）
│  ├─ TreeRenderer.java                渲染 + hitTest
│  ├─ TreeAddFlag.java                 BoM.setGoal 标记
│  ├─ ConfigResetButton.java           EMI 设置页一键重置按钮
│  ├─ AutoCraftClient.java             自动合成快捷键/悬停 → 找 handler → 启动链条
│  ├─ ClientCraftChain.java            纯客户端点击链（tick 驱动，EMI handler + vanilla 点击包）
│  ├─ Ae2Support.java                  AE2 合成终端取产物（可选，InventoryActionPacket → doClick）
│  ├─ CraftInventory.java              屏幕感知库存提供器（handler.getInventory / 玩家背包）
│  └─ MessageOverlay.java              合成成功/失败提示（容器屏顶部）
└─ mixin/
   ├─ EmiApiMixin / EmiScreenManagerMixin / SidebarPanelMixin
   ├─ RecipeTreeButtonWidgetMixin / RecipeButtonWidgetAccessor / BoMFlagMixin
   ├─ BoMScreenMixin（Shift 开原版 BoMScreen + 左侧产物缩略条联动）
   ├─ AbstractContainerScreenMixin（MessageOverlay 渲染）
   └─ ConfigScreenMixin
```

## 8. 风险与约束
- **版本敏感**：Mixin 全指向 EMI 内部类，锁定 1.1.24；升级需回归所有注入点。
- **窄面板**：长树靠滚动 + 可调宽缓解。
- **跨版本**：NeoForge 1.21.1（范围放宽到 21.0.0+）；自动合成纯客户端（无自定义包，服务端无需装 mod）。
- **组件感知**：物品按 `id#组件哈希` 去重，区分不同 NBT（药水/时长/附魔）。

## 9. Phase 3 — 自动合成（v2.0.0 纯客户端）

### 9.1 入口（AutoCraftClient）
"自动合成"快捷键（`InputEvent.Key` 原始按键事件，仿 EMI `matchesKey`；`V`=一次、`Shift+V`=连续，`mods==GLFW_MOD_SHIFT`，排除 Ctrl+V 等）：取悬停物品（树模式优先 → EMI 当前坐标 → 槽位遍历）→ 找产出它的**工作台配方**（`BoM.getRecipe` 优先）→ **纯客户端**：当前界面需有支持它的 EMI handler（`EmiRecipeFiller.getFirstValidHandler`）——无 handler（箱子/熔炉等）红字"该界面不支持自动合成"；有 → 启动 `ClientCraftChain`。未悬停物品 / 无工作台配方红字提示。

### 9.2 库存检测（CraftInventory，屏幕感知）
- `currentScreenInventory()`：`EmiRecipeFiller.getAllHandlers(screen)` 取首个 `StandardRecipeHandler` → `getInventory(screen)`（AE2 合成终端 = 网络 + 背包；工作台/背包 = 玩家背包）；无 handler → `EmiPlayerInventory.of(player)`。
- 树标红（`InternalHelperImpl.hasEnough`/`canObtain`）与链条预检都吃这个快照——**跟随当前界面**。
- `EmiPlayerInventory.canCraft(recipe)`：按 EmiStack 聚合计数干跑，判断直接输入是否足够。

### 9.3 点击链（ClientCraftChain，tick 驱动）
- **每步**（目标或中间产物）：`pickNext()`（EMI 祖先栈 finder，数量感知）→ **`isWorkbenchRecipe` 硬检查**（仅工作台配方）→ `canFitCurrentGrid`（bounding box）→ `canAfford`（数量感知）→ **`fillViaEmi`**（EMI `clientFill(NONE)` 一次性放料）→ 等 `showTicks` → **取产物（`takeOutput` 分阶段 + 背包数量验证）** → 等 `gapTicks` → 下一步。
- **仅工作台配方（`isWorkbenchRecipe`）**：backingRecipe 必须为 `CraftingRecipe`。目标配方（`AutoCraftClient.findCraftingRecipe`）与中间产物（`findProducerToCraft` 的 `BoM.getRecipe` 结果）都过滤——非工作台默认配方（如玩家把熔炉设为默认）不再放料进合成格；`canFitCurrentGrid` 非 `EmiCraftingRecipe` 一律 false。`pickNext` 返回 null 时用 `hasNonWorkbenchDefault()` 区分原因：目标输入存在非工作台默认 → 红字**"该配方无法在工作台内进行"**；否则"材料不足"。
- **速度**：`showTicks()` 直接用设置值（`Math.max(2,...)`，移除隐藏 4 tick 下限——此前设置页调到 2 也不生效）；取产物验证等待 `resultRetryTicks()` 普通界面 2 / AE2 12 tick。最快每步 ≈ showTicks(2)+2+gap(0) = 4 tick（0.2s）。
- **放料（`fillViaEmi`）**：`EmiRecipeFiller.getStacks(handler, recipe, screen, 1)` → `EmiRecipeFiller.clientFill(handler, recipe, screen, stacks, Destination.NONE)` 一次性放料（清格→整堆拾起→精确放入→余料归还，光标受控）。`Destination.NONE`=只放料不移走；`INVENTORY`（放料后把材料移回背包→结果槽空）、`CURSOR`（余料留光标）不可用。
- **合成格 fit（`canFitCurrentGrid`/`fitsGrid`）**：配方非空输入 bounding box 判定（3×3 shaped 包围盒 ≤ 网格；shapeless 非空数 ≤ 格数），不用 EMI `canFit`（3×3 在 2×2 返回 true 不可靠）。背包 2×2 → 3×3 拦截提示"需打开工作台"。
- **取产物（`takeOutput` 分阶段，配合 `onShowEnd` 背包数量验证）**：
  - **槽位 index**：`clickSlot` 用 `menu.slots.indexOf` / `container+containerSlot` 匹配**菜单槽 index**（模组 handler 返回的 `Slot.index` 是容器 index，如精妙背包 172 > slotsSize 156 → 越界）。
  - **AE2 合成终端分支（`client/Ae2Support`）**：结果槽 `CraftingTermSlot.mayPickup()` **恒 false** → vanilla QUICK_MOVE/PICKUP 与 `handler.craft`（AE2 只放料 transferRecipe）均取不走。改用 AE2 action 机制：发 `InventoryActionPacket(InventoryAction.CRAFT_ITEM, slotIndex, 0)`（= 玩家左键点击结果槽）→ 服务端 `AEBaseMenu.doAction` → `CraftingTermSlot.doClick`：消耗合成格材料（makeItem = AppEngCraftingSlot.onTake）+ 网络有料时等价补充（craftItem 从网络提取、postCraft 补回合成格空位）→ 产物进玩家光标 → `onShowEnd` 先把光标产物放回背包再验证。
  - **防"合成后重新填充" + 结果槽残留（`Ae2Support.clearCraftingGrid`）**：CRAFT_ITEM 后 AE2 会把网络等价材料补回合成格（网络借料设计）。清格**不能**用 `clearToPlayerInventory()`——它 `setItemDirect` 绕过槽直接操作 InternalInventory，**不触发 `slotsChanged`** → 结果槽服务端残留上一产物（"卡一个东西在产物框"）。改为**逐格 vanilla QUICK_MOVE 清格**：走 `CraftingMatrixSlot.remove` → `slotsChanged` → `updateCurrentRecipeAndOutput` 结果槽同步清空；材料经 `AEBaseMenu.quickMoveStack` 回玩家背包（`isValidQuickMoveDestination` 排除 FakeSlot/合成格）。AE2 菜单取产物验证等待 `resultRetryTicks()`=12 tick（每步 CRAFT_ITEM + 清格最多 10 个包，防同步慢误重试多合成）。
  - AE2 未安装时 `ModList.isLoaded("ae2")` 门禁短路，不加载 AE2 类（方法体惰性引用）。`WirelessCraftingTermMenu` 继承 CraftingTermMenu 自动覆盖。
  - **分阶段**（按 `retries`，非 AE2 路径）：0=QUICK_MOVE（shift，服务端自动堆叠）；1=PICKUP 手动拾起+放回背包（**优先堆叠到同种未满槽**，修复精妙背包产物散落空槽）；2+=`handler.craft` fallback（精妙背包等 override 取产物）。每次只做一阶段，避免 QUICK_MOVE 生效时被 fallback 干扰产生第二份不堆叠产物。
  - **成功判定**：`onShowEnd` 用**背包产物数量增加**（`CraftInventory.count` > 取走前基线）验证——模组 `output.hasItem()` 可能恒 true 不刷新，不能依赖；未增才进下一阶段/重试，超限提示"无法取走合成产物"；验证前先处理光标残留（AE2 CRAFT_ITEM / PICKUP 产物在光标 → 先放回背包）。
- **数量感知**：`pickNext` 用 `goalReady()`、`placeStep` 用 `canAfford(recipe)`、`findProducerToCraft` 子材料判断均用 **`totalNeedOf`（同种材料多槽聚合总量）**，不用 EMI `canCraft`（按"种类"判断，同种需 6 个时 1 个也算够）；`findProducerToCraft` 对 **tag 子材料遍历具体成员递归**（`BoM.getRecipe(tag)` 恒 null）。
- **合成成功提示**：目标步用 `goalRecipe.getOutputs()` 的物品名。
- **V 键范围**：`AutoCraftClient.getHoveredStack` 只在**树模式 + `hit.isGoal()`（最终产物）**返回非空；非树模式返回 EMPTY。
- **中止**：每 tick 校验 `mc.screen`/`menu` 仍同屏，关界面即停；无玩家/无世界清理 `active`；`click` 越界/异常不终止链（辅助点击 fatal=false）。

### 9.4 防环（EMI 祖先配方栈，替换 isReverse 启发式）
- 树构建 worklist：每个材料 `Agg.path` 携带到达它的祖先配方集；展开时 `forbidden = path ∪ {recipe}`，子材料 `findRecipe(content, forbidden)` 遇路径上已用配方 → null（叶子剪枝）。
- `canObtain` / 链条 `findProducerToCraft`：递归带 `Set<EmiRecipe> ancestors`，路径重复即剪枝。
- 分解类默认配方由 EMI 默认数据（`recipe_defaults.json`）本身避免；用户强制设为默认时祖先栈保证安全显示。目标配方选择仍保留 `isReverse`（避免把分解配方当目标）。

### 9.5 消息（MessageOverlay，纯客户端）
成功"合成成功: X"白字（X = `goalRecipe.getOutputs()` 输出名）；失败红字：该界面不支持自动合成 / 材料不足 / **该配方无法在工作台内进行**（非工作台默认配方）/ 未悬停物品 / 仅工作台配方 / 需打开工作台后合成；受 `autoCraftFailureMessages` 开关控制。

### 9.6 健壮性（P2 项，评审后）
- **`InternalHelperImpl.hasEnough`**：异常路径返回 **`false`**（无法确认足够即按"材料不足"标红，保守处理，不再 `return true` 静默放行掩盖错误）；空物品/流体计数的 `return true` 分支保留（非异常）。
- **`InternalHelperImpl.maybeRefreshOnInventoryChange`**：背包刷新 hash 用 **`ItemStack.hashItemAndComponents(s)`**（含 NBT 组件）+ `getCount()`，并跳过空堆——同 item 换组件（不同药水/时长/附魔）也能触发树缓存失效，组件感知标红用最新快照。
- **`TreeAddFlag`**：`setGoal→viewRecipeTree` 判定窗口从 50ms 硬编码提为常量 **`ADD_WINDOW_NANOS = 500ms`**；两事件在 EMI 同一帧/调用栈紧邻发生，放宽窗口降低慢机卡顿下"添加"被误判为"切换"的概率，仍能区分左下角树按钮的纯切换。
- **`ClientCraftChain.tick`**：无玩家 / 无世界（切存档、断开连接）时**清理全局 `active`**——否则残留会让 `start()` 一直返回 `false`（"已进行中"），自动合成永久失效直至重启。

### 9.7 AE2 终端内自动合成：网络材料识别与放料（需求 11，v2.1.1）
- **根因（javap 实证 AE2 19.2.17）**：
  1. **识别层**：AE2 的 `AbstractRecipeHandler.getInventory` 只在配置 `exposeNetworkInventoryToEmi=true`（**默认 false**，反汇编 `iconst_0`，注释还警告"可能引起性能问题"）时把 `getClientRepo().getAllEntries()` 并进库存；默认关闭时 handler 的库存**只有玩家背包槽** → 树标红/链条预检看不到网络。
  2. **放料层**：EMI `EmiRecipeFiller.getStacks/clientFill` 内部用 `handler.getInventory` 判断可合成（同上不含网络），且放料只能从 `getInputSources`（= PLAYER_INVENTORY + PLAYER_HOTBAR + CRAFTING_GRID，**无网络**）拾取 → 背包无料时 `getStacks` 返回 null，放料失败"无法放置该配方的材料"。
- **识别方案（`Ae2Support.mergedInventory(screen)`）**：**不依赖 AE2 配置**，自建"玩家背包槽位（inputSources）+ 网络存储条目（`getClientRepo().getAllEntries()`，`storedAmount>0`）"合并 `EmiPlayerInventory`；网络条目用 AE2 自己的 `EmiStackHelper.toEmiStack(GenericStack)` 转换（组件保真、数量无 64 上限）。`CraftInventory.currentScreenInventory()/current(recipe)` 在 AE2 合成终端（`CraftingTermMenu`）**优先返回合并库存** → 树标红（`invSnapshot`）、链条预检（`canAfford`/`goalReady`/`findProducerToCraft`）全链路可见网络。门禁：未装 AE2 返回 null 回退原逻辑（`ModList.isLoaded("ae2")` 短路，方法体惰性引用 AE2 类）。
- **放料方案（`ClientCraftChain.fillViaEmi` AE2 分支）**：AE2 终端**不走 EMI clientFill**（其 getStacks 依赖 AE2 配置的 getInventory，网络不可见），改调 **`handler.craft(recipe, ctx)`** = AE2 的 `transferRecipe(recipe, ctx, true)`：
  - `isCraftingRecipe` 校验 → `fitsIn3x3Grid` → `getGuiSlotToIngredientMap`（配方输入→合成格槽位）→ **`CraftingTermMenu.findMissingIngredients`**（javap 实证：查合成格/玩家背包/**网络**缺料）→ 服务端从**背包+网络**提取材料放进合成格（AE2 网络借料机制，一步到位）。
  - 普通界面（工作台/背包/精妙背包等）保持 EMI clientFill 不变。
- **默认配方唯一（用户指定策略"设默认，断了就断了"）**：
  - 树标红 `InternalHelperImpl.producersOf`（canObtain 用）：**只用默认配方**——preferred（父配方视角/该材料已解析默认）优先，否则 `BoM.getRecipe(content)`（玩家设为默认 / EMI 数据默认）；删除 `buildProducers`（`getRecipesByOutput` 全部候选）与 `producerCache`。默认链断 → 标红。
  - 链条 `ClientCraftChain.findProducerToCraft`：**只用 `BoM.getRecipe`**（默认），不逐个尝试其它产出配方；默认不可用/非工作台/祖先栈防环 → null → 链条停止（"材料不足"/"该配方无法在工作台内进行"）。
  - 树标红与链条口径一致，不再出现"树绿但合不了"。
- **3×3 合成格认可**：`InternalHelperImpl.hasCraftingMenuOpen` 增加 **AE2 合成终端**判定（`containerMenu instanceof CraftingMenu || Ae2Support.isCraftingTermMenu(...)`）——AE2 合成格为 3×3，与工作台等价；否则中间 3×3 材料在 AE2 终端被误标红（"只能检测一层"）。
- **与既有路径关系**：AE2 取产物仍走 `Ae2Support.takeOutput`（CRAFT_ITEM）+ `clearCraftingGrid`（网络借料后清格，v2.1.0 既有逻辑）；放料材料来源（背包/网络）由 AE2 transferRecipe 管理，不改变取产物/清格行为。

## 10. 批量特性（Q1-Q6，拟 v2.1.0）

> 与需求 6-10 对应；每项均为新增/增强，不破坏既有路径。详见 `update.md`。

### 10.1 产物列左右可调（Q1，需求 6）
- `Config` 新增 `treeGoalSide`（`left`/`right`，默认 `right`）。
- `TreeRenderer.layout`：产物列位置动态化。右模式 = 产物列贴右侧、材料从 `px+PAD` 左侧排（镜像）；左模式 = 现有行为（产物在左）。`dividerX`/`rowX`/`goalX` 按侧切换。hitTest 与渲染共用同一 `layout`，天然一致。
- 无遮挡修复（用户已明确不做）。

### 10.2 背景透明可调（Q2，需求 7）
- `Config` 新增 `treeBackgroundTransparent`（bool，默认 true）。
- `TreeRenderer.render` 背景绘制按开关：透明时不画 `g.fill(0xCC0B0B0B)`（透出下层）；不透明时恢复现有深色。

### 10.3 顶部总材料行（Q3，需求 8）
- `InternalHelperImpl.buildTree`：额外聚合一棵树的**叶节点总材料**（递归到底、库存已足不再展开的那一层，按 `Agg.need` 汇总）。
- `TreeData` 增加总材料行数据（如 `leafTotals: List<EmiIngredient>`），渲染在最顶部一行；下方保留 `rows`/`directInputs` 分层不变。

### 10.4 Shift+树按钮 → BoMScreen + 产物缩略条（Q4，需求 9）
- `EmiApiMixin`：拦截 `viewRecipeTree` 时检测 Shift（`EmiScreenManager`/事件携带的修饰键）→
  - **Shift**：放行/手动 `new BoMScreen(containerScreen)`（当前容器屏），并在其左侧叠加产物缩略条。
  - **普通**：维持现有收藏页树视图分支。
- 缩略条：新增 `client/BoMTreeSidebar`，叠在原版 `BoMScreen` 左侧，条目来自 `TreeManager` 多目标（各树最终产物图标）。
- 点击条目 → `BoM.setGoal(<该产物最终配方>)` + `BoMScreen.recalculateTree()`（javap 实证存在），联动刷新；缩略条高亮当前选中。
- 展示交给原版 EMI（BoM 样式），不自绘原版树。

### 10.5 Ctrl+V 强制合成（Q5，需求 10）
- `AutoCraftClient.onKeyInput`：放开 Ctrl 判定（现有只认 plain/shift）；`Ctrl+V` → `attemptAutoCraft(force=true)`。
- `ClientCraftChain` 加 `force` 标记；`pickNext`/`placeStep`：
  - 目标能合就合目标；
  - 否则**枚举缺料输入对应的中间产物**，挑**任一能 `canCraft`** 的合成，缺料的跳过；
  - 直到无任何可推进步骤 → 静默停（不弹"材料不足"红字）。
- 不新增 Ctrl+Shift+V 连续档。

### 10.6 NBT 同种物品两槽（Q6，**已确认存在、本次不修**）
- 根因：`ClientCraftChain.findSource` 与 `CraftInventory.count` 用 `isSameItemSameComponents`（含 NBT）匹配；配方 `Ingredient` 实际按 item 类型匹配。需 2 个同种物品分放两槽且 NBT 不同时，第二个找不到源 →"只放一个就停"。
- 本次不修，仅记录；后续若修：`findSource` 改 `isSameItem`，并评估 `count`/`canCraft` 预检是否同步统一口径。

### 10.7 崩溃修复：handler 槽列表含 null
- **现象**：在玩家背包界面按 V → NPE（`Cannot invoke "Slot.hasItem()" because "s" is null`）。
- **根因（javap 实证）**：`InventoryRecipeHandler.getCraftingSlots(InventoryMenu)` 为把 2×2 合成格补齐 3×3，返回列表**显式含 5 个 `null`**。
- **修复**：遍历槽列表时跳过 null（`findEmptySource` 等）。

### 10.8 放料复用 EMI clientFill + Destination.NONE（v2.1.0 最终方案）
- **弃用原因**：手写逐格放料用"右键 `PICKUP` 取1"在**光标有物品时语义变为"取一半/累积"**→指针堆 16/32 个、一格多木板、连续合成中断。
- **最终方案**：放料直接调用 **`EmiRecipeFiller.clientFill(handler, recipe, screen, getStacks(...), Destination.NONE)`**，一次性把全部材料放进合成格（EMI 内部清格→整堆拾起→精确放置→余料归还，光标全程受控）；`Destination.NONE` = 只放料不移走，材料留在合成格 → 服务端合成 → 结果槽有货 → `onShowEnd` `QUICK_MOVE` 取走 → `clearCursorIfHeld` 清光标 → 下一步。
  - `Destination.INVENTORY` 不可用：它放料后把合成格材料 QUICK_MOVE 回背包 → 结果槽永远空 → 超时弹"无法在此界面合成配方"。
  - `Destination.CURSOR` 不可用：余料留光标 → "合成结束物品卡鼠标"。
- **合成格 fit 检查**：弃用 EMI `EmiCraftingRecipe.canFit(w,h)`（对 3×3 配方在 2×2 返回 true 不可靠），改用**配方非空输入 bounding box** 判定（`fitsGrid`）：3×3 shaped 算包围盒宽高 ≤ 网格宽高；shapeless 按非空数 ≤ 格数。背包 2×2 → 3×3 配方被拦截提示"需打开工作台"。
- **代码清理**：删除手写放料全部死代码（`buildPlacementClicks`/`onClickTick`/`CLICK` phase/`clickQueue`/`findSource`/`matches`），ClientCraftChain 从 563 行精简到 ~280 行。

## 11. Phase 4 三项（需求 12-14）

### 11.1 树节点 tooltip 明细（需求 12）
- `TreeData.TreeItem` 增加 `EmiRecipe producer`（产出该材料的配方；叶节点/无配方为 null）——构建时传 `Agg.recipe`（`InternalHelperImpl.buildTree` 的 goal/directInputs/rows；leafTotal 传 null）。
- `TreeRenderer.appendDetailLines`：在原版 `getTooltipText()` 后追加 `需要: X`、`拥有: Y（不足红字，材料节点）`、`由 <配方输出名> 合成 / 库存直接获取`、`可合成（绿）/ 材料不足（红）`。`Placed` 增加 producer 透传。
- 数量格式复用 `formatItemAmount` / `formatFluidAmount`（k/M/G/T 与 mB/L）。

### 11.2 单次合成数量可配置（需求 13，**每树独立**）
- 状态：**每树独立**（`TreeManager` 每条目 `amounts`，默认 1，1-999，落盘 `amount` 字段兼容旧档，重启回 1）。
- 调节：**滚轮**（`EmiScreenManagerMixin` 悬停最终产物，`adjustAmount(treeIndex, dir, mods)` **只改悬停树**）+ **键盘 +/-**（`hoveredTreeIndex` 定位悬停树）；Shift ±10、Ctrl 翻倍/减半；提示"N"。
- **V 定量**：`ClientCraftChain.start(..., targetAmount)` 用**悬停树的数量**；V 模式 `remainingTarget` 按实际增量（`lastOutputGain`）扣减。
- **树按 N 倍展示**：`buildTrees` 每树用 `TreeManager.getAmount(i)` → `buildTree(goal, recipeId, treeAmount)`（目标 need=N、材料 N 倍、标红按 N 判）。
- **批量调度**：`pickNext` 批量前瞻 `lookaheadBatches()`（V=剩余份数换算上限 16 / Shift+V·Ctrl+V=16 循环）+ `goalReadyFor(batches)`；`lastProducer` 复用（中间步同一配方连续合成不切换）；force 保持单批；步数上限 V 定量按 N 放宽。
- **批量放料（普通界面 + AE2 通用）**：`batchAmount()`（目标=lookahead 剩余批；中间=需求÷单批输出；上限 16）→ `EmiRecipeFiller.getStacks(recipe, batch)` + `clientFill` 放 **B 份堆叠进合成格**（合成前检测：库存不足降级单批）→ 取产物 shift 快速合成（普通）或 CRAFT_SHIFT 消耗合成格堆叠（AE2，不依赖网络补料）。
- **AE2 批量策略（CRAFT_SHIFT 按需，避免超量）**：CRAFT_SHIFT 数量固定一组 64 → 仅需求 ≥ 一组时批量、余数 CRAFT_ITEM 单批精确；中间步需求视野=目标剩余全部批次（V 定量，连续多组零超量）；背包材料也批量（clientFill 堆叠放料优先，网络放料 `handler.craft` 回退）。
- **AE2 光标放回同步冷却**：`placeCursorIntoInventory` 发出 PICKUP 后 2s 冷却（`placeSyncUntil`），窗口内光标非空视为"放回同步中"不重复发——防"服务端光标已空 → 再 PICKUP 把背包产物拾回光标"死循环。

### 11.5 Productive Bees 蜜蜂配方显示（可选联动，零硬依赖）
- **背景（javap 实证 13.13.3）**：蜜蜂是"实体 + 数据驱动品种"；EMI 用自定义 `BeeEmiStack`（**没有 override getItemStack() → EMPTY**；`getId()` = 品种）→ 树的 ItemStack 体系无法直接持有蜜蜂；繁殖/生成配方**非工作台**（繁殖箱）→ 不自动合成（仅显示）。
- **持久化**：`ProductiveBeesSupport.toBeeCageStack` 蜜蜂 EmiStack → **蜂笼物品标记**（`BeeCage` + `CUSTOM_DATA.type=<品种>`，**不伪造 entity 官方字段**——仅作树内部品种 id 载体，不对外表现为合法蜂笼）；`beeTypeOfStack` 读 type。
- **树显示**：目标/材料用**配方输出/输入的原始 BeeEmiStack**（`buildTree` 对蜜蜂目标取 `resolveGoalRecipe` 配方输出——**与原版 EMI 渲染同路径**，不自造 BeeEmiStack）；`key()` 蜜蜂 EmiStack 与蜂笼物品统一 `bee:<品种>`；`CraftInventory.count` 蜜蜂↔蜂笼等价计数（拥有量/标红准确）；`hasEnough` 蜜蜂按蜂笼计数（不走"空物品视为足够"）。
- **tooltip 名字**：`getTooltipText()` 在品种数据缺失时跳过名字行 → 第一行若是 id 形式（含 `:`）→ 补 `getName()`（translatable，官方翻译机制，非硬编码）。
- **门禁**：`ModList.isLoaded("productivebees")` 短路；`compileOnly libs/productivebees-1.21.1-13.13.3.jar`（运行时 run/mods）。
- **自动合成**：蜜蜂配方的目标/中间保持"仅工作台配方"拦截（繁殖箱非工作台，提示"该配方无法在工作台内进行"）。

### 11.3 树面板拖动调宽（需求 14）~~（已取消，2026-08-16 用户要求移除）~~
- 功能已从代码中移除（`TreeMode`/`EMIBettersynthesischainClient` 无宽度拖动）；面板宽度仍由 `treeSidebarWidth` 配置 + EMI 设置页调整。

### 11.4 已知限制与防御性启发式（兼容性原则审查后记录，2026-08-16）
以下为有依据但属**假设/启发式**的实现，若相关机制变化需回归（不视为硬编码违规，但需知晓）：
- **`isReverse`（分解类启发式）**：输出数量 > 输入数量 且 可逆 → 判"分解配方"并跳过（目标选择回退用）。原版无"分解"标记，纯数量启发式；误判场景罕见且祖先栈防环兜底。
- **防御性上限**：worklist `guard 3000`、链条 `MAX_STEPS 128`（V 定量 +min(N,4096)）、`MAX_BATCH_LOOKAHEAD 16`、`canObtain` 深度 8（防递归指数爆炸；更深链保守标红）。
- **`TreeAddFlag` 500ms 窗口**：EMI `setGoal→viewRecipeTree` 事件间隔实测假设（慢机放宽）。
- **`BoMScreenMixin` 缩略条尺寸**：对原版 `BoMScreen` 左侧布局的自绘叠加层（mod 自身 UI，非第三方数据）。
- **`resultRetryTicks`（AE2 12 / 普通 2 tick）**：服务端同步时序实测值。
- **`hasEnough`"流体/空视为足够"**：原版流体非 ItemStack 不可计数，保守简化（不据此判不足）。
