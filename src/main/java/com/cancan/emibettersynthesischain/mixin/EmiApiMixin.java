package com.cancan.emibettersynthesischain.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cancan.emibettersynthesischain.EMIBettersynthesischain;
import com.cancan.emibettersynthesischain.client.IEmiInternal;
import com.cancan.emibettersynthesischain.client.InternalHelperImpl;
import com.cancan.emibettersynthesischain.client.TreeAddFlag;
import com.cancan.emibettersynthesischain.client.TreeManager;
import com.cancan.emibettersynthesischain.client.TreeMode;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.screen.BoMScreen;
import dev.emi.emi.screen.RecipeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

/**
 * 拦截 {@code EmiApi.viewRecipeTree()}：不再打开独立 BoM 界面，改为切换收藏页显示合成树。
 * 注入点：EMI 1.1.24 {@code dev.emi.emi.api.EmiApi#viewRecipeTree}。
 *
 * <p>行为：左下角树按钮（未先 setGoal）＝切换开关；EMI keybind/配方页按钮（刚 setGoal）＝
 * 只添加、不关闭页面（"没打开的打开，打开的不变"）。</p>
 *
 * <p>Q4：按住 Shift 点树按钮＝打开**原版 EM 合成树界面**（BoMScreen，不切收藏页树、不添加），
 * 在其上叠加自写产物缩略条由 {@code BoMScreenMixin} 负责。</p>
 */
@Mixin(EmiApi.class)
public abstract class EmiApiMixin {
    @Inject(method = "viewRecipeTree", at = @At("HEAD"), cancellable = true)
    private static void ebs$redirectViewRecipeTree(CallbackInfo ci) {
        IEmiInternal helper = InternalHelperImpl.INSTANCE;

        // Q4: Shift+树按钮 → 打开原版 EM 合成树界面（BoMScreen），不切收藏页树、不添加
        if (Screen.hasShiftDown()) {
            Minecraft mc = Minecraft.getInstance();
            Screen cur = mc.screen;
            AbstractContainerScreen<?> cs = null;
            if (cur instanceof AbstractContainerScreen<?> container) {
                cs = container;
            } else if (cur instanceof RecipeScreen rs) {
                cs = rs.old;
            }
            if (cs != null) {
                mc.setScreen(new BoMScreen(cs));
            }
            ci.cancel();
            return;
        }

        // 左下角树按钮再次点击且不是"添加"动作 → 关闭树视图
        if (TreeMode.isActive() && !TreeAddFlag.wasJustSetGoal()) {
            TreeMode.setActive(false);
            helper.restoreFavoritesPanel();
            ci.cancel();
            return;
        }

        // 添加动作：把悬停物品加入合成树集合，并确保页面打开
        ItemStack hovered = helper.getAddTarget();
        EMIBettersynthesischain.LOGGER.info("EBS viewRecipeTree intercepted, add target: {}",
                hovered.isEmpty() ? "EMPTY" : hovered.toString());
        if (!hovered.isEmpty()) {
            TreeManager.INSTANCE.add(hovered, helper.getGoalRecipeId());
        }

        TreeMode.setActive(true);
        helper.widenFavoritesPanel();
        helper.focusFavoritesSidebar();
        ci.cancel();
    }
}
