package com.cancan.emibettersynthesischain.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cancan.emibettersynthesischain.client.IEmiInternal;
import com.cancan.emibettersynthesischain.client.InternalHelperImpl;
import com.cancan.emibettersynthesischain.client.TreeData;
import com.cancan.emibettersynthesischain.client.TreeMode;
import com.cancan.emibettersynthesischain.client.TreeRenderer;

import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 收藏页面板渲染合成树：树模式开启且该面板为收藏页时，跳过原物品网格，绘制合成树。
 * 注入点：EMI 1.1.24 {@code EmiScreenManager$SidebarPanel#render(EmiDrawContext,int,int,float)}。
 */
@Mixin(EmiScreenManager.SidebarPanel.class)
public abstract class SidebarPanelMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ebs$renderTree(EmiDrawContext draw, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!TreeMode.isActive()) {
            return;
        }
        IEmiInternal helper = InternalHelperImpl.INSTANCE;
        if (!helper.isFavoritesPanel(this)) {
            return;
        }
        GuiGraphics g = draw.raw();
        Bounds bounds = helper.getPanelBounds(this);
        List<TreeData> trees = helper.buildTrees();
        TreeRenderer.render(g, bounds.x(), bounds.y(), bounds.width(), bounds.height(), trees, mouseX, mouseY);
        ci.cancel();
    }
}
