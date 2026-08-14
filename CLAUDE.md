# 项目指南 — EMI Better Synthesis Chain

NeoForge 1.21.1 / EMI 1.1.24 的 EMI 附属 mod：重写配方树展示 + 自动合成。
在开始任何工作前，**先读本文件 + 当日开发日志 + 执行步骤文档**。

## 标准文件（必读路径）

| 文件 | 内容 |
|------|------|
| `docs/01-REQUIREMENTS.md` | 开发需求（与用户确认的功能清单、验收标准、变更记录） |
| `docs/02-TECHNICAL_DESIGN.md` | 技术设计（架构、EMI 内部类清单、Mixin/payload 方案、布局规范） |
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
5. **依赖（重要）**：EMI 是**本地 jar**（`libs/emi-1.1.24+1.21.1+neoforge.jar`），非 maven；`build.gradle` 用 `compileOnly` + `localRuntime files(...)`。**不要**改回 `dev.emi:emi-neoforge` maven 坐标（官方仓库 `repo.sleeping.town` 本网络不可达）。
6. **Mixin 约束**：注入 EMI **内部类**（非 api 包优先），锁定 EMI 1.1.24；升级 EMI 版本必须回归测试所有注入点。
7. **安全**：涉及物品/合成的逻辑，**服务端必须权威校验**（防作弊）；客户端只发请求。
8. **小步推进**：严格按 `docs/03` 分阶段，每阶段独立验证后再进入下一阶段；不一口气做太多。

## 技术要点速查
- 树按钮：`EmiScreenManager.tree` → `EmiApi.viewRecipeTree()`（`mixin/EmiApiMixin` 拦截点）。
- **EMI 内部访问隔离**：业务代码只依赖 `client/IEmiInternal`（接口）+ `TreeData`（纯业务模型）；EMI 内部类引用只在 `client/InternalHelperImpl.java` 与 `mixin/` 中；访问 EMI 私有成员用 `@Accessor` 接口 mixin（"目标实例 cast 到接口"）。升级 EMI 只改这些地方。
- **树数据**：`InternalHelperImpl.buildTrees`（`BoM.getRecipe` 选配方 + worklist 成本聚合，跳过分解类）；`TreeData` = goal/directInputs/rows/byproducts。`BoM.tree` 仅用于 `getAddTarget` 取目标。
- **收藏页**：`SidebarType.FAVORITES`；`EmiScreenManager.focusSidebarType(...)`；面板渲染 `SidebarPanel.render`。
- **滚轮/悬停**：`EmiScreenManagerMixin` 拦截 `mouseScrolled`（仅树区消费）与 `getHoveredStack`（树区返回 EMPTY，防点穿/重叠）。
- **自动合成**：`V`=一次（得最终结果即停）/ `Shift+V`=连续合成。客户端 `AutoCraftClient` 悬停发包（`AutoCraftPayload`：recipeId + 默认配方映射 + repeat）；服务端 `AutoCraftHandler`（不可见回退）+ `AutoCraftChain`（可见链，tick 驱动逐步显示在真实合成格）权威校验——仅 `CraftingRecipe`，3×3 需**打开工作台界面**（`containerMenu instanceof CraftingMenu`），模拟玩家合成（摆格→assemble→余料/容器返还）；成功提示 `AutoCraftResultPayload` + `MessageOverlay`。
