package com.cancan.emibettersynthesischain.client;

/**
 * 记录 EMI 最近一次 {@code BoM.setGoal} 的时刻，用于区分"添加配方树"（keybind/配方页按钮先 setGoal）
 * 与"切换配方树视图"（左下角树按钮直接调 viewRecipeTree）。
 */
public final class TreeAddFlag {
    private static volatile long lastSetGoalNanos = 0;

    private TreeAddFlag() {
    }

    public static void markSetGoal() {
        lastSetGoalNanos = System.nanoTime();
    }

    /** 50ms 内是否刚 setGoal（视为"添加"动作）。 */
    public static boolean wasJustSetGoal() {
        return System.nanoTime() - lastSetGoalNanos < 50_000_000L;
    }
}
