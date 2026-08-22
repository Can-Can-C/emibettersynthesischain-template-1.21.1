package com.cancan.emibettersynthesischain.client;

import net.minecraft.world.item.ItemStack;

/**
 * 1.20.1（无 DataComponents）物品堆栈兼容工具。
 *
 * <p>主线 1.21.1 用 {@code ItemStack.hashItemAndComponents()}（组件感知哈希）与
 * {@code isSameItemSameComponents}（同 item 同组件）；1.20.1 等价物是 item + NBT 的组合
 * （{@code hashStack} / {@code sameStack}），跨版本移植时统一走本类，保持"组件感知"语义。</p>
 */
public final class StackCompat {
    private StackCompat() {
    }

    /** 组件感知哈希（1.20.1：item + NBT 组合；无 NBT 与空 NBT 等价）。 */
    public static int hashStack(ItemStack s) {
        int h = s.getItem().hashCode();
        if (s.hasTag()) {
            h = h * 31 + s.getTag().hashCode();
        }
        return h;
    }

    /** 同 item 且同 NBT（等价 1.21.1 的 {@code isSameItemSameComponents}）。 */
    public static boolean sameStack(ItemStack a, ItemStack b) {
        return ItemStack.isSameItem(a, b) && ItemStack.isSameItemSameTags(a, b);
    }
}