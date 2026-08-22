package com.cancan.emibettersynthesischain.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cancan.emibettersynthesischain.EMIBettersynthesischain;
import com.cancan.emibettersynthesischain.client.TreeManager;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.BoMScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Q4: 在**原版 EM 合成树界面**（BoMScreen）左侧叠加"产物缩略条"。
 * 条目 = 已加入合成树集合（TreeManager）的各目标产物；点击某产物 → 设为 BoM 目标并重算当前树。
 *
 * <p>注入点：EMI 1.1.24 {@code dev.emi.emi.screen.BoMScreen#extractRenderState} 与 {@code mouseClicked}。</p>
 *
 * <p>配合 {@link EmiApiMixin} 的 Shift 分支（Shift+树按钮打开 BoMScreen）。</p>
 */
@Mixin(BoMScreen.class)
public abstract class BoMScreenMixin {
    @Unique
    private static final int SIDEBAR_W = 44;
    @Unique
    private static final int SLOT_H = 22;
    @Unique
    private static final int SLOT_PAD = 3;
    @Unique
    private int ebs$selected = -1;

    /** 渲染末尾：在界面左侧绘制产物缩略条。 */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void ebs$renderSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int top = 10;
        for (int i = 0; i < TreeManager.INSTANCE.size(); i++) {
            ItemStack stack = TreeManager.INSTANCE.getItem(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int x = SLOT_PAD;
            int y = top + i * SLOT_H;
            // 背景格
            g.fill(x, y, x + SIDEBAR_W, y + SLOT_H - SLOT_PAD, 0x44000000);
            if (i == ebs$selected) {
                g.fill(x + 1, y + 1, x + SIDEBAR_W - 1, y + SLOT_H - SLOT_PAD - 1, 0x66FFFFFF);
            }
            // 图标
            EmiDrawContext ctx = EmiDrawContext.wrap(g);
            ctx.drawStack(EmiStack.of(stack), x + 2, y + 2, EmiIngredient.RENDER_ICON);
        }
    }

    /** 点击处理：命中缩略条目 → 设为 BoM 目标并重算原版树；否则交回原逻辑。 */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ebs$clickSidebar(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        int idx = pickSidebarItem(mouseX, mouseY);
        if (idx >= 0) {
            select(idx);
            cir.setReturnValue(true);
        }
    }

    @Unique
    private int pickSidebarItem(double mx, double my) {
        int top = 10;
        for (int i = 0; i < TreeManager.INSTANCE.size(); i++) {
            ItemStack stack = TreeManager.INSTANCE.getItem(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int x = SLOT_PAD;
            int y = top + i * SLOT_H;
            if (mx >= x && mx < x + SIDEBAR_W && my >= y && my < y + SLOT_H - SLOT_PAD) {
                return i;
            }
        }
        return -1;
    }

    @Unique
    private void select(int idx) {
        ItemStack goal = TreeManager.INSTANCE.getItem(idx);
        if (goal == null || goal.isEmpty()) {
            return;
        }
        EmiRecipe recipe = resolveFor(goal, TreeManager.INSTANCE.getRecipeId(idx));
        if (recipe != null) {
            BoM.setGoal(recipe);
            ((BoMScreen) (Object) this).recalculateTree();
            ebs$selected = idx;
            EMIBettersynthesischain.LOGGER.info("EBS BoM screen selected tree #{}: {}", idx, goal);
        }
    }

    @Unique
    private static EmiRecipe resolveFor(ItemStack goal, Identifier recipeId) {
        if (recipeId != null) {
            try {
                EmiRecipeManager m = EmiApi.getRecipeManager();
                if (m != null) {
                    EmiRecipe r = m.getRecipe(recipeId);
                    if (r != null) {
                        return r;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        try {
            return BoM.getRecipe(EmiStack.of(goal));
        } catch (Exception e) {
            return null;
        }
    }
}
