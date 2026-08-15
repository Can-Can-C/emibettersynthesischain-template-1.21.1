package com.cancan.emibettersynthesischain.client;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiRecipeFiller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

/**
 * 屏幕感知库存提供器：当前打开界面有 EMI handler 时用 `handler.getInventory(screen)`
 * （如 AE2 合成终端 = 网络 + 背包；工作台 = 背包）；否则回退玩家背包。
 * 供树标红（hasEnough/canObtain）与自动合成链条共用。
 */
public final class CraftInventory {
    private CraftInventory() {
    }

    /** 当前屏幕库存快照（无屏幕/无 handler → 玩家背包）。不依赖具体配方，供树检测使用。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static EmiPlayerInventory currentScreenInventory() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen instanceof AbstractContainerScreen<?> screen) {
            try {
                for (EmiRecipeHandler h : EmiRecipeFiller.getAllHandlers(screen)) {
                    if (h instanceof StandardRecipeHandler<?>) {
                        return h.getInventory(screen);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return EmiPlayerInventory.of(mc.player);
    }

    /** 当前屏幕库存快照（针对指定配方取首个支持的 handler，供合成链使用）。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static EmiPlayerInventory current(EmiRecipe recipe) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen instanceof AbstractContainerScreen<?> screen) {
            try {
                EmiRecipeHandler handler = EmiRecipeFiller.getFirstValidHandler(recipe, screen);
                if (handler != null) {
                    return handler.getInventory(screen);
                }
            } catch (Exception ignored) {
            }
        }
        return EmiPlayerInventory.of(mc.player);
    }

    /** 给定库存快照里该材料的总数量（标签匹配任一成员；组件感知）。 */
    public static long count(EmiPlayerInventory inv, EmiIngredient content) {
        long total = 0;
        for (EmiStack merged : inv.inventory.values()) {
            ItemStack item = merged.getItemStack();
            if (item.isEmpty()) {
                continue;
            }
            for (EmiStack member : content.getEmiStacks()) {
                ItemStack m = member.getItemStack();
                if (!m.isEmpty() && ItemStack.isSameItemSameComponents(m, item)) {
                    total += merged.getAmount();
                    break;
                }
            }
        }
        return total;
    }

    /** 当前屏幕库存里该材料的总数量（树检测用）。 */
    public static long count(EmiIngredient content) {
        return count(currentScreenInventory(), content);
    }

    /** 指定配方视角下该材料的总数量（合成链用）。 */
    public static long count(EmiRecipe recipe, EmiIngredient content) {
        return count(current(recipe), content);
    }

    public static boolean hasEnough(EmiIngredient content, long amount) {
        return count(content) >= amount;
    }

    /** 当前屏幕库存能否直接合成该配方（直接输入）。 */
    public static boolean canCraft(EmiRecipe recipe) {
        try {
            return current(recipe).canCraft(recipe);
        } catch (Exception e) {
            return false;
        }
    }
}
