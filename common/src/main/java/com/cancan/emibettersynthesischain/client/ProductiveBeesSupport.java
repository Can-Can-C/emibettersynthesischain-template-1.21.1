package com.cancan.emibettersynthesischain.client;

import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Productive Bees 蜜蜂配方显示支持 — Forge 1.20.1 分支【占位实现】。
 *
 * <p><b>11.5 前临时占位</b>：1.20.1 的 Productive Bees jar 未提供，本类保持与主线相同的公开 API
 * （门禁结构不变），但方法体不再引用 PB 类——一律返回 null/false/EMPTY，
 * 蜜蜂树显示在 11.5 换入真实实现前退化为普通物品显示（未装 PB 环境无感知）。</p>
 */
public final class ProductiveBeesSupport {
    private ProductiveBeesSupport() {
    }

    /** 是否为蜜蜂品种 EmiStack（占位：未实现 → false）。 */
    public static boolean isBeeEmiStack(EmiStack stack) {
        return false;
    }

    /** EmiStack（蜜蜂品种/蜂笼）的品种 ResourceLocation（占位：未实现 → null）。 */
    public static ResourceLocation beeTypeOf(EmiStack stack) {
        return null;
    }

    /** 蜂笼物品（带内部品种标记）的品种 ResourceLocation（占位：未实现 → null）。 */
    public static ResourceLocation beeTypeOfStack(ItemStack stack) {
        return null;
    }

    /** 蜜蜂品种显示名（官方翻译，占位：未实现 → null）。 */
    public static Component beeDisplayName(EmiStack stack) {
        return null;
    }

    /** 蜜蜂 EmiStack → 蜂笼物品标记（占位：未实现 → EMPTY）。 */
    public static ItemStack toBeeCageStack(EmiStack stack) {
        return ItemStack.EMPTY;
    }

    /** PB 是否已加载（mod id {@code productivebees}）。 */
    public static boolean isLoaded() {
        return net.minecraftforge.fml.ModList.get().isLoaded("productivebees");
    }
}