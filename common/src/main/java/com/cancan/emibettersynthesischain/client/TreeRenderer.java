package com.cancan.emibettersynthesischain.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.cancan.emibettersynthesischain.Config;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.FluidEmiStack;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 在侧边栏面板内绘制合成树（行布局）。
 *
 * <p>布局（用户示例）：最终产物在左（竖分割线左侧），材料区在右从上到下分行横排
 * （最深→最浅，最后一行 = 最终配方直接输入）；副产物区在树底部。纯图标 + 右下角数量，无文字。
 * 无连线；滚动带惯性 + 可拖动滚动条；悬停显示物品栏样式 tooltip。</p>
 */
public final class TreeRenderer {
    private static final int ICON = 16;
    private static final int PAD = 3;
    private static final int LEFT_COL = 24;
    /** 材料行左侧 S(sum)/序号 通道宽度（px）：材料图标统一右移，S/序号画在通道右缘。 */
    private static final int GUTTER = 9;
    /** S/序号 文字缩放。 */
    private static final float TAG_SCALE = 0.5f;
    /** 树侧括号：竖线在树顶/树底外突出量（px）。 */
    private static final int BRACKET_OVER = 2;
    /** 树侧括号：顶部/底部横帽长度（px）。 */
    private static final int BRACKET_CAP = 3;
    private static final int SB_W = 4;
    private static final int SB_MARGIN = 2;
    private static final int SB_LANE = SB_W + SB_MARGIN + 2;

    /** 线条亮色（连接线 / 树括号 / 序号 / S），配色预设可配置（Config.TREE_LINE_COLOR）。 */
    private static int lineColor() {
        return TreeColorPreset.fromId(Config.TREE_LINE_COLOR.get()).base();
    }

    /** 线条深色（分割线 / 副产物横线），配色预设可配置（Config.TREE_DIVIDER_COLOR，取该预设深色阶）。 */
    private static int dividerColor() {
        return TreeColorPreset.fromId(Config.TREE_DIVIDER_COLOR.get()).deep();
    }

    /** 间距从 Config 读取（EMI 设置页可调），带下限保护。 */
    private static int rowH() {
        return Math.max(8, Config.TREE_ROW_SPACING.get());
    }

    private static int itemGap() {
        return Math.max(0, Config.TREE_ITEM_GAP.get());
    }

    private static int treeGap() {
        return Math.max(2, Config.TREE_TREE_GAP.get());
    }

    private static int bpGap() {
        return Math.max(2, Config.TREE_BYPRODUCT_GAP.get());
    }

    /** 图标上数字（数量/拥有量/流体用量）的缩放倍数（EMI 设置页可调）。 */
    private static float numberScale() {
        return (float) Math.max(0.25, Config.TREE_NUMBER_SCALE.get());
    }

    /** Q1: 产物列是否在右侧（Config.TREE_GOAL_SIDE）。 */
    private static boolean goalOnRight() {
        return "right".equals(Config.TREE_GOAL_SIDE.get());
    }

    /** 命中结果：哪棵树、哪个物品、是否最终产物、标签解析到的具体物品（无则 null）。 */
    public record Hit(int treeIndex, EmiIngredient content, boolean isGoal, EmiIngredient resolvedTo) {
    }

    /** 滚动条滑块矩形。 */
    public record Scrollbar(int x, int y, int width, int height) {
    }

    private enum Kind { GOAL, MATERIAL, BYPRODUCT }

    private record Placed(int treeIndex, int x, int y, EmiIngredient content, Kind kind, boolean canCraft,
            EmiIngredient resolvedTo, EmiRecipe producer, TreeData.ResolveState state) {
        /** 需求 16：未知（不可计数）叶子——仅显示，不参与拥有量/标红/自动合成。 */
        boolean unknown() {
            return state == TreeData.ResolveState.LEAF_UNKNOWABLE;
        }
    }

    private TreeRenderer() {
    }

