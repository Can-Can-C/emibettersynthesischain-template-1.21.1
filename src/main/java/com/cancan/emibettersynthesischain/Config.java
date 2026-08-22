package com.cancan.emibettersynthesischain;

import com.cancan.emibettersynthesischain.client.TreeColorPreset;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 本 mod 配置（Forge 1.20.1 分支，写入 `config/emibettersynthesischain-common.toml`）。
 * 值可经 EMI 设置页修改（见 {@code mixin/ConfigScreenMixin}），改动后调用 {@link #save()} 落盘。
 */
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // 各配置默认值（EMI 设置页"重置"按钮恢复用）
    private static final int DEF_TREE_SIDEBAR_WIDTH = 10;
    private static final int DEF_TREE_ROW_SPACING = 16;
    private static final int DEF_TREE_ITEM_GAP = 4;
    private static final int DEF_TREE_TREE_GAP = 8;
    private static final int DEF_TREE_BYPRODUCT_GAP = 6;
    private static final boolean DEF_TREE_RED_MARKING = true;
    private static final int DEF_AUTO_CRAFT_SHOW_TICKS = 6;
    private static final int DEF_AUTO_CRAFT_GAP_TICKS = 2;
    private static final boolean DEF_AUTO_CRAFT_FAILURE_MESSAGES = true;
    private static final String DEF_TREE_GOAL_SIDE = "right";
    private static final boolean DEF_TREE_BACKGROUND_TRANSPARENT = true;
    private static final double DEF_TREE_NUMBER_SCALE = 0.5;
    private static final String DEF_TREE_LINE_COLOR = "teal";
    private static final String DEF_TREE_DIVIDER_COLOR = "teal";

    /** 树模式下左侧栏的物品列数（宽度 = 列数×9px + 内边距，越大面板越宽）。 */
    public static final ForgeConfigSpec.IntValue TREE_SIDEBAR_WIDTH = BUILDER
            .comment("Items per row for the left sidebar while in tree mode (larger = wider panel).",
                    "Default EMI left sidebar is 6; this mod widens it to this value in tree mode.")
            .defineInRange("treeSidebarWidth", DEF_TREE_SIDEBAR_WIDTH, 4, 24);

    /** 合成树行高（px，即同一树内相邻行的间距）。 */
    public static final ForgeConfigSpec.IntValue TREE_ROW_SPACING = BUILDER
            .comment("Row height (px) of the synthesis tree = gap between adjacent rows in a tree.",
                    "Larger makes rows farther apart but the whole tree taller.")
            .defineInRange("treeRowSpacing", DEF_TREE_ROW_SPACING, 8, 40);

    /** 同行物品间距（px）。 */
    public static final ForgeConfigSpec.IntValue TREE_ITEM_GAP = BUILDER
            .comment("Gap (px) between items in the same row.")
            .defineInRange("treeItemGap", DEF_TREE_ITEM_GAP, 0, 16);

    /** 树与树之间的垂直间距（px）。 */
    public static final ForgeConfigSpec.IntValue TREE_TREE_GAP = BUILDER
            .comment("Vertical gap (px) between trees (independent of row spacing).")
            .defineInRange("treeTreeGap", DEF_TREE_TREE_GAP, 2, 24);

    /** 副产物区上方的间距（px）。 */
    public static final ForgeConfigSpec.IntValue TREE_BYPRODUCT_GAP = BUILDER
            .comment("Gap (px) above the byproduct section.")
            .defineInRange("treeByproductGap", DEF_TREE_BYPRODUCT_GAP, 2, 16);

    /** 悬停树时，材料不足节点是否标红。 */
    public static final ForgeConfigSpec.BooleanValue TREE_RED_MARKING = BUILDER
            .comment("Red-mark insufficient-material nodes while hovering the tree.")
            .define("treeRedMarking", DEF_TREE_RED_MARKING);

    /** 合成树最终产物列在左还是右（"left"/"right"），默认右。 */
    public static final ForgeConfigSpec.ConfigValue<String> TREE_GOAL_SIDE = BUILDER
            .comment("Which side the final product column sits on in the synthesis tree.",
                    "Use \"left\" or \"right\". Default is right.")
            .define("treeGoalSide", DEF_TREE_GOAL_SIDE,
                    (Object v) -> "left".equals(v) || "right".equals(v));

    /** 合成树背景是否透明（默认透明；关闭恢复深色背景）。 */
    public static final ForgeConfigSpec.BooleanValue TREE_BACKGROUND_TRANSPARENT = BUILDER
            .comment("Make the synthesis tree background transparent (like the favorites sidebar),",
                    "or draw the old dark background. Default is transparent.")
            .define("treeBackgroundTransparent", DEF_TREE_BACKGROUND_TRANSPARENT);

    /** 合成树图标上数字（数量/拥有量/流体用量）的缩放倍数。 */
    public static final ForgeConfigSpec.DoubleValue TREE_NUMBER_SCALE = BUILDER
            .comment("Scale of the small number text drawn on tree item icons (count / owned / fluid amount).",
                    "0.5 = half size, 1.0 = full size.")
            .defineInRange("treeNumberScale", DEF_TREE_NUMBER_SCALE, 0.25, 1.0);

    /** 合成树线条配色预设（连接线 / 树括号 / 层级序号 / S 标记的亮色）。 */
    public static final ForgeConfigSpec.ConfigValue<String> TREE_LINE_COLOR = BUILDER
            .comment("Color preset of the tree lines: tag links, the tree bracket, level numbers and the S(sum) mark.",
                    "Presets: teal, blue, purple, white, gold, red, green, gray.")
            .define("treeLineColor", DEF_TREE_LINE_COLOR, (Object v) -> TreeColorPreset.isKnown((String) v));

    /** 合成树分割线配色预设（目标/材料分割线与副产物横线，取该预设的深色阶）。 */
    public static final ForgeConfigSpec.ConfigValue<String> TREE_DIVIDER_COLOR = BUILDER
            .comment("Color preset of the tree divider lines: goal/materials separator and the byproduct line.",
                    "Same presets as treeLineColor; uses the deeper shade of the chosen preset.")
            .define("treeDividerColor", DEF_TREE_DIVIDER_COLOR, (Object v) -> TreeColorPreset.isKnown((String) v));

    /** 可见合成链每步材料进格后的显示时长（tick）。 */
    public static final ForgeConfigSpec.IntValue AUTO_CRAFT_SHOW_TICKS = BUILDER
            .comment("Ticks each crafting step stays visible in the grid (show duration).")
            .defineInRange("autoCraftShowTicks", DEF_AUTO_CRAFT_SHOW_TICKS, 2, 40);

    /** 可见合成链步与步之间的间隔（tick）。 */
    public static final ForgeConfigSpec.IntValue AUTO_CRAFT_GAP_TICKS = BUILDER
            .comment("Ticks between steps (empty grid gap).")
            .defineInRange("autoCraftGapTicks", DEF_AUTO_CRAFT_GAP_TICKS, 0, 20);

    /** 无法合成时是否显示红字提示。 */
    public static final ForgeConfigSpec.BooleanValue AUTO_CRAFT_FAILURE_MESSAGES = BUILDER
            .comment("Show a red message when auto-craft fails.")
            .define("autoCraftFailureMessages", DEF_AUTO_CRAFT_FAILURE_MESSAGES);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    /** 把改动写回配置文件（EMI 设置页改动后调用）。 */
    public static void save() {
        SPEC.save();
    }

    /** 把所有配置重置为默认值并落盘（EMI 设置页"重置"按钮）。 */
    public static void resetAll() {
        TREE_SIDEBAR_WIDTH.set(DEF_TREE_SIDEBAR_WIDTH);
        TREE_ROW_SPACING.set(DEF_TREE_ROW_SPACING);
        TREE_ITEM_GAP.set(DEF_TREE_ITEM_GAP);
        TREE_TREE_GAP.set(DEF_TREE_TREE_GAP);
        TREE_BYPRODUCT_GAP.set(DEF_TREE_BYPRODUCT_GAP);
        TREE_RED_MARKING.set(DEF_TREE_RED_MARKING);
        TREE_GOAL_SIDE.set(DEF_TREE_GOAL_SIDE);
        TREE_BACKGROUND_TRANSPARENT.set(DEF_TREE_BACKGROUND_TRANSPARENT);
        TREE_NUMBER_SCALE.set(DEF_TREE_NUMBER_SCALE);
        TREE_LINE_COLOR.set(DEF_TREE_LINE_COLOR);
        TREE_DIVIDER_COLOR.set(DEF_TREE_DIVIDER_COLOR);
        AUTO_CRAFT_SHOW_TICKS.set(DEF_AUTO_CRAFT_SHOW_TICKS);
        AUTO_CRAFT_GAP_TICKS.set(DEF_AUTO_CRAFT_GAP_TICKS);
        AUTO_CRAFT_FAILURE_MESSAGES.set(DEF_AUTO_CRAFT_FAILURE_MESSAGES);
        save();
    }
}