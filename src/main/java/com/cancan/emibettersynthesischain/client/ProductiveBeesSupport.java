package com.cancan.emibettersynthesischain.client;

import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * Productive Bees（实用蜜蜂）蜜蜂品种支持（可选 mod，零硬依赖）。
 *
 * <p>Productive Bees 的蜜蜂是"实体 + 数据驱动品种"（javap 实证：EMI 用自定义
 * {@code BeeEmiStack}，其 **没有 override getItemStack()** → 返回 EMPTY；品种 id 如
 * {@code productivebees:iron} 不是注册物品）。我们的合成树/持久化基于 ItemStack，
 * 无法直接持有蜜蜂。</p>
 *
 * <p>本类把蜜蜂品种 EmiStack **规范化为蜂笼物品标记**（{@code BeeCage} + {@code CUSTOM_DATA} 里仅写
 * {@code type=<品种>}）——树的目标/材料统一以蜂笼物品承载品种 id 用于持久化/匹配/计数
 * （显示层仍用原始蜜蜂 EmiStack）。**不伪造蜜蜂实体字段**（官方 {@code BeeCage} 装满需要
 * {@code entity}，但该值属 Productive Bees 内部细节——我们只把它当内部标记，不做推测性构造）。
 * 自动合成仍按"仅工作台配方"拦截（繁殖箱非工作台，不自动合成，符合"只要显示"）。</p>
 *
 * <p>Productive Bees 未安装时本类所有方法立即返回 false/null（方法体引用其类，类加载惰性，
 * 门禁短路后不会触发 NoClassDefFoundError）。</p>
 */
public final class ProductiveBeesSupport {
    private ProductiveBeesSupport() {
    }

    /** Productive Bees 是否已加载（mod id {@code productivebees}）。 */
    public static boolean isLoaded() {
        try {
            return net.neoforged.fml.ModList.get().isLoaded("productivebees");
        } catch (Exception e) {
            return false;
        }
    }

    /** 该 EmiStack 是否为 Productive Bees 的蜜蜂品种（自定义 BeeEmiStack）。 */
    public static boolean isBeeEmiStack(EmiStack stack) {
        if (!isLoaded() || stack == null) {
            return false;
        }
        try {
            return stack instanceof cy.jdkdigital.productivebees.compat.emi.BeeEmiStack;
        } catch (Exception e) {
            return false;
        }
    }

    /** 蜜蜂品种 id（如 {@code productivebees:iron}）；非蜜蜂 EmiStack → null。 */
    public static ResourceLocation beeTypeOf(EmiStack stack) {
        if (!isBeeEmiStack(stack)) {
            return null;
        }
        try {
            return stack.getId();
        } catch (Exception e) {
            return null;
        }
    }

    /** 蜂笼物品（注册 id {@code productivebees:bee_cage}）；未注册 → null。 */
    private static Item beeCageItem() {
        try {
            return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("productivebees", "bee_cage"));
        } catch (Exception e) {
            return null;
        }
    }

    /** 蜜蜂 EmiStack → 蜂笼物品标记（CUSTOM_DATA 仅含品种 type）；非蜜蜂 / 蜂笼未注册 → null。
     *  注意：只写 {@code type}（不伪造官方 {@code entity} 实体字段——那是 Productive Bees 内部细节），
     *  本物品仅作为树内部"品种 id 的持久化载体"，不对外表现为合法装满蜂笼。 */
    public static ItemStack toBeeCageStack(EmiStack stack) {
        ResourceLocation type = beeTypeOf(stack);
        if (type == null) {
            return null;
        }
        Item cage = beeCageItem();
        if (cage == null || cage == Items.AIR) {
            return null;
        }
        try {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", type.toString());
            ItemStack out = new ItemStack(cage);
            out.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /** 蜂笼物品标记是否承载 Productive Bees 品种（CUSTOM_DATA.type 非空）；是 → 品种 id；否 → null。 */
    public static ResourceLocation beeTypeOfStack(ItemStack stack) {
        if (!isLoaded() || stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            Item cage = beeCageItem();
            if (cage == null || stack.getItem() != cage) {
                return null;
            }
            CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
            if (cd == null) {
                return null;
            }
            String type = cd.getUnsafe().getString("type");
            if (type.isEmpty()) {
                return null;
            }
            return ResourceLocation.tryParse(type);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 蜜蜂品种显示名（与原版 EMI 的 {@code getTooltip()} 第一行一致）：调用 Productive Bees 的
     * {@code getName()}（translatable "productivebees.<品种>"，lang 决定名字）。非蜜蜂 → null。
     */
    public static net.minecraft.network.chat.Component beeDisplayName(EmiStack stack) {
        if (!isBeeEmiStack(stack)) {
            return null;
        }
        try {
            return ((cy.jdkdigital.productivebees.compat.emi.BeeEmiStack) stack).getName();
        } catch (Exception e) {
            return null;
        }
    }
}
