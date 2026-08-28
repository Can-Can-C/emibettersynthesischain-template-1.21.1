package com.cancan.emibettersynthesischain.client;

/**
 * 合成树线条配色预设（纯业务数据，不依赖 EMI）。
 *
 * <p>每个预设提供两个色阶：{@code base}（亮色，用于连接线 / 树括号 / 序号 / S 标记）与
 * {@code deep}（深色，用于目标/材料分割线与副产物横线，保持层次）。配置存预设 id 字符串
 * （{@code Config.TREE_LINE_COLOR} / {@code TREE_DIVIDER_COLOR}），渲染时经
 * {@link #fromId} 取色。设置页下拉见 {@code mixin/TreeColorEnum}（EMI ConfigEnum 适配）。</p>
 */
public enum TreeColorPreset {
    TEAL("teal", 0xFF2DD4BF, 0xFF17A396),
    BLUE("blue", 0xFF4DA6FF, 0xFF2F7FE0),
    PURPLE("purple", 0xFFB48CFF, 0xFF8A5CE6),
    WHITE("white", 0xFFE8E8E8, 0xFFA8A8A8),
    GOLD("gold", 0xFFFFC84D, 0xFFE0A02F),
    RED("red", 0xFFFF6B6B, 0xFFD94F4F),
    GREEN("green", 0xFF5BE37A, 0xFF37B85C),
    GRAY("gray", 0xFFAAAAAA, 0xFF555555);

    private final String id;
    private final int base;
    private final int deep;

    TreeColorPreset(String id, int base, int deep) {
        this.id = id;
        this.base = base;
        this.deep = deep;
    }

    /** 配置存盘的 id（如 {@code "teal"}）。 */
    public String getId() {
        return id;
    }

    /** 亮色（连接线 / 括号 / 序号 / S）。 */
    public int base() {
        return base;
    }

    /** 深色（分割线 / 副产物横线）。 */
    public int deep() {
        return deep;
    }

    /** 按 id 取预设；未知 id 回退青绿（配置校验已挡，防御性回退）。 */
    public static TreeColorPreset fromId(String id) {
        for (TreeColorPreset p : values()) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return TEAL;
    }

    /** 配置校验：id 是否属于预设集合。 */
    public static boolean isKnown(String id) {
        for (TreeColorPreset p : values()) {
            if (p.id.equals(id)) {
                return true;
            }
        }
        return false;
    }
}