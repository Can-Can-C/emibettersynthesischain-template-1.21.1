package com.cancan.emibettersynthesischain.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cancan.emibettersynthesischain.client.TreeAddFlag;

import dev.emi.emi.bom.BoM;

/**
 * 记录 {@code BoM.setGoal} 被调用，供 {@code EmiApiMixin} 区分"添加"与"切换"。
 * 注入点：EMI 1.1.24 {@code dev.emi.emi.bom.BoM#setGoal(EmiRecipe)}。
 */
@Mixin(BoM.class)
public abstract class BoMFlagMixin {
    @Inject(method = "setGoal", at = @At("HEAD"))
    private static void ebs$markSetGoal(CallbackInfo ci) {
        TreeAddFlag.markSetGoal();
    }
}
