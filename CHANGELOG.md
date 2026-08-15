# Changelog

## [2.1.0] - 2026-08-09

合成树增强 + 强制合成 + 原版树联动（新增/优化）。

### 功能
- **产物列左右可调**：`treeGoalSide` 设置，默认右侧（材料从左侧镜像排布）。
- **背景透明可调**：`treeBackgroundTransparent` 设置，默认透明（与收藏夹一致）。
- **顶部总材料行**：树顶部新增一行"底层原始材料汇总"，下方保留完整分层。
- **Shift+树按钮 → 原版 EM 合成树界面**：打开 BoMScreen + 左侧产物缩略条，点选产物联动展示对应合成树。
- **Ctrl+V 强制合成**：材料不足也尽力而为（缺料中间产物跳过），直到无步骤静默停。
- **V/Shift+V/Ctrl+V 只对树模式下右侧最终产物生效**；合成成功提示改用目标配方输出名。

### 修复
- 放料复用 **EMI `clientFill`**（一次性放置、光标受控），修复原逐格放料"右键取一半→光标堆 32 个/一格多木板"。
- 3×3 配方在背包 2×2 正确拦截（bounding box 判定），不再硬塞错位。
- 背包界面 handler `getCraftingSlots` 含 null → NPE 崩溃修复。
- 取结果后清空光标（不再"合成结束物品卡鼠标"）。

### 技术
- 纯客户端自动合成（服务端无需装本 mod）；删除了手写逐格放料死代码。

## [2.1.0-updates] - 2026-08-15

v2.1.0 之后的联调修复与增强（仍为 2.1.0）。

### 功能
- **AE2 合成终端取产物**：结果槽 `CraftingTermSlot` 无法用 vanilla 点击取走（`mayPickup` 恒 false）→ 走 AE2 action 机制（`InventoryActionPacket` CRAFT_ITEM，可选依赖 `libs/appliedenergistics2-19.2.17.jar`，未装 AE2 自动跳过）。
- **合成后自动清格**：AE2 取产物后逐格 QUICK_MOVE 清空合成格（防"网络补料重新填充"；并修复结果槽残留——`clearToPlayerInventory` 不触发 `slotsChanged`）。
- **精妙背包产物堆叠**：取产物放回背包优先堆叠到同种未满槽（不再散落空槽）。

### 修复
- **合成量大时打开工作台卡顿**：树构建 hasEnough 复用一次库存快照（`invSnapshot`）、产出配方候选缓存（`producerCache`）、渲染拥有量一次快照——消除"每节点/每格重建快照"热点。
- **数量数字被物品图标盖住**：物品图标渲染开启深度测试（z=32），自绘数字改为 z=200 平移（同 EMI `renderAmount` 做法），数字完整显示在图标格内右下角。
- **数字位置居中**：右下角坐标公式修正为 `(ICON-1)/sc - 尺寸`（原公式把尺寸误放分子，文字落到图标中间）。
- **AE2 误重试多合成**：AE2 菜单取产物验证等待 4→12 tick（每步发包多，防同步未到误重试）。
- **合成速度**：`autoCraftShowTicks` 移除隐藏 4 tick 下限（设置完全生效）、取产物验证等待 4→2 tick，最快约 0.2s/步。
- **右键删树防误触（三层）**：面板边界检查 + `lastScreen` 界面切换检测 + `ScreenEvent` 置位 `screenTransition`——修复"关闭容器界面时 EMI 侧边栏 bounds 残留、下次打开界面误删树"。
- **非工作台配方放料拦截**：中间产物仅接受工作台配方（`isWorkbenchRecipe`），非工作台默认配方（如熔炉）不再放料进合成格；提示"该配方无法在工作台内进行"（区别于"材料不足"）。

### 技术
- 删除 `ClientCraftChain`/`AutoCraftClient` 全部 EBS INFO 调试日志（保留 warn/debug）；`Ae2Support` 用 `ModList.isLoaded("ae2")` 门禁 + 方法体惰性引用 AE2 类。
- 代码审查清理：删除死 `AbstractContainerScreenAccessor`（mixin+json+import）与未用 `ebs$x/y`；`hasEnough` 去每调用 `ItemStack.copy`；背包变化检测降频（每 5 tick）；`TreeRenderer` 内容高度缓存；`TreeMode` 清理 `scrollToBottom` 残留；`Ae2Support` 收窄 `catch Throwable`→`Exception`。

## [2.0.0] - 2026-08-09

自动合成改造为**纯客户端**（服务端无需安装本 mod），并支持 AE2 等注册过 EMI handler 的模组界面。

- **模拟玩家合成**：客户端发 vanilla 点击包在当前界面合成格上一步步合成（中间产物可见），服务端走原版校验。
- 复用 **EMI handler**（`StandardRecipeHandler`）检测/合成：支持 AE2 合成终端（网络+背包）、多模组合成界面。
- 库存检测跟随当前屏幕（`CraftInventory`）；树标红跟随当前界面库存。
- 防环改用 EMI 祖先配方栈；删除全部服务端 + payload 代码。

## [1.0.0] - 2026-08-09

正式版发布。

### 功能
- **配方树按钮**：点左下角"配方树"按钮在收藏页内显示合成树（不再弹独立 BoM 界面）；添加配方不关闭当前页面。
- **合成树**：最终产物在左（竖分割线）+ 材料分行横排 + 副产物区；纯图标 + 右下角数量，悬停显示 tooltip。
- **多物品合成树**：标签点选具体成员、组件感知（不同 NBT 各自成树）、重启持久化、流体用量显示。
- **自动合成**：`V`=一次（得最终结果即停）/ `Shift+V`=连续（合到材料用完）；完整链条递归（缺什么先合什么，含标签成员）；模拟玩家合成（材料在合成格逐步显示、余料/容器如桶返还、默认配方优先、3×3 需打开工作台界面、失败红字提示）。
- **EMI 设置页**：树行高/间距/标红、自动合成速度/失败提示 9 项可调 + 一键重置。

### 技术
- NeoForge 1.21.1 / EMI 1.1.24（EMI 为本地 jar，见 `libs/`）。
- Mixin 注入 EMI 内部类（锁定 EMI 1.1.24）；自动合成**服务端权威校验**（防作弊）。
- License: **MIT**。
