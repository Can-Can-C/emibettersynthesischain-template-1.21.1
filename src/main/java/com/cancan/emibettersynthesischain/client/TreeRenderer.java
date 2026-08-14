package com.cancan.emibettersynthesischain.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.cancan.emibettersynthesischain.Config;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.FluidEmiStack;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

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
    private static final int LINE_COLOR = 0xFF888888;
    private static final int DIVIDER_COLOR = 0xFF444444;
    private static final int SB_W = 4;
    private static final int SB_MARGIN = 2;
    private static final int SB_LANE = SB_W + SB_MARGIN + 2;

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

    /** 命中结果：哪棵树、哪个物品、是否最终产物、标签解析到的具体物品（无则 null）。 */
    public record Hit(int treeIndex, EmiIngredient content, boolean isGoal, EmiIngredient resolvedTo) {
    }

    /** 滚动条滑块矩形。 */
    public record Scrollbar(int x, int y, int width, int height) {
    }

    private enum Kind { GOAL, MATERIAL, BYPRODUCT }

    private record Placed(int treeIndex, int x, int y, EmiIngredient content, Kind kind, boolean canCraft,
            EmiIngredient resolvedTo) {
    }

    private TreeRenderer() {
    }

    /** @param px,py,pw,ph 面板屏幕坐标范围。滚动状态从 {@link TreeMode} 读取（惯性/拖动）。 */
    public static void render(GuiGraphics g, int px, int py, int pw, int ph, List<TreeData> trees, int mouseX,
            int mouseY) {
        int viewH = ph - 2 * PAD;

        // 用未滚动的总内容高度计算 maxScroll（避免滚动越大 maxScroll 越小的反馈 bug）
        int contentHeight = contentHeight(px, py, pw, trees);
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
        g.fill(px, py, px + pw, py + ph, 0xCC0B0B0B);

        if (placed.isEmpty()) {
            Font font = Minecraft.getInstance().font;
            g.drawString(font, "no tree", px + PAD, py + PAD, 0xFFAAAAAA);
            g.disableScissor();
            return;
        }

        EmiDrawContext ctx = EmiDrawContext.wrap(g);
        int baseFlags = EmiIngredient.RENDER_ICON | EmiIngredient.RENDER_AMOUNT;

        drawDividers(g, px, placed);
        for (Placed p : placed) {
            if (p.y() > py + ph) {
                continue;
            }
            // 悬停树时：材料不足的节点叠红色（可在 EMI 设置页关闭）
            if (Config.TREE_RED_MARKING.get() && hoverOnTree && !p.canCraft()) {
                g.fill(p.x(), p.y(), p.x() + ICON, p.y() + ICON, 0x55FF3030);
            }
            if (p == hovered) {
                // 悬停：材料不足 → 红；可合成 → 白
                g.fill(p.x() - 1, p.y() - 1, p.x() + ICON + 1, p.y() + ICON + 1,
                        p.canCraft() ? 0x55FFFFFF : 0x66FF4040);
            } else if (p.kind() == Kind.BYPRODUCT) {
                g.fill(p.x() - 1, p.y() - 1, p.x() + ICON + 1, p.y() + ICON + 1, 0x22000000);
            }
            // 标签材料加 RENDER_INGREDIENT → 显示标签徽记；流体去掉 RENDER_AMOUNT（数量用下方文本）
            int flags = baseFlags;
            if (p.content() instanceof TagEmiIngredient) {
                flags |= EmiIngredient.RENDER_INGREDIENT;
            }
            if (p.content() instanceof FluidEmiStack) {
                flags &= ~EmiIngredient.RENDER_AMOUNT;
            }
            ctx.drawStack(p.content(), p.x(), p.y(), flags);
            // 已解析标签：右侧画短连线到"所选物品"（其图标是独立 Placed）
            if (p.resolvedTo() != null && !p.resolvedTo().isEmpty()) {
                int cy = p.y() + ICON / 2;
                g.fill(p.x() + ICON, cy, p.x() + ICON + 3, cy + 1, LINE_COLOR);
            }
            // 流体：用量文本用 0.5 倍小字画在图标格内底部
            if (p.content() instanceof FluidEmiStack) {
                long amt = p.content().getAmount();
                if (amt > 0) {
                    Font font = Minecraft.getInstance().font;
                    String label = formatFluidAmount(amt);
                    var pose = g.pose();
                    pose.pushPose();
                    pose.translate(p.x(), p.y() + 11, 0);
                    pose.scale(0.5f, 0.5f, 1f);
                    g.drawString(font, label, 2, 1, 0xFF88CCFF);
                    pose.popPose();
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
                if (lines != null && !lines.isEmpty()) {
                    Font font = Minecraft.getInstance().font;
                    g.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
                }
            }
        }
    }

    /** 所有树的未滚动总内容高度（不含顶部 PAD）。 */
    private static int contentHeight(int px, int py, int pw, List<TreeData> trees) {
        if (trees == null) {
            return 0;
        }
        int y = py + PAD;
        int maxBottom = y;
        for (TreeData tree : trees) {
            if (tree == null || tree.isEmpty()) {
                continue;
            }
            int top = y;
            // layout 总是把 directInputs 作为最后一行加入，行数一致
            int rows = tree.rows().size() + 1;
            int byproductY = top + rows * rowH() + bpGap();
            if (!tree.byproducts().isEmpty()) {
                maxBottom = Math.max(maxBottom, byproductY + ICON);
                y = byproductY + ICON + treeGap();
            } else {
                maxBottom = Math.max(maxBottom, top + rows * rowH()); // 树底 = 最后一行底，不叠加 bpGap 幻影
                y = byproductY + treeGap() - bpGap();
            }
        }
        return Math.max(0, maxBottom - (py + PAD));
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

    /** 计算所有树中每个物品的屏幕位置（渲染与命中测试共用）。 */
    private static List<Placed> layout(int px, int py, int pw, List<TreeData> trees, int scroll) {
        List<Placed> out = new ArrayList<>();
        int y = py + PAD - scroll;
        int goalX = px + PAD;
        int dividerX = goalX + LEFT_COL;
        int rowX = dividerX + 4;
        int contentRight = px + pw - SB_LANE;
        for (int ti = 0; ti < trees.size(); ti++) {
            TreeData tree = trees.get(ti);
            if (tree == null || tree.isEmpty()) {
                continue;
            }
            int top = y;
            out.add(new Placed(ti, goalX, top, tree.goal().content(), Kind.GOAL, tree.goal().canCraft(),
                    tree.goal().resolvedTo()));

            List<List<TreeData.TreeItem>> allRows = new ArrayList<>(tree.rows());
            allRows.add(tree.directInputs());
            for (int r = 0; r < allRows.size(); r++) {
                int rowY = top + r * rowH();
                int x = rowX;
                for (TreeData.TreeItem item : allRows.get(r)) {
                    if (item != null && !item.content().isEmpty() && x + ICON <= contentRight) {
                        out.add(new Placed(ti, x, rowY, item.content(), Kind.MATERIAL, item.canCraft(),
                                item.resolvedTo()));
                    }
                    x += ICON + itemGap();
                    if (item != null && item.hasResolved()) {
                        // 所选物品占用下一个正常格子（连线的另一头），可点击
                        if (x + ICON <= contentRight) {
                            out.add(new Placed(ti, x, rowY, item.resolvedTo(), Kind.MATERIAL, true, null));
                        }
                        x += ICON + itemGap();
                    }
                }
            }

            int byproductY = top + allRows.size() * rowH() + bpGap();
            if (!tree.byproducts().isEmpty()) {
                int bx = rowX;
                for (EmiIngredient bp : tree.byproducts()) {
                    if (bx + ICON <= contentRight) {
                        out.add(new Placed(ti, bx, byproductY, bp, Kind.BYPRODUCT, true, null));
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

    /** 分割线：目标列与材料区之间的竖线，以及副产物区上方的横线。 */
    private static void drawDividers(GuiGraphics g, int px, List<Placed> placed) {
        int dividerX = px + PAD + LEFT_COL;
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
            g.fill(dividerX, top, dividerX + 1, maxY, DIVIDER_COLOR);
            if (byproductY >= 0) {
                g.fill(px + PAD, byproductY, px + PAD + 240, byproductY + 1, DIVIDER_COLOR);
            }
            i = j;
        }
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
