package com.cancan.emibettersynthesischain.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cancan.emibettersynthesischain.client.MessageOverlay;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;

/**
 * 在容器屏渲染末尾自绘提示消息（如"合成成功"，显示在物品栏内顶部）。
 * 注入点：MC {@code AbstractContainerScreen#extractRenderState(GuiGraphicsExtractor,int,int,float)}。
 *
 * <p><b>26.1.2 实测</b>：物品栏屏（InventoryScreen）经 {@code AbstractRecipeBookScreen} 继承，
 * 而 {@code AbstractRecipeBookScreen.extractRenderState} **不调用**父类
 * {@code AbstractContainerScreen.extractRenderState}（改调 {@code extractContents}）→
 * 只注入 AbstractContainerScreen 时，物品栏里提示永不渲染。因此同时注入两个类的同名方法。</p>
 */
@Mixin({AbstractContainerScreen.class, AbstractRecipeBookScreen.class})
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void ebs$renderMessage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MessageOverlay.render(g);
    }
}