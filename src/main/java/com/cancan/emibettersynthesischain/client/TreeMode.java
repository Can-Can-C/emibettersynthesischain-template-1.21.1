package com.cancan.emibettersynthesischain.client;

/**
 * 合成树视图开关 + 侧边栏宽度还原 + 滚动（惯性 / 拖动 / 越界钳制）。
 */
public final class TreeMode {
    private static boolean active = false;
    private static int originalWidth = -1;
    private static int scrollOffset = 0;
    private static int maxScroll = 0;
    private static double velocity = 0;
    private static boolean dragging = false;
    private static int dragStartMouseY = 0;
    private static int dragStartOffset = 0;
    private static boolean scrollToBottom = false;

    private TreeMode() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
        if (!value) {
            scrollOffset = 0;
            maxScroll = 0;
            velocity = 0;
            dragging = false;
            scrollToBottom = false; // 清理残留的"滚到底"请求，避免下次进入树模式误滚底
        }
    }

    public static void toggle() {
        active = !active;
    }

    public static boolean hasOriginalWidth() {
        return originalWidth >= 0;
    }

    public static int getOriginalWidth() {
        return originalWidth;
    }

    public static void setOriginalWidth(int width) {
        originalWidth = width;
    }

    public static void clearOriginalWidth() {
        originalWidth = -1;
    }

    public static int getScroll() {
        return scrollOffset;
    }

    /** 添加新树后请求滚到底部（下一次渲染时生效）。 */
    public static void requestScrollToBottom() {
        scrollToBottom = true;
    }

    /** 取出并清除"滚到底"请求。 */
    public static boolean consumeScrollToBottom() {
        boolean b = scrollToBottom;
        scrollToBottom = false;
        return b;
    }

    public static void setScroll(int value) {
        scrollOffset = Math.max(0, value);
    }

    public static int getMaxScroll() {
        return maxScroll;
    }

    public static void setMaxScroll(int max) {
        maxScroll = Math.max(0, max);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
    }

    /** 滚轮滚动：给惯性速度。 */
    public static void addScroll(int delta) {
        velocity += delta * 0.8;
    }

    /** 每帧更新：惯性滑动 + 拖动 + 钳制到 [0, maxScroll]。 */
    public static void update(int mouseY, int trackHeight) {
        if (dragging && trackHeight > 0) {
            double dy = (double) (mouseY - dragStartMouseY) * maxScroll / trackHeight;
            scrollOffset = dragStartOffset + (int) dy;
        } else {
            if (Math.abs(velocity) < 0.5) {
                velocity = 0;
            }
            scrollOffset += (int) Math.round(velocity);
            velocity *= 0.82;
        }
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
    }

    public static void startDrag(int mouseY) {
        dragging = true;
        dragStartMouseY = mouseY;
        dragStartOffset = scrollOffset;
        velocity = 0;
    }

    public static void stopDrag() {
        dragging = false;
    }
}