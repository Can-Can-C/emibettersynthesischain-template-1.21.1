package com.cancan.emibettersynthesischain.client;

import java.util.List;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.fml.ModList;

/**
 * AE2（应用能源 2）合成终端支持 — Forge 1.20.1 分支【占位实现】。
 *
 * <p><b>11.5 前临时占位</b>：1.20.1 的 AE2 jar 未提供，本类保持与主线相同的公开 API
 * （门禁结构不变），但方法体不再引用 AE2 类——一律返回 null/false/无操作，
 * 因此合成终端取产物/网络拉料功能在 11.5 换入真实实现前暂不可用（未装 AE2 环境无感知）。</p>
 */
public final class Ae2Support {
    private Ae2Support() {
    }

    /** AE2 是否已加载（mod id {@code ae2}）。 */
    public static boolean isLoaded() {
        return ModList.get().isLoaded("ae2");
    }

    /** 当前菜单是否为 AE2 合成终端（占位：未实现 → false）。 */
    public static boolean isCraftingTermMenu(AbstractContainerMenu menu) {
        return false;
    }

    /** 该槽是否为 AE2 合成终端结果槽（占位：未实现 → false）。 */
    public static boolean isCraftingTermSlot(Slot slot) {
        return false;
    }

    /** AE2 取产物（占位：未实现 → 无操作）。 */
    public static void takeOutput(AbstractContainerMenu menu, int slotIndex, boolean shiftBatch) {
    }

    /** 玩家库存槽（占位：未实现 → null）。 */
    public static List<Slot> playerSlots(AbstractContainerMenu menu) {
        return null;
    }

    /** AE 网络存储合并库存（占位：未实现 → null）。 */
    public static EmiPlayerInventory mergedInventory(AbstractContainerScreen<?> screen) {
        return null;
    }

    /** AE 网络存储签名（占位：未实现 → 0）。 */
    public static long networkSignature() {
        return 0L;
    }

    /** 清空合成格（占位：未实现 → 无操作）。 */
    public static void clearCraftingGrid(AbstractContainerMenu menu) {
    }
}