# docs/06 — IDEA 开发与调试（Hot Swap + Mixin 调试）

> 对应需求：common+platform 模块化 + IDEA 热交换/Mixin 调试（2026-08-23）。
> 适用于 main（neoforge 平台）/ 26.1.2（neoforge 平台）/ 1.20.1（forge 平台）。示例以 main 为模板，
> 分支差异：平台模块目录分别名为 `neoforge/`（main、26.1.2）与 `forge/`（1.20.1）；任务名 `:neoforge:runClient` / `:forge:runClient`。

## 0. 模块结构（EMI 式 common + platform）
```
common/    — 业务/渲染/Mixin/配置值（无加载器入口；依赖 MC+EMI+可选联动，编译期）
neoforge/  — 平台模块：@Mod 入口（EMIBettersynthesischainMod / EMIBettersynthesischainClient）、
             全部资源（assets / mixins.json / 模板 mods.toml）、run 配置；生产 jar 合并 common 输出
forge/     — （1.20.1 分支的平台模块）
```
- 常量（MODID/LOGGER）在 common 的 `EMIBettersynthesischain`；入口类只剩加载器胶水。
- 生产 jar = `neoforge/build/libs/emibettersynthesischain-<ver>.jar`（已合并 common 类）。

## 1. 导入与运行
- IDEA → File → Open → 选**仓库根目录**（Gradle 项目）；让 Gradle 导入（Trust Project）。
- Gradle JVM：main / 26.1.2 = **JDK 21**；1.20.1 = **JDK 17**（IDEA Settings → Build Tools → Gradle → Gradle JVM）。
- 运行客户端：Gradle 面板 → `:neoforge:runClient`（main/26.1.2）或 `:forge:runClient`（1.20.1）。
  - **Debug**：在 Gradle 面板右键 runClient → Debug（断点可用：渲染线程、事件、mixin 目标方法）。
- 改 Gradle 文件后：刷新 Gradle 项目（Gradle 面板 → Reload）。
- 两个模块红 ×：Project Structure → Modules → 确保 `:common` 在，neoforge 模块依赖 `:common`（Gradle 会自动配好，重载即可）。

## 2. 热交换（Hot Swap）
- **已内置**（`neoforge/build.gradle` runs configureEach，无需改代码）：
  - `-XX:+AllowRedefinitionToAddMethod`（Java 21 允许"加方法"型重定义）
  - 仓库根存在 `hotswap-agent.jar` 时自动挂载 `-javaagent:`。
- **启用步骤（一次性）**：
  1. 从 HotSwapAgent GitHub Releases 下载 `hotswap-agent-<version>.jar`（fat jar，JDK 21 用 ≥ 1.4.x 或最新）。
  2. 复制到仓库根并改名 `hotswap-agent.jar`（已在 .gitignore）。
  3. 用 **Debug** 方式运行 runClient，启动日志出现 `HOTSWAP AGENT: ...` 即生效。
- **用法**：Debug 会话中改普通 Java 类 → `Ctrl+F9`（Build → Recompile / Hot Reload）→ 方法体/新加方法即时生效。
- **限制（务必知悉）**：
  | 改动 | 是否热生效 |
  |---|---|
  | 方法体/新增方法（业务类） | ✅ Ctrl+F9 即可 |
  | 新增类 | ✅ 大多可用 |
  | 修改字段布局 / 泛型签名 | ❌ 需重启（部分场景 agent 支持但不确定） |
  | **Mixin 类** | ❌ 需重启（mixin 在类加载期变换，运行期不可再注入） |
  | 资源（lang/json/纹理） | 游戏内 F3+T 重载资源包 |
- 不想用 agent：普通 JVM 热交换也能改**方法体**（Debug 会话 Ctrl+F9），调试"加一行日志/改一个常量"够用。

## 3. Mixin 调试开关（已默认开启）
已在 runs 配置：
| 开关 | 作用 |
|---|---|
| `-Dmixin.debug.verbose=true` | 启动日志逐条打印 mixin 应用与注入目标 |
| `-Dmixin.debug.countInjections=true` | 注入计数——检查是否有 **0 命中**（漏注入） |
| `-Dmixin.debug.export=true` | 转换后 class 导出到 `run/.mixin.out/`（检查注入结果） |
| `-Dmixin.dumpTargetOnFailure=true` | 注入失败时导出目标类，崩溃报告带完整字节码上下文 |

关闭方法：删掉 `neoforge/build.gradle` runs.configureEach 里对应 `systemProperty` 行。
说明：
- dev（官方名命名空间）无需 refmap；1.20.1 **生产打包**才需要手写 refmap（见 docs/03 11.2b）。
- 注入"找不到目标"先看 verbose 日志 `[mixin/]` 段；26.1.2 的非官方 EMI jar 类名与 1.21.1 主线一致，可直接对照编译期输出。

## 4. 常见问题
- `runClient` 提示资源缺失 / mixins.json 读不到 → 资源必须在**平台模块**（neoforge/forge）下（common 不携带资源，生产和 dev 一致）。
- 改了 common 代码不生效 → 热交换只对已加载类生效；新增/结构性改动重启 runClient。
- IDEA Debug 下 `run/.mixin.out/` 很大 → 属正常，已在 .gitignore。
- 分支切换后 Gradle 任务名变化（:forge: 等）→ 重新 Reload 项目。

## 5. 多版本启动速查（三分支）

| 版本 | 分支 | 必选 JDK | 启动方式 |
|---|---|---|---|
| 1.21.1 | main | 21（toolchain 自动；Gradle 9.2.1 可用 17-25 跑） | `.\gradlew.bat :neoforge:runClient` |
| 1.20.1 | 1.20.1-forge | **17（必须）**：Gradle 8.7 上限 JVM 21；FG6 不兼容 Gradle 9 | 先 `$env:JAVA_HOME="C:\Program Files\Java\jdk-17"` 再 `C:\gradle-tools\gradle-8.7\bin\gradle.bat :forge:build`。**dev runClient 不可用**（EMI 官方 jar 为 SRG 风味，Forge dev 官方名运行时不兼容）→ 打包装真机 Forge 实例测试 |
| 26.1.2 | 26.1.2-neoforge | 25（toolchain） | `.\gradlew.bat :neoforge:runClient`（JAVA_HOME 保持默认 25） |

- 报 `Unsupported class file major version 69` = Gradle 守护进程跑在过新的 JDK：按上表设该分支的 JAVA_HOME（1.20.1 必须是 17）重跑；IDEA 里同样把 **Gradle JVM** 设成对应 JDK（File → Settings → Build Tools → Gradle）后 Reload。
- 切版本：`git checkout <分支>`（工作区随分支切换）；IDEA 里切分支后 **Reload All Gradle Projects**。
- 1.20.1 构建（打包）：`$env:JAVA_HOME=...jdk-17; C:\gradle-tools\gradle-8.7\bin\gradle.bat build` → 产物 `forge/build/libs/emibettersynthesischain-2.2.1.jar`（已合并 common）。