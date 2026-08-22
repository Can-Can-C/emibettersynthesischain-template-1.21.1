package com.cancan.emibettersynthesischain.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cancan.emibettersynthesischain.client.MessageOverlay;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * 在容器屏渲染末尾自绘提示消息（如"合成成功"，显示在物品栏内顶部）。
 * 注入点：MC {@code AbstractContainerScreen#extractRenderState(GuiGraphics,int,int,float)}。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void ebs$renderMessage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MessageOverlay.render(g);
    }
}
