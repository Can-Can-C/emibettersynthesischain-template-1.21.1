# EMI Better Synthesis Chain

NeoForge 1.21.1 / EMI 1.1.24 的 **EMI 附属 mod**：重写 EMI 的配方树（合成树）展示，并新增"悬停即自动合成"。

## 功能

1. **配方树按钮**：点左下角"配方树"按钮**不再弹独立 BoM 界面**，改为切换侧边栏到"收藏页"显示合成树；添加配方时不关闭当前页面。
2. **合成树布局**：最终产物列可置**左或右**（设置 `treeGoalSide`，默认右），材料区按行横排（最深→最浅，最后一行=目标直接输入），副产物在底部；**纯图标 + 右下角数量（大数字缩写 k/M/G/T）**，顶部总材料行（种类过多自动换行铺满），材料不足时**左上角红字显示拥有量**；悬停显示物品栏样式 tooltip + **合成明细**（需要/拥有/由 X 合成/可合成、不足红字）。
3. **多物品合成树**：可同时添加多棵；**标签材料**点选具体成员后拆解；**分解类配方**（铁块→铁锭等）自动跳过避免循环；**组件感知**（同 id 不同 NBT）各自单独成树；**重启持久化**。
4. **流体显示**：流体合成（如黑曜石 = 岩浆 + 水）正常显示，消耗量以 mB / L / K L / M L / G L 标注在图标格内。
5. **自动合成（完整链条，纯客户端）**：悬停最终产物 + 快捷键 → 缺什么先用工作台合成（含标签成员），再合成目标；**数量感知**（同种材料聚合总量）；**批量策略**：AE2 终端 CRAFT_SHIFT 按需批量（需求 ≥ 一组才批量、余数单批精确不超量、中间材料连续多组、背包材料也批量）、普通界面放 B 份材料堆叠 + shift 快速合成；**每树独立合成数量**（V 按悬停树数量）；支持精妙背包等模组界面；**AE2 终端取产物**（action 机制，合成后清格）。
6. **Productive Bees（可选）**：蜜蜂繁殖/生成配方可**添加到树显示**（蜜蜂本体渲染、繁殖输入链、拥有量按蜂笼计数）；繁殖箱非工作台不自动合成。AE2 / Productive Bees 未装时对应功能自动跳过。

## 操作

| 操作 | 说明 |
|------|------|
| 树模式悬停**最终产物** + **V** | 自动合成一次（按该树"单次合成数量"合 N 个即停） |
| 树模式悬停**最终产物** + **Shift+V** | 连续合成（一直合到材料用完） |
| 树模式悬停**最终产物** + **Ctrl+V** | 强制合成（缺料中间产物跳过、尽力而为、无步骤静默停） |
| 树模式悬停**最终产物** + 滚轮 / 键盘 **+/-** | 调节**该树**单次合成数量（Shift ±10、Ctrl 翻倍/减半） |
| **Shift+左下角/配方页"树"按钮** | 打开原版 EMI 合成树界面（BoMScreen）+ 左侧产物缩略条联动 |
| 左下角/配方页"树"按钮（普通点） | 打开合成树视图（收藏页内）；配方页按钮点击切换加入/删除 |
| 右键点树最终产物 | 删除该树 |
| 左键点树节点 | 查询该物品配方（标签进入选材料界面） |
| 滚轮 / 拖动滚动条 | 树滚动（仅树区响应） |

**说明**：
- **V/Shift+V/Ctrl+V 只在树模式下、悬停最终产物时生效**；非树模式或悬停中间材料/收藏夹其它物品按 V 无反应。
- 仅**工作台配方**（`CraftingRecipe`）自动合成；烧炼/锻造/模组机器一律不合成（非工作台默认配方提示"该配方无法在工作台内进行"）；3×3 配方需打开工作台或 AE2 合成终端。
- **纯客户端**：放料复用 EMI `clientFill`（批量堆叠），**服务端无需安装本 mod**；支持注册过 EMI handler 的模组界面（精妙背包合成插件等）。
- 树标红跟随**当前界面库存**（AE2 终端里看树 = 网络 + 背包）。

## 配置

可在 **EMI 设置页**（打开 EMI 设置 → "EMI Better Synthesis Chain" 分组）里修改，改动即写入 `config/emibettersynthesischain-common.toml`：

| 键 | 默认 | 说明 |
|----|------|------|
| `treeSidebarWidth` | 10 | 树模式左侧栏物品列数（宽） |
| `treeRowSpacing` | 16 | 合成树行高（px） |
| `treeItemGap` | 4 | 同行物品间距（px） |
| `treeTreeGap` | 8 | 树间垂直间距（px） |
| `treeByproductGap` | 6 | 副产物区上方间距（px） |
| `treeRedMarking` | true | 悬停树时材料不足节点是否标红 |
| `treeGoalSide` | right | 产物列在左还是右（默认右） |
| `treeBackgroundTransparent` | true | 合成树背景是否透明（默认透明；关闭恢复深色） |
| `treeNumberScale` | 0.5 | 图标上数字（数量/拥有量/流体用量）缩放倍数 |
| `autoCraftShowTicks` | 6 | 自动合成每步显示时长（tick） |
| `autoCraftGapTicks` | 2 | 自动合成步间间隔（tick） |
| `autoCraftFailureMessages` | true | 无法合成时是否红字提示 |

快捷键可在游戏"按键设置"里改绑（默认 `V`；`Shift+V` 连续；`Ctrl+V` 强制）。

## 构建 / 运行

- 编译：`./gradlew build`（必须 BUILD SUCCESSFUL）
- 试玩：`./gradlew runClient`
- 依赖：EMI 为**本地 jar**（`libs/emi-1.1.24+1.21.1+neoforge.jar`）；AE2、Productive Bees 为**可选编译依赖**（`libs/appliedenergistics2-19.2.17.jar`、`libs/productivebees-1.21.1-13.13.3.jar` 均 compileOnly，运行时 mod 放 `run/mods`，未装对应 mod 功能自动跳过）；NeoForge 1.21.1（范围 `[21.0.0,)`）。

## 文档

- `docs/01-REQUIREMENTS.md` 开发需求（含变更记录）
- `docs/02-TECHNICAL_DESIGN.md` 技术设计
- `docs/03-DEV_STEPS.md` 分阶段执行步骤
- `docs/04-STANDARDS.md` 代码/文档/日志规范
- `devlog/` 每日开发日志
- `CLAUDE.md` 项目指南（AI 协作速查）

## License

[MIT](LICENSE)（Copyright (c) 2026 Can-Can-C）。EMI 为 MIT 许可（本地 jar 见 `libs/`，随仓库分发以便直接构建）。
