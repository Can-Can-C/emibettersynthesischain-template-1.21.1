package com.cancan.emibettersynthesischain.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cancan.emibettersynthesischain.client.AutoCraftClient;
import com.cancan.emibettersynthesischain.client.IEmiInternal;
import com.cancan.emibettersynthesischain.client.InternalHelperImpl;
import com.cancan.emibettersynthesischain.client.TreeMode;
import com.cancan.emibettersynthesischain.client.TreeRenderer;

import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * 滚轮路由 + 树区悬停屏蔽。
 * 注入点：EMI 1.1.24 {@code EmiScreenManager#mouseScrolled(double,double,double)}
 * 与 {@code getHoveredStack(int,int,boolean,boolean)}。
 */
@Mixin(EmiScreenManager.class)
public abstract class EmiScreenManagerMixin {
    private static boolean ctrlDown() {
                return org.lwjgl.glfw.GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }
    private static boolean shiftHeld() {
                return org.lwjgl.glfw.GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }
    @Inject(method = "mouseScrolled(DDD)Z", at = @At("HEAD"), cancellable = true)
    private static void ebs$mouseScrolled(double mouseX, double mouseY, double amount,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TreeMode.isActive()) {
            return;
        }
        IEmiInternal helper = InternalHelperImpl.INSTANCE;
        Bounds bounds = helper.getFavoritesPanelBounds();
        if (bounds == null) {
            return;
        }
        if (!bounds.contains((int) mouseX, (int) mouseY)) {
            return; // 不在收藏面板上：走 EMI 原逻辑（不滚动树）
        }
        // 悬停最终产物：滚轮调节"单次合成数量"（Shift ±10、Ctrl 翻倍/减半），消费事件不滚动树
        TreeRenderer.Hit hit = TreeRenderer.hitTest(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                helper.buildTrees(), (int) mouseX, (int) mouseY);
        if (hit != null && hit.isGoal()) {
            int dir = amount > 0 ? 1 : -1;
            int mods = 0;
            if (ctrlDown()) {
                mods |= GLFW.GLFW_MOD_CONTROL;
            } else if (shiftHeld()) {
                mods |= GLFW.GLFW_MOD_SHIFT;
            }
            AutoCraftClient.adjustAmount(hit.treeIndex(), dir, mods); // 只调节悬停的那棵树
            cir.setReturnValue(true);
            return;
        }
        TreeMode.addScroll((int) -amount);
        cir.setReturnValue(true); // 消费事件，阻止 EMI 页面/原版再滚动
    }

    @Inject(method = "getHoveredStack(IIZZ)Ldev/emi/emi/api/stack/EmiStackInteraction;",
            at = @At("HEAD"), cancellable = true)
    private static void ebs$noFavoritesHover(int mouseX, int mouseY, boolean click, boolean tooltip,
            CallbackInfoReturnable<EmiStackInteraction> cir) {
        // 树模式：鼠标在收藏面板（树区）上时，EMI 不再检测到收藏物品，
        // 避免点穿/点击到下层收藏页物品、以及悬停 tooltip 重叠
        if (!TreeMode.isActive()) {
            return;
        }
        IEmiInternal helper = InternalHelperImpl.INSTANCE;
        Bounds bounds = helper.getFavoritesPanelBounds();
        if (bounds == null) {
            return;
        }
        if (bounds.contains(mouseX, mouseY)) {
            cir.setReturnValue(EmiStackInteraction.EMPTY);
        }
    }

    /**
     * 树模式点击屏蔽：鼠标在收藏面板（树区）上时，EMI 的 mouseClicked 一律消费——
     * 防止点击"点穿"到下层收藏夹物品（悬停已用 getHoveredStack EMPTY 屏蔽，点击需单独处理）。
     * 树节点自身交互走平台层 InputEvent.MouseButton，不依赖 EMI 返回值。
     */
    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void ebs$blockFavoritesClick(net.minecraft.client.input.MouseButtonEvent e,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TreeMode.isActive()) {
            return;
        }
        IEmiInternal helper = InternalHelperImpl.INSTANCE;
        Bounds bounds = helper.getFavoritesPanelBounds();
        if (bounds == null) {
            return;
        }
        if (bounds.contains((int) e.x(), (int) e.y())) {
            cir.setReturnValue(true);
        }
    }
}
