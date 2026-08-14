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
├─ client/AutoCraftClient        自动合成快捷键/悬停 → 发包
├─ client/MessageOverlay         合成成功提示（容器屏顶部）
└─ network/ server/（Phase3 自动合成）
   ├─ network/AutoCraftPayload (C2S) / AutoCraftResultPayload (S2C) / ModPayloads
   └─ server/AutoCraftHandler（不可见回退） + AutoCraftChain（可见逐步显示）
```

- 树显示为**客户端纯 UI**；自动合成为**客户端请求 + 服务端权威校验**（防作弊）。
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
- **纯图标 + 右下角数量**（`drawStack(RENDER_ICON|RENDER_AMOUNT)`），无文字/箭头；目标与材料间**无连线**；已解析标签用**短连线**连到右侧"所选成员"图标；标签材料加 `RENDER_INGREDIENT`（标签徽记）。
- **流体**：EMI 的 `RENDER_AMOUNT` 不处理流体 → 用量用 **0.5 倍小字画在图标格内底部**（`ROW_H=16`）；单位 `mB / L / K L / M L / G L`（<1000mB 用 mB，之后 1000 进制分段，两位小数去尾零）。
- **紧凑间距**：`PAD=3`、`LEFT_COL=24`、`ROW_H=16`、`ITEM_GAP=4`、`TREE_GAP=8`、`BP_GAP=6`。
- **悬停**：悬停树时**所有 `canCraft=false` 节点叠红**（不可合成），悬停节点可合成白框/不可合成红框；物品栏样式 tooltip（`getTooltipText()` + `GuiGraphics.renderTooltip`，scissor 外绘制）。
- **滚动**：`TreeMode` 惯性（velocity 衰减）+ 可拖动滚动条 + 钳制到 `[0,maxScroll]`（用未滚动内容高度算）+ 添加新树自动滚到底。

### 5.3 交互（EMIBettersynthesischainClient 的 InputEvent.MouseButton.Post）
- **左键点物品** → `EmiApi.displayRecipes(content)`；点标签 → `RecipeScreen.resolve = tag` + `displayRecipes(tag)`（可选择具体材料）。
- **左键拖滚动条滑块** → `TreeMode.startDrag/stopDrag`。
- **右键点最终产物列** → `TreeManager.remove(index)`。

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
| `autoCraftShowTicks` | int | 6 | 可见合成链每步显示时长（tick） |
| `autoCraftGapTicks` | int | 2 | 可见合成链步间间隔（tick） |
| `autoCraftFailureMessages` | bool | true | 无法合成时是否红字提示 |

`~~autoCraftWorkbenchRadius~~` 已移除：3×3 改判"打开工作台界面"（containerMenu=CraftingMenu）。

### 6.1 EMI 设置页注入（ConfigScreenMixin）
- **参考**：`emi-plus-plus-2` 的 `ConfigScreenMixin`（EMI 1.1.24 同版本实证）。
- **原理**：EMI 设置页 `ConfigScreen.init()` 反射 `EmiConfig` 静态字段渲染条目，附属无法直接注册配置。做法是 Mixin `ConfigScreen`：`@ModifyArg` 拦截 `init` 里 `addWidget(ListWidget)`，往 `ListWidget` 追加 `GroupNameWidget`/`SubGroupNameWidget`（分组头）+ `IntWidget`/`BooleanWidget`（值控件，`ConfigScreen.Mutator` 读写本 mod `Config`），并把 `root.children`/`widget.parentGroups`/`sub.children` 互相连好（可折叠、可搜索）。`@Inject addJumpButtons` TAIL 加跳转按钮（`ConfigJumpButton` → `jump("ebs")`）。
- **落盘**：`Mutator.setValue` 里 `Config.X.set(v)` 后调 `Config.save()`（`ModConfigSpec.save()` 写回 `config/emibettersynthesischain-common.toml`）。
- **重置按钮**：组尾 `ConfigResetButton`（`client/`，非 mixin 包；`ConfigEntryWidget` + `EmiPort.newButton`）→ `Config.resetAll()` 恢复全部默认并落盘。

## 7. 目录结构
```
src/main/java/com/cancan/emibettersynthesischain/
├─ EMIBettersynthesischain.java        @Mod（注册 payload）
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
│  ├─ AutoCraftClient.java             自动合成快捷键/悬停 → 发包（默认配方映射 + repeat）
│  └─ MessageOverlay.java              合成成功提示（容器屏顶部）
├─ network/
│  ├─ AutoCraftPayload.java            C2S（recipeId + 默认配方映射 + repeat）
│  ├─ AutoCraftResultPayload.java      S2C（成功提示文本）
│  └─ ModPayloads.java                 注册 payload
├─ server/
│  ├─ AutoCraftHandler.java            不可见回退合成（临时格）+ 底层 placeInGrid/takeResult
│  └─ AutoCraftChain.java              可见合成链（真实合成格逐步显示，tick 驱动）
└─ mixin/
   ├─ EmiApiMixin / EmiScreenManagerMixin / SidebarPanelMixin
   ├─ RecipeTreeButtonWidgetMixin / RecipeButtonWidgetAccessor / BoMFlagMixin
   ├─ AbstractContainerScreenMixin / AbstractContainerScreenAccessor
   └─ ConfigScreenMixin
