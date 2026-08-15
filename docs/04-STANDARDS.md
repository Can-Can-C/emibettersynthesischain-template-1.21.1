  # 项目规范（Standards）

> 本项目开发需遵守的约定。更新需同步到 `devlog` 变更记录。

## 1. 代码规范
- **语言/工具链**：Java 21；NeoForge 1.21.1（moddev gradle 2.0.143）；Mixin 注入。
- **包结构**：`com.cancan.emibettersynthesischain`，按职责分子包 `client/ network/ server/ mixin/`。
- **命名**：类名语义化；常量 `UPPER_SNAKE`；快捷键、配置名小写驼峰。
- **Mixin 规则**：
  - 只注入 EMI **内部类**（`dev.emi.emi.*`，非 `api` 包优先）；必须注明目标 EMI 版本（1.1.24）。
  - `@Inject` 优先于 `@Redirect`；能 `cancel` 就 `cancel`，少改方法体。
  - 所有注入点注释标明"EMI 1.1.24"与用途，便于版本升级复查。
- **纯客户端（v2.0.0 起）**：自动合成为纯客户端（vanilla 点击包，服务端无需装本 mod、无自定义包）；凡涉及物品/合成的**可作弊风险由 vanilla 点击校验兜底**（客户端只发点击包，不直操作服务端数据）。不再引入自定义 payload/服务端合成逻辑。
- **不引入不必要依赖**：除 EMI（本地 jar）+ NeoForge 自带，不加第三方库。**例外**：AE2 为**可选编译依赖**（`libs/appliedenergistics2-19.2.17.jar`，仅 `compileOnly`；运行时 mod 由 `run/mods` 加载，未装 AE2 时功能经 `ModList.isLoaded` 门禁短路——新增可选 mod 联动须保持此模式）。
- **注释**：默认不写注释；仅当 WHY 不明显时写一行。

## 2. 文档规范
- **需求**：新功能先在 `docs/01-REQUIREMENTS.md` 增加条目（含验收标准），再实现。
- **设计**：实现前更新 `docs/02-TECHNICAL_DESIGN.md` 对应章节。
- **执行步骤**：每完成一步，勾选 `docs/03-DEV_STEPS.md`。
- **规范**：违反时更新本文档。
- **变更记录**：`docs/01` 文末变更表、`devlog` 均须记录日期与内容。

## 3. 开发日志规范（devlog/）
- 文件：`devlog/YYYY-MM-DD.md`；一天一份，追加写入。
- 结构：
  - `## 完成`（勾选事项 + 一句结果）
  - `## 待办`（下一步 / 遗留问题）
  - `## 问题与决策`（关键取舍，含原因）
- 规则：
  - 会话**开始**：读当日日志 + `docs/03`，确认当前阶段。
  - 会话**结束**：更新当日日志（完成 / 待办 / 决策）。
  - 构建/验证结果（成功/失败摘要）也记录。

## 4. 构建与验证
- 编译验证：`./gradlew build`（必须 SUCCESS 才算完成一步）。
- 界面/功能验证：`./gradlew runClient`，手动跑通该阶段验证点。
- 依赖：EMI 为本地 jar（`libs/emi-1.1.24+1.21.1+neoforge.jar`）；AE2 为可选 compileOnly 本地 jar（`libs/appliedenergistics2-19.2.17.jar`）；**不要**改回 maven 坐标。
- 网络：本环境无法访问 `repo.sleeping.town` / GitHub，需外网依赖时用可达镜像（Modrinth 可达）或本地文件。

## 5. 提交规范（若启用 git）
- 提交信息：`<阶段>/<功能>: <一句话>`，如 `phase1: intercept tree button to favorites page`。
- 每阶段完成验证后提交一次；不在中途堆提交。
- 不提交：`build/`、`run/`、`.gradle/`（确认在 `.gitignore`）。

## 6. 沟通约定
- 需求变更先确认再动手（沿用"先沟通、后执行"）。
- 遇到阻塞（编译错、API 变动）先查 `docs/` 与 devlog，再动手。