    /** @param px,py,pw,ph 面板屏幕坐标范围。滚动状态从 {@link TreeMode} 读取（惯性/拖动）。 */
    public static void render(GuiGraphicsExtractor g, int px, int py, int pw, int ph, List<TreeData> trees, int mouseX,
            int mouseY) {
        int viewH = ph - 2 * PAD;

        // 用未滚动的总内容高度计算 maxScroll（避免滚动越大 maxScroll 越小的反馈 bug）
        int contentHeight = contentHeightCached(px, py, pw, trees);
        int maxScroll = Math.max(0, contentHeight + 8 - viewH);
        TreeMode.setMaxScroll(maxScroll);
        // 添加新树后自动滚到底部
        if (TreeMode.consumeScrollToBottom()) {
            TreeMode.setScroll(maxScroll);
        }
        TreeMode.update(mouseY, viewH);
        int scroll = TreeMode.getScroll();

        List<Placed> placed = layout(px, py, pw, trees, scroll);

        // 悬停的物品（高亮 + tooltip）
        Placed hovered = null;
        for (Placed p : placed) {
            if (mouseX >= p.x() && mouseX < p.x() + ICON && mouseY >= p.y() && mouseY < p.y() + ICON) {
                hovered = p;
                break;
            }
        }
        // 悬停树时：所有材料不足的节点标红
        boolean hoverOnTree = hovered != null;

        g.enableScissor(px, py, px + pw, py + ph);
        // Q2: 背景透明可在设置里开关（默认透明=不画自绘深背景，透出下层；关闭恢复深色）
        if (!Config.TREE_BACKGROUND_TRANSPARENT.get()) {
            g.fill(px, py, px + pw, py + ph, 0xCC0B0B0B);
        }

        if (placed.isEmpty()) {
            Font font = Minecraft.getInstance().font;
            g.text(font, "no tree", px + PAD, py + PAD, 0xFFAAAAAA);
            g.disableScissor();
            return;
        }

        EmiDrawContext ctx = EmiDrawContext.wrap(g);
        // 数量不交给 EMI 的 RENDER_AMOUNT（它只显示完整数字），改为自绘小字，支持大数字缩写（K / M）
        int baseFlags = EmiIngredient.RENDER_ICON;
        // 拥有量检测共用一个库存快照（每 Placed 每帧重建快照是渲染热点，合成量大时卡顿）
        EmiPlayerInventory invSnap = CraftInventory.currentScreenInventory();

        drawDecor(g, px, pw, trees, placed);
        for (Placed p : placed) {
            if (p.y() > py + ph) {
                continue;
            }
            // 悬停树时：材料不足的节点叠红色（可在 EMI 设置页关闭）；未知节点不标红（需求 16 中立项）
            if (Config.TREE_RED_MARKING.get() && hoverOnTree && !p.canCraft() && !p.unknown()) {
                g.fill(p.x(), p.y(), p.x() + ICON, p.y() + ICON, 0x55FF3030);
            }
            if (p == hovered) {
                // 悬停：材料不足 → 红；可合成 → 白；未知 → 灰（需求 16）
                int hl = p.unknown() ? 0x66AAAAAA : (p.canCraft() ? 0x55FFFFFF : 0x66FF4040);
                g.fill(p.x() - 1, p.y() - 1, p.x() + ICON + 1, p.y() + ICON + 1, hl);
            } else if (p.kind() == Kind.BYPRODUCT) {
                g.fill(p.x() - 1, p.y() - 1, p.x() + ICON + 1, p.y() + ICON + 1, 0x22000000);
            }
            // 标签材料加 RENDER_INGREDIENT → 显示标签徽记
            int flags = baseFlags;
            if (p.content() instanceof TagEmiIngredient) {
                flags |= EmiIngredient.RENDER_INGREDIENT;
            }
            ctx.drawStack(p.content(), p.x(), p.y(), flags);
            // 已解析标签：右侧画短连线到"所选物品"（其图标是独立 Placed）
            if (p.resolvedTo() != null && !p.resolvedTo().isEmpty()) {
                int cy = p.y() + ICON / 2;
                g.fill(p.x() + ICON, cy, p.x() + ICON + 3, cy + 1, lineColor());
            }
            // 数量：自绘小字（缩放倍数可配置），**画在图标格内右下角**。
            // 物品用 formatItemAmount（支持 k/M/G/T 缩写），流体用 formatFluidAmount（mB / L 单位）。
            // z 平移到 200：renderFakeItem 物品模型 z=32 且开启深度测试，数字若在 z=0 会被深度测试
            // 剔除（物品"叠在数字上方"）——z=200 深度更近，保证数字画在图标之上。
            long amt = p.content().getAmount();
            if (amt > 1) {
                String label = p.content() instanceof FluidEmiStack ? formatFluidAmount(amt) : formatItemAmount(amt);
                if (label != null && !label.isEmpty()) {
                    float sc = numberScale();
                    Font font = Minecraft.getInstance().font;
                    var pose = g.pose();
                    pose.pushMatrix();
                    pose.translate(p.x(), p.y());
                    pose.scale(sc, sc);
                    // 右下角内侧：在缩放坐标系里把文字右/下边缘对齐到图标格右缘-1/底缘-1。
                    // 注意公式是 (ICON-1)/sc - 尺寸（先换算到缩放坐标再减），否则文字落在图标中间。
                    int labelW = font.width(label);
                    int textH = font.lineHeight;
                    int x = (int) ((ICON - 1) / sc - labelW);
                    int y = (int) ((ICON - 1) / sc - textH);
                    g.text(font, label, x, y,
                            p.content() instanceof FluidEmiStack ? 0xFF88CCFF : 0xFFFFFFFF);
                    pose.popMatrix();
                }
            }
            // 左上角：当前拥有量不足时显示拥有量（红字），足够时不显示。仅物品节点（非目标、非流体、非未知）。
            // 同样 z=200 防止被物品图标深度测试剔除。未知节点不显示假数量（需求 16）。
            if (p.kind() != Kind.GOAL && !(p.content() instanceof FluidEmiStack) && !p.unknown()) {
                try {
                    long owned = CraftInventory.count(invSnap, p.content());
                    if (owned < amt) {
                        float sc = numberScale();
                        Font font = Minecraft.getInstance().font;
                        String ownLabel = formatItemAmount(owned);
                        var pose = g.pose();
                        pose.pushMatrix();
                        pose.translate(p.x(), p.y());
                        pose.scale(sc, sc);
                        g.text(font, ownLabel, 1, 1, 0xFFFF4040);
                        pose.popMatrix();
                    }
                } catch (Exception ignored) {
                }
            }
        }

        Scrollbar sb = scrollbarRect(px, py, pw, ph, scroll, maxScroll);
        if (sb != null) {
            g.fill(px + pw - SB_W - SB_MARGIN, py + PAD, px + pw - SB_MARGIN, py + ph - PAD, 0x33000000);
            g.fill(sb.x(), sb.y(), sb.x() + sb.width(), sb.y() + sb.height(), 0xFFAAAAAA);
        }

        g.disableScissor();
        if (hovered != null && !hovered.content().isEmpty()) {
            List<EmiStack> stacks = hovered.content().getEmiStacks();
            if (!stacks.isEmpty()) {
                List<Component> lines = stacks.get(0).getTooltipText();
                if (lines != null) {
                    lines = new ArrayList<>(lines);
                    // Productive Bees 蜜蜂：原版 EMI 用 getTooltip()（**总是先显示名字行** getName）；
                    // getTooltipText() 在品种数据缺失（getData null）时跳过名字行 → 第一行直接是灰色 id
                    // （"显示 id"根因）→ 补上名字行（用 getName 的翻译，不硬编码），与原版 EMI 显示一致。
                    if (ProductiveBeesSupport.isBeeEmiStack(stacks.get(0))) {
                        // 第一行若是 id 形式（Identifier 必然含 ":"）→ 无名字行 → 补 `getName()` 名字行
                        String first = lines.isEmpty() ? "" : lines.get(0).getString();
                        if (lines.isEmpty() || first.contains(":")) {
                            Component name = ProductiveBeesSupport.beeDisplayName(stacks.get(0));
                            if (name != null) {
                                lines.add(0, name);
                            }
                        }
                    }
                    // tooltip 明细：需要量 / 拥有量（不足红字）/ 产出配方 / 可合成性
                    appendDetailLines(lines, hovered, invSnap);
                    Font font = Minecraft.getInstance().font;
                    // 26.1.2：tooltip 走 extractor 的 setTooltipForNextFrame（FormattedCharSequence 列表）；替换 renderTooltip
                    List<net.minecraft.util.FormattedCharSequence> seqs = new ArrayList<>();
                    for (Component c : lines) {
                        seqs.add(c.getVisualOrderText());
                    }
                    g.setTooltipForNextFrame(seqs, mouseX, mouseY);
                }
            }
        }
    }

    /** tooltip 明细（追加在 EMI 原版 tooltip 下方）：需要量、拥有量（不足红字）、产出配方、可合成性。
     *  需求 16：未知节点显示中立项（需要量照显 + "无可用库存" + "未知产物（不可自动合成）"）。 */
    private static void appendDetailLines(List<Component> lines, Placed p, EmiPlayerInventory invSnap) {
        try {
            long need = p.content().getAmount();
            boolean fluid = p.content() instanceof FluidEmiStack;
            if (need > 0) {
                lines.add(Component.literal("需要: " + (fluid ? formatFluidAmount(need) : formatItemAmount(need))));
            }
            if (!fluid && p.kind() != Kind.GOAL && !p.unknown()) {
                long owned = CraftInventory.count(invSnap, p.content());
                lines.add(owned < need
                        ? Component.literal("拥有: " + formatItemAmount(owned) + "（不足）")
                                .withStyle(s -> s.withColor(0xFFFF5555))
                        : Component.literal("拥有: " + formatItemAmount(owned)));
            } else if (p.unknown() && p.kind() != Kind.GOAL) {
                // 需求 16：未知（不可计数）→ 显示"无可用库存"中立项，不显示假 0 / 假 ?
                lines.add(Component.literal("库存: 无可用库存").withStyle(s -> s.withColor(0xFFAAAAAA)));
            }
            if (p.producer() != null) {
                lines.add(Component.literal("由 " + producerOutputName(p.producer()) + " 合成"));
            } else if (p.kind() != Kind.GOAL && !p.unknown()) {
                lines.add(Component.literal("库存直接获取"));
            } else if (p.unknown()) {
                lines.add(Component.literal("未知产物（不可自动合成）").withStyle(s -> s.withColor(0xFFAAAAAA)));
            }
            if (p.kind() != Kind.BYPRODUCT && !p.unknown()) {
                lines.add(p.canCraft()
                        ? Component.literal("可合成").withStyle(s -> s.withColor(0xFF55FF55))
                        : Component.literal("材料不足，无法合成").withStyle(s -> s.withColor(0xFFFF5555)));
            }
        } catch (Exception ignored) {
        }
    }

