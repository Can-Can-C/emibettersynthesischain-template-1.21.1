package com.cancan.emibettersynthesischain.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.widget.RecipeButtonWidget;

/**
 * {@code RecipeButtonWidget} 私有/受保护成员 accessor（EMI 1.1.24）。
 * 通过"目标实例 cast 到本接口"调用；升级 EMI 版本时只需调整这里。
 */
@Mixin(RecipeButtonWidget.class)
public interface RecipeButtonWidgetAccessor {
    @Accessor("recipe")
    EmiRecipe ebs$recipe();

    @Accessor("x")
    int ebs$x();

    @Accessor("y")
    int ebs$y();
}
