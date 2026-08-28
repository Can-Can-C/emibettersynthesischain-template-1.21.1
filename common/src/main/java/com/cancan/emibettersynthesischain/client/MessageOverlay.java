package com.cancan.emibettersynthesischain.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 在容器屏（物品栏等）内自绘的一条提示消息（顶部居中，约 2.5 秒）。
 * 替代 vanilla Toast（在背包内会被容器屏挡住）。成功白字、失败红字。
 */
public final class MessageOverlay {
    private static final int COLOR_SUCCESS = 0xFFFFFFFF;
    private static final int COLOR_FAILURE = 0xFFFF5555;

    private static Component message;
    private static int color = COLOR_SUCCESS;
    private static long expiryMs;

    private MessageOverlay() {
    }

    public static void show(Component text) {
        show(text, true);
    }

    public static void show(Component text, boolean success) {
        message = text;
        color = success ? COLOR_SUCCESS : COLOR_FAILURE;
        expiryMs = System.currentTimeMillis() + 2500;
    }

    public static void render(GuiGraphics g) {
        if (message == null) {
            return;
        }
        if (System.currentTimeMillis() > expiryMs) {
            message = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        String text = message.getString();
        int textW = font.width(text);
        int x = (mc.getWindow().getGuiScaledWidth() - textW) / 2;
        int y = 14;
        g.fill(x - 4, y - 2, x + textW + 4, y + 10, 0xCC000000);
        g.drawString(font, text, x, y, color);
    }
}