    /** 配方输出物名称（tooltip "由 X 合成"）。 */
    private static String producerOutputName(EmiRecipe recipe) {
        try {
            List<EmiStack> outs = recipe.getOutputs();
            if (outs != null && !outs.isEmpty()) {
                ItemStack s = outs.get(0).getItemStack();
                if (s != null && !s.isEmpty()) {
                    return s.getHoverName().getString();
                }
            }
        } catch (Exception ignored) {
        }
        return "配方";
    }

    /** 所有树的未滚动总内容高度（不含顶部 PAD）。与 {@link #layout} 的物理行数一致。
     *  缓存：trees 每次重建引用变化 + Config 值变化即失效（大树上渲染热点，避免每帧全树遍历）。 */
    private static List<TreeData> cacheTrees;
    private static int cacheRowH;
    private static int cacheItemGap;
    private static int cacheTreeGap;
    private static int cacheBpGap;
    private static int cacheGoalSide;
    private static int cachePx;
    private static int cachePw;
    private static int cacheHeight;

    private static int contentHeightCached(int px, int py, int pw, List<TreeData> trees) {
        int rowH = rowH();
        int gap = itemGap();
        int tg = treeGap();
        int bg = bpGap();
        int side = goalOnRight() ? 1 : 0;
        if (trees == cacheTrees && rowH == cacheRowH && gap == cacheItemGap && tg == cacheTreeGap
                && bg == cacheBpGap && side == cacheGoalSide && px == cachePx && pw == cachePw) {
            return cacheHeight;
        }
        cacheTrees = trees;
        cacheRowH = rowH;
        cacheItemGap = gap;
        cacheTreeGap = tg;
        cacheBpGap = bg;
        cacheGoalSide = side;
        cachePx = px;
        cachePw = pw;
        cacheHeight = contentHeight(px, py, pw, trees);
        return cacheHeight;
    }

    private static int contentHeight(int px, int py, int pw, List<TreeData> trees) {
        if (trees == null) {
            return 0;
        }
        int contentRight = px + pw - SB_LANE;
        boolean right = goalOnRight();
        int rowX, matRight;
        if (right) {
            rowX = px + PAD;
            matRight = contentRight - LEFT_COL;
        } else {
            rowX = px + PAD + LEFT_COL + 4;
            matRight = contentRight;
        }
        // 材料图标统一右移 GUTTER 给左侧 S/序号 留通道（与 layout/drawDecor 一致）
        int matX = rowX + GUTTER;
        int y = py + PAD;
        int maxBottom = y;
        for (TreeData tree : trees) {
            if (tree == null || tree.isEmpty()) {
                continue;
            }
            int top = y;
            // 物理行数 = leafTotal 换行数 + 每层 rows/directInputs 各自换行数（与 layout 一致）
            int rows = 0;
            if (tree.leafTotal() != null && !tree.leafTotal().isEmpty()) {
                rows += rowItemLines(tree.leafTotal(), matX, matRight);
            }
            rows += rowItemLines(tree.directInputs(), matX, matRight);
            for (List<TreeData.TreeItem> r : tree.rows()) {
                rows += rowItemLines(r, matX, matRight);
            }
            int byproductY = top + rows * rowH() + bpGap();
            if (!tree.byproducts().isEmpty()) {
                maxBottom = Math.max(maxBottom, byproductY + ICON);
                y = byproductY + ICON + treeGap();
            } else {
                maxBottom = Math.max(maxBottom, top + rows * rowH());
                y = byproductY + treeGap() - bpGap();
            }
        }
        return Math.max(0, maxBottom - (py + PAD));
    }

    /** 一行物品（可能超宽）换行铺满的**物理行数**（供 contentHeight 用，与 layout 放置一致）。 */
    private static int rowItemLines(List<TreeData.TreeItem> items, int rowX, int matRight) {
        if (items == null) {
            return 0;
        }
        int x = rowX;
        int lines = 1;
        boolean any = false;
        boolean lineStart = true;
        for (TreeData.TreeItem item : items) {
            if (item == null || item.content() == null || item.content().isEmpty()) {
                continue;
            }
            int step = ICON + itemGap();
            if (!lineStart && x + step > matRight) {
                lines++;
                x = rowX;
                lineStart = true;
            }
            if (x + ICON <= matRight) {
                lineStart = false;
            }
            x += step;
            if (item.hasResolved()) {
                x += step;
            }
            any = true;
        }
        return any ? lines : 0;
    }

    /** 放置一行物品（可能超宽换行铺满），返回下一物理行的 y。layout 与 rowItemLines 共用同一切换行判定。 */
    private static int placeRowItems(List<Placed> out, int ti, int rowX, int matRight, int startY,
            List<TreeData.TreeItem> items) {
        if (items == null || items.isEmpty()) {
            return startY; // 空行不占物理行（与 rowItemLines 一致）
        }
        int curY = startY;
        int x = rowX;
        boolean lineStart = true;
        for (TreeData.TreeItem item : items) {
            if (item == null || item.content().isEmpty()) {
                continue;
            }
            int step = ICON + itemGap();
            if (!lineStart && x + step > matRight) {
                x = rowX;
                curY += rowH();
            }
            if (x + ICON <= matRight) {
                out.add(new Placed(ti, x, curY, item.content(), Kind.MATERIAL, item.canCraft(), item.resolvedTo(),
                        item.producer(), item.state()));
                lineStart = false;
            }
            x += step;
            if (item.hasResolved()) {
                if (x + ICON <= matRight) {
                    out.add(new Placed(ti, x, curY, item.resolvedTo(), Kind.MATERIAL, true, null, null,
                            TreeData.ResolveState.RESOLVED));
                }
                x += step;
            }
        }
        return curY + rowH();
    }

    /** 命中测试：返回鼠标位置对应的树/物品。 */
    public static Hit hitTest(int px, int py, int pw, int ph, List<TreeData> trees, int mx, int my) {
        if (trees == null || trees.isEmpty()) {
            return null;
        }
        int scroll = TreeMode.getScroll();
        for (Placed p : layout(px, py, pw, trees, scroll)) {
            if (mx >= p.x() && mx < p.x() + ICON && my >= p.y() && my < p.y() + ICON) {
                return new Hit(p.treeIndex(), p.content(), p.kind() == Kind.GOAL, p.resolvedTo());
            }
        }
        return null;
    }

    /** 滚动条滑块几何（渲染与拖动共用）。 */
    public static Scrollbar scrollbarRect(int px, int py, int pw, int ph, int scroll, int maxScroll) {
        if (maxScroll <= 0) {
            return null;
        }
        int trackX = px + pw - SB_W - SB_MARGIN;
        int trackY = py + PAD;
        int trackH = ph - 2 * PAD;
        int contentH = trackH + maxScroll;
        int handleH = Math.max(14, trackH * trackH / Math.max(1, contentH));
        int maxY = trackH - handleH;
        int handleY = trackY + (int) ((long) maxY * scroll / Math.max(1, maxScroll));
        return new Scrollbar(trackX, handleY, SB_W, handleH);
    }

    /** 计算所有树中每个物品的屏幕位置（渲染与命中测试共用）。产物列位置由 {@link #goalOnRight()} 决定。 */
    private static List<Placed> layout(int px, int py, int pw, List<TreeData> trees, int scroll) {
        List<Placed> out = new ArrayList<>();
        int y = py + PAD - scroll;
        int contentRight = px + pw - SB_LANE;
        boolean right = goalOnRight();
        int goalX;
        int dividerX;
        int rowX;
        if (right) {
            rowX = px + PAD;                        // 产物在右：材料从最左侧排
            dividerX = contentRight - LEFT_COL;     // 分割线在右
            goalX = dividerX + 4;                   // 产物贴分割线右侧
        } else {
            goalX = px + PAD;                       // 产物在左（原逻辑）
            dividerX = goalX + LEFT_COL;
            rowX = dividerX + 4;
        }
        // 材料区右界 = 分割线（目标在右时材料不能越过分割线遮挡目标；目标在左时材料可用到面板右边）
        int matRight = right ? dividerX : contentRight;
        // 材料图标统一右移 GUTTER 给左侧 S/序号 留通道（与 contentHeight/drawDecor 一致）
        int matX = rowX + GUTTER;
        for (int ti = 0; ti < trees.size(); ti++) {
            TreeData tree = trees.get(ti);
            if (tree == null || tree.isEmpty()) {
                continue;
            }
            int top = y;
            out.add(new Placed(ti, goalX, top, tree.goal().content(), Kind.GOAL, tree.goal().canCraft(),
                    tree.goal().resolvedTo(), tree.goal().producer(), tree.goal().state()));

            // 材料区从目标行（top）开始：leafTotal → 原分层 rows → directInputs，每行超宽自动换行铺满
            int curY = top;
            if (tree.leafTotal() != null && !tree.leafTotal().isEmpty()) {
                curY = placeRowItems(out, ti, matX, matRight, curY, tree.leafTotal());
            }
            List<List<TreeData.TreeItem>> restRows = new ArrayList<>(tree.rows());
            restRows.add(tree.directInputs());
            for (List<TreeData.TreeItem> rowItems : restRows) {
                curY = placeRowItems(out, ti, matX, matRight, curY, rowItems);
            }

            int byproductY = curY + bpGap();
            if (!tree.byproducts().isEmpty()) {
                int bx = matX;
                for (EmiIngredient bp : tree.byproducts()) {
                    if (bx + ICON <= matRight) {
                        out.add(new Placed(ti, bx, byproductY, bp, Kind.BYPRODUCT, true, null, null,
                                TreeData.ResolveState.RESOLVED));
                    }
                    bx += ICON + itemGap();
                }
                y = byproductY + ICON + treeGap();
            } else {
                y = byproductY + treeGap() - bpGap();
            }
        }
        return out;
    }

