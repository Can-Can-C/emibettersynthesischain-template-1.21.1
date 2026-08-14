package com.cancan.emibettersynthesischain.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cancan.emibettersynthesischain.client.IEmiInternal;
import com.cancan.emibettersynthesischain.client.InternalHelperImpl;
import com.cancan.emibettersynthesischain.client.TreeMode;

import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.screen.EmiScreenManager;

/**
 * 滚轮路由 + 树区悬停屏蔽。
 * 注入点：EMI 1.1.24 {@code EmiScreenManager#mouseScrolled(double,double,double)}
 * 与 {@code getHoveredStack(int,int,boolean,boolean)}。
 */
@Mixin(EmiScreenManager.class)
public abstract class EmiScreenManagerMixin {
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
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
}
