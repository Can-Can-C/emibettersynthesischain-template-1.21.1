package com.cancan.emibettersynthesischain.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cancan.emibettersynthesischain.client.InternalHelperImpl;
import com.cancan.emibettersynthesischain.client.TreeManager;
import com.cancan.emibettersynthesischain.client.TreeMode;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.widget.RecipeButtonWidget;
import dev.emi.emi.widget.RecipeTreeButtonWidget;
import net.minecraft.world.item.ItemStack;

/**
 * 配方页"配方树"按钮：
 * <ul>
 *   <li><b>激活态</b>（复用 EMI 原生纹理机制，同"设为默认配方"按钮）：配方产物已在合成树中
 *       时，{@code getTextureOffset} 返回 基础+36（激活纹理行），不画任何自定义高亮。</li>
 *   <li><b>点击</b>切换加入/删除该配方的产物。</li>
 * </ul>
 * 注入点：EMI 1.1.24 {@code dev.emi.emi.widget.RecipeTreeButtonWidget}。
 * 注意：recipe 是父类受保护字段，@Shadow 找不到，经 {@link RecipeButtonWidgetAccessor} 访问。
 */
@Mixin(RecipeTreeButtonWidget.class)
public abstract class RecipeTreeButtonWidgetMixin {
    @Inject(method = "getTextureOffset", at = @At("HEAD"), cancellable = true)
    private void ebs$textureOffset(int mouseX, int mouseY, CallbackInfoReturnable<Integer> cir) {
        RecipeButtonWidgetAccessor acc = (RecipeButtonWidgetAccessor) (Object) this;
        EmiRecipe recipe = acc.ebs$recipe();
        EmiStack output = recipe.getOutputs().isEmpty() ? null : recipe.getOutputs().get(0);
        if (output == null) {
            return;
        }
        ItemStack stack = output.getItemStack();
        if (stack == null || stack.isEmpty()) {
            // Productive Bees 蜜蜂等虚拟 EmiStack → 蜂笼物品（树按蜂笼持久化/判重）
            stack = com.cancan.emibettersynthesischain.client.ProductiveBeesSupport.toBeeCageStack(output);
        }
        if (stack == null || stack.isEmpty() || !TreeManager.INSTANCE.contains(stack)) {
            return; // 不在树中：走 EMI 原逻辑
        }
        // 基础偏移（悬停 +12 / 否则 0，EMI 1.1.24）+ 36 = 激活纹理（同"设为默认配方" FULL 态）
        int base = ((RecipeButtonWidget) (Object) this).getBounds().contains(mouseX, mouseY) ? 12 : 0;
        cir.setReturnValue(base + 36);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ebs$toggle(int mouseX, int mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        RecipeButtonWidgetAccessor acc = (RecipeButtonWidgetAccessor) (Object) this;
        EmiRecipe recipe = acc.ebs$recipe();
        EmiStack output = recipe.getOutputs().isEmpty() ? null : recipe.getOutputs().get(0);
        if (output == null) {
            com.cancan.emibettersynthesischain.EMIBettersynthesischain.LOGGER.info(
                    "EBS tree-btn: recipe {} has no outputs", recipe.getId());
            return;
        }
        ItemStack stack = output.getItemStack();
        if (stack == null || stack.isEmpty()) {
            // Productive Bees 蜜蜂等虚拟 EmiStack（getItemStack 空）→ 蜂笼物品（蜜蜂品种 NBT）
            stack = com.cancan.emibettersynthesischain.client.ProductiveBeesSupport.toBeeCageStack(output);
        }
        if (stack == null || stack.isEmpty()) {
            com.cancan.emibettersynthesischain.EMIBettersynthesischain.LOGGER.info(
                    "EBS tree-btn: output stack EMPTY for recipe {} (output emi={})", recipe.getId(), output);
            return;
        }
        if (TreeManager.INSTANCE.contains(stack)) {
            TreeManager.INSTANCE.remove(stack);
        } else {
            TreeManager.INSTANCE.add(stack, recipe.getId());
            // 确保配方树页面打开
            TreeMode.setActive(true);
            InternalHelperImpl.INSTANCE.widenFavoritesPanel();
            InternalHelperImpl.INSTANCE.focusFavoritesSidebar();
        }
        cir.setReturnValue(true);
    }
}