```

## 8. 风险与约束
- **版本敏感**：Mixin 全指向 EMI 内部类，锁定 1.1.24；升级需回归所有注入点。
- **窄面板**：长树靠滚动 + 可调宽缓解。
- **跨版本**：NeoForge 1.21.1（范围放宽到 21.0.0+）；payload 用 NeoForge 标准 API。
- **组件感知**：物品按 `id#组件哈希` 去重，区分不同 NBT（药水/时长/附魔）。

## 9. Phase 3 — 自动合成（已实现）
### 9.1 协议
- `AutoCraftPayload`（C2S，`emibettersynthesischain:auto_craft`）：`recipeId` + `Map<ResourceLocation,ResourceLocation> preferredProducers`（"材料键→默认配方 id"，客户端 `BoM.getRecipe` 沿链收集，服务端优先用）+ `boolean repeat`（Shift+V=连续合成）。
- `AutoCraftResultPayload`（S2C）：`message` + `success`（`ByteBufCodecs.BOOL`）→ 客户端 `MessageOverlay.show(text, success)`（容器屏顶部，约 2.5s，成功白字 / 失败红字）。注册见 `ModPayloads`。

### 9.2 客户端
"自动合成"快捷键（`InputEvent.Key` 原始按键事件，仿 EMI `matchesKey`；`V`=一次、`Shift+V`=连续，`mods==GLFW_MOD_SHIFT`，排除 Ctrl+V 等）：取悬停物品——**树模式优先**（`TreeRenderer.hitTest` 命中树节点，标签取解析/可合成成员）→ 回退 EMI 当前鼠标坐标 `EmiScreenManager.getHoveredStack(mx,my,true,false)` → 再回退容器槽位遍历 → 找产出它的**工作台配方**（`BoM.getRecipe` 优先）→ **3×3 目标需 `CraftingScreen` 打开**（与服务端一致，未打开不发包）→ 发包（附默认配方映射 + repeat）。**无法合成时客户端直接红字提示**（`MessageOverlay.show(text,false)`）：未悬停物品 / 该物品无工作台配方 / 3×3 未打开工作台界面；材料不足等由服务端发失败提示。

### 9.3 服务端（AutoCraftHandler 权威校验）
1. `byKey(recipeId)`，必须 `instanceof CraftingRecipe`。
2. **2×2 判定**：Shapeless 输入≤4 / Shaped 宽高≤2 → 直接合；否则要求**打开工作台界面**（`player.containerMenu instanceof CraftingMenu`，打开工作台时服务端同步），无则拒绝（先查，避免白耗中间材料）。
3. **完整链条递归（循环确保材料）**：
   - 对目标配方每个输入 `Ingredient` 计算 `needed` = 匹配它的输入槽位数（重叠槽各需 1 个，跨槽共享计数）。
   - 外层循环（≤12 次）：`canAfford`（干跑）失败 → 对每个不足材料调 `ensureMaterialOnce`（缺 `required` 个时**依次尝试所有产出它的工作台配方**，跳过分解类（铁块→9铁锭），**客户端默认配方映射优先**，取**任一子材料递归可得**的配方合成一批——如红色染料可用虞美人而非甜菜根；含 tag 成员，递归深度≤6，中间 3×3 产物同样需**打开工作台界面**）；若某轮无任何进展则停止。
   - 每批只合一个，外层循环自然处理"一批不够、需多批"（如 6 玻璃板 → 合 2 批 3 块）。
   - 数量判断 `countMatching` = 背包中匹配材料的总数量（`ing.test` + 堆叠计数）；重叠判定 `ItemStack.isSameItemSameComponents`。
4. **材料校验**：配方 `Ingredient`（支持 tag）与背包干跑比对（每槽消耗 1，同堆可共享计数），不足拒绝。
5. **模拟玩家合成（底层 `placeInGrid`/`takeResult`，可见链也复用）**：把材料从背包摆进合成格（Shaped 按 pattern 左上角对齐 / Shapeless 顺序填入；材料 `split(1)` 拆堆；摆放失败归还背包）→ `recipe.assemble(CraftingInput)` 取结果 → `getRemainingItems` **余料/容器（如桶）放回背包** → 清格（材料随合成消耗）→ 结果入背包（满则掉落）。不可见回退用 `TransientCraftingContainer` 临时格一次完成；成功发 `AutoCraftResultPayload`。

### 9.4 可见合成链（AutoCraftChain，逐步显示）
- **入口**：`AutoCraftHandler.handle` 优先 `AutoCraftChain.start`（玩家打开工作台/背包有**可见合成格**时走可见链）；无可见合成格（如箱子）→ 回退 9.3 的不可见临时格合成。`repeat`（Shift+V）= 连续合成，目标完成后继续合到材料用完；默认 V 得最终结果即停。
- **找合成格**：`findGrid(menu)` 遍历 `menu.slots`，取 `Slot.container instanceof CraftingContainer`（工作台 3×3 / 背包 2×2），不访问私有字段。
- **逐级执行**：每 tick 推进状态机 `PLACE → SHOW（6 tick）→ TAKE → GAP（2 tick）→ 下一步`；`pickNext` 决定下一步——目标材料齐则合目标，否则先合**最深处**缺的中间材料（`findProducerToCraft` 递归，一批一合，深度≤6，跳过分解类/默认配方优先，2×2 格放不下 3×3 配方则跳过）。
- **摆入**：`placeInGrid` 材料进**真实合成格** → `broadcastChanges`（客户端即时显示格子 + 结果槽）。
- **取结果**：`takeResult` 校验 `recipe.matches(input, level)`（玩家中途动格子则中止不取）→ assemble 产物 + 余料/容器返还 + 清格 + 产物入背包；目标步发 `AutoCraftResultPayload`，**默认 V 至此停**（repeat=false），Shift+V 继续下一步。
- **中止条件**：`isValid` 每 tick 校验玩家仍开着同一合成界面（`findGrid == this.grid`），关界面/换菜单即中止（格子物品由原版归还，**不发提示**）；**目标材料需非工作台步骤（如熔炉铁锭）或材料不足时，`placeNext` 返回 null → 中止并红字提示"材料不足"**；步数上限 128（连续合成更久）；同一玩家已有进行中的链则忽略新请求。

### 9.5 反作弊
所有判定在服务端；客户端仅发送请求。