    /** 树装饰（在图标之下绘制）：每棵树左侧的"["括号、目标/材料分割线、副产物上横线、
     *  总材料行左侧 S(sum) 标记、材料行左侧层级序号 1、2、…（自上而下，普通数字不带圆圈）。
     *  线条颜色走配置预设（{@link #lineColor} / {@link #dividerColor}）。 */
    private static void drawDecor(GuiGraphicsExtractor g, int px, int pw, List<TreeData> trees, List<Placed> placed) {
        int contentRight = px + pw - SB_LANE;
        boolean right = goalOnRight();
        int dividerX = right ? (contentRight - LEFT_COL) : (px + PAD + LEFT_COL);
        int rowX = right ? (px + PAD) : (px + PAD + LEFT_COL + 4);
        int matX = rowX + GUTTER;
        int matRight = right ? dividerX : contentRight;
        int i = 0;
        while (i < placed.size()) {
            int ti = placed.get(i).treeIndex();
            int j = i;
            while (j < placed.size() && placed.get(j).treeIndex() == ti) {
                j++;
            }
            int top = placed.get(i).y();
            int maxY = top;
            int byproductY = -1;
            for (int k = i; k < j; k++) {
                Placed p = placed.get(k);
                maxY = Math.max(maxY, p.y() + ICON);
                if (p.kind() == Kind.BYPRODUCT && byproductY < 0) {
                    byproductY = p.y() - 2;
                }
            }
            // 左侧括号：1px 竖线，顶部/底部各突出 BRACKET_OVER px，并带顶/底横帽，
            // 形成"["把一棵配方（含副产物）括起来
            int bx = px + PAD - 2;
            int bTop = top - BRACKET_OVER;
            int bBot = maxY + BRACKET_OVER;
            int line = lineColor();
            g.fill(bx, bTop, bx + 1, bBot + 1, line);
            g.fill(bx, bTop, bx + BRACKET_CAP, bTop + 1, line);
            g.fill(bx, bBot, bx + BRACKET_CAP, bBot + 1, line);
            // 分割线（目标列与材料区之间）+ 副产物区上方横线
            g.fill(dividerX, top, dividerX + 1, maxY, dividerColor());
            if (byproductY >= 0) {
                g.fill(matX, byproductY, Math.min(matX + 240, matRight), byproductY + 1, dividerColor());
            }
            // 总材料行左侧 S(sum) + 各层合成序号（自上而下 1、2、…；S/序号共用左侧通道）
            TreeData tree = trees.get(ti);
            int y = top;
            if (tree != null) {
                if (tree.leafTotal() != null && !tree.leafTotal().isEmpty()) {
                    drawSideTag(g, matX, y, "S");
                    y += rowItemLines(tree.leafTotal(), matX, matRight) * rowH();
                }
                int num = 1;
                for (List<TreeData.TreeItem> row : tree.rows()) {
                    int lines = rowItemLines(row, matX, matRight);
                    if (lines > 0) {
                        drawSideTag(g, matX, y, String.valueOf(num++));
                        y += lines * rowH();
                    }
                }
                int lines = rowItemLines(tree.directInputs(), matX, matRight);
                if (lines > 0) {
                    drawSideTag(g, matX, y, String.valueOf(num));
                }
            }
            i = j;
        }
    }

    /** 在材料行最左图标左侧画 S/序号：右缘对齐图标左缘 -3px，垂直居中于 16px 行；颜色同线条亮色。 */
    private static void drawSideTag(GuiGraphicsExtractor g, int matX, int y, String label) {
        Font font = Minecraft.getInstance().font;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(matX - 3, y);
        pose.scale(TAG_SCALE, TAG_SCALE);
        // 右缘对齐（缩放坐标系里 x = -宽度）；垂直居中：(ICON/缩放 - 行高)/2
        int sx = -font.width(label);
        int sy = (int) ((ICON / TAG_SCALE - font.lineHeight) / 2f);
        g.text(font, label, sx, sy, lineColor());
        pose.popMatrix();
    }

    /** 物品数量大数字缩写（1000 进制多级）：<1000 原样；≥1k / ≥1M / ≥1G / ≥1T。一位小数去尾零。 */
    private static String formatItemAmount(long v) {
        if (v < 1000) {
            return String.valueOf(v);
        }
        double d = v;
        String[] units = {"k", "M", "G", "T"};
        int u = -1;
        while (d >= 1000.0 && u < units.length - 1) {
            d /= 1000.0;
            u++;
        }
        return trim1(d) + units[Math.max(0, u)];
    }

    /** 一位小数去尾零（整数则去掉 .0）。 */
    private static String trim1(double d) {
        String s = String.format(java.util.Locale.ROOT, "%.1f", d);
        if (s.endsWith(".0")) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }

    private static final String[] FLUID_UNITS = {"L", "K L", "M L", "G L"};

    /** 流体用量：<1000mB 显示 mB；否则按 L / K L / M L / G L 分段（1000mB=1L，两位小数去尾零）。 */
    private static String formatFluidAmount(long mB) {
        if (mB < 1000) {
            return mB + "mB";
        }
        double l = mB / 1000.0;
        int unit = 0;
        while (l >= 1000.0 && unit < FLUID_UNITS.length - 1) {
            l /= 1000.0;
            unit++;
        }
        return trim2(l) + FLUID_UNITS[unit];
    }

    private static String trim2(double v) {
        String s = String.format(java.util.Locale.ROOT, "%.2f", v);
        if (s.endsWith("00")) {
            return s.substring(0, s.length() - 3);
        }
        if (s.endsWith("0")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }
}
