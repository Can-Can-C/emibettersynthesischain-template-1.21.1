package com.cancan.emibettersynthesischain.client;

import java.util.List;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiRecipeFiller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
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
            // AE2 合成终端：自建"槽位 + 网络"合并库存（AE2 的 exposeNetworkInventoryToEmi 默认 false，
            // 此时 handler.getInventory 不含网络 → 树标红/预检看不到网络材料）
            EmiPlayerInventory ae2 = Ae2Support.mergedInventory(screen);
            if (ae2 != null) {
                return ae2;
            }
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
            // AE2 合成终端：同上，自建合并库存（链条预检 must 看到网络，才能走到 REFILL 拉料）
            EmiPlayerInventory ae2 = Ae2Support.mergedInventory(screen);
            if (ae2 != null) {
                return ae2;
            }
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

    /** 给定库存快照里该材料的总数量（标签匹配任一成员；组件感知）。
     *  饱和加法：异常大数量（如 AE2 无限存储，已在 mergedInventory 钳制）也不溢出为负数。
     *  Productive Bees：蜜蜂品种（虚拟 EmiStack）与蜂笼物品（同品种）等价计数。 */
    public static long count(EmiPlayerInventory inv, EmiIngredient content) {
        long total = 0;
        for (EmiStack merged : inv.inventory.values()) {
            ItemStack item = merged.getItemStack();
            if (item.isEmpty()) {
                continue;
            }
            for (EmiStack member : content.getEmiStacks()) {
                ItemStack m = member.getItemStack();
                if (m == null || m.isEmpty()) {
                    // Productive Bees 蜜蜂品种（BeeEmiStack getItemStack 空）→ 匹配蜂笼物品（同品种）
                    net.minecraft.resources.Identifier beeType = ProductiveBeesSupport.beeTypeOf(member);
                    if (beeType != null && beeType.equals(ProductiveBeesSupport.beeTypeOfStack(item))) {
                        long add = merged.getAmount();
                        if (add > 0) {
                            total = (total > Long.MAX_VALUE - add) ? Long.MAX_VALUE : total + add;
                        }
                        break;
                    }
                    continue;
                }
                if (ItemStack.isSameItemSameComponents(m, item)) {
                    long add = merged.getAmount();
                    if (add > 0) {
                        total = (total > Long.MAX_VALUE - add) ? Long.MAX_VALUE : total + add;
                    }
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
