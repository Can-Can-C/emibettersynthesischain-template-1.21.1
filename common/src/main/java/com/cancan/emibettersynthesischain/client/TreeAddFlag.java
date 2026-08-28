package com.cancan.emibettersynthesischain.client;

/**
 * 记录 EMI 最近一次 {@code BoM.setGoal} 的时刻，用于区分"添加配方树"（keybind/配方页按钮先 setGoal）
 * 与"切换配方树视图"（左下角树按钮直接调 viewRecipeTree）。
 */
public final class TreeAddFlag {
    /**
     * setGoal → viewRecipeTree 之间的最大间隔（纳秒）。两事件在 EMI 同一个调用栈/同一帧内紧邻发生，
     * 正常几乎即时；取 500ms 在慢机/卡顿下也不易误判，同时仍能区分"左下角树按钮的纯切换"
     * （那种路径本就不会在窗口内 setGoal）。
     */
    private static final long ADD_WINDOW_NANOS = 500_000_000L; // 500ms

    private static volatile long lastSetGoalNanos = 0;

    private TreeAddFlag() {
    }

    public static void markSetGoal() {
        lastSetGoalNanos = System.nanoTime();
    }

    /** 窗口内是否刚 setGoal（视为"添加"动作）。 */
    public static boolean wasJustSetGoal() {
        return System.nanoTime() - lastSetGoalNanos < ADD_WINDOW_NANOS;
    }
}
