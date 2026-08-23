【EMI Better Synthesis Chain - 1.20.1 测试说明】

本目录 = 你需要的全部 3 个 mod jar（拷贝进启动器的 mods 文件夹即可）：

  1. emibettersynthesischain-2.2.1.jar   ← 本 mod（EMI Better Synthesis Chain）
  2. emi-1.1.24+1.20.1+forge.1.jar       ← EMI 1.20.1（Forge 官方版）
  3. mixinextras-forge-0.3.6.jar         ← MixinExtras（本 mod 设置页注入需要，EMI 不带）

一、准备一个 1.20.1 + Forge 实例
- 用你常用的启动器（HMCL / PCL2 / Prism Launcher / 官方启动器）新建 1.20.1 实例
- 安装 Forge（47.x，如 47.4.x 均可）——启动器一般有"一键安装 Forge"
- 打开该实例的 mods 文件夹，把上面 3 个 jar 全部放进去
- 其它 mod 先别加（尤其 AE2/精妙背包/蜜蜂等 1.21.1 版千万别放——版本不符会崩/拒载）

二、验收清单（对照 26.1.2 那套）
1. 启动游戏进世界，打开背包/工作台 → 按 E 打开 EMI → 左下角"配方树"按钮
   → 收藏页显示合成树：青绿线条、树左侧"["括号、总材料行 S、各层序号 1、2、…
2. 树内悬停最终产物按 V（一次）/ Shift+V（连续）/ Ctrl+V（强制）
3. 滚轮 / 键盘 ± 调单次合成数量（Shift ±10、Ctrl 翻倍/减半）
4. 合成成功/失败顶部提示文字（物品栏内可见）
5. EMI 设置页 → "EMI Better Synthesis Chain" 分组 → 线条配色/分割线配色两个下拉
6. 产物列左右切换、背景透明开关、数字大小等既有设置回归

三、常见问题
- 游戏不启动/崩溃：看实例的 crash-reports（最新 client 日志发我即可）
- 提示"请安装 mixinextras"：确认 3 个 jar 都在 mods 里
- 别把 1.21.1 时代的任何 mod jar 混进这个 1.20.1 实例