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
import net.minecraft.world.item.ItemStack;

/**
 * 拦截 {@code EmiApi.viewRecipeTree()}：不再打开独立 BoM 界面，改为切换收藏页显示合成树。
 * 注入点：EMI 1.1.24 {@code dev.emi.emi.api.EmiApi#viewRecipeTree}。
 *
 * <p>行为：左下角树按钮（未先 setGoal）＝切换开关；EMI keybind/配方页按钮（刚 setGoal）＝
 * 只添加、不关闭页面（"没打开的打开，打开的不变"）。</p>
 */
@Mixin(EmiApi.class)
public abstract class EmiApiMixin {
    @Inject(method = "viewRecipeTree", at = @At("HEAD"), cancellable = true)
    private static void ebs$redirectViewRecipeTree(CallbackInfo ci) {
        IEmiInternal helper = InternalHelperImpl.INSTANCE;

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
