package com.cancan.emibettersynthesischain.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * AE2（应用能源 2）合成终端取产物支持（可选 mod，零硬依赖）。
 *
 * <p>AE2 合成终端的结果槽是 {@code CraftingTermSlot}，其 {@code mayPickup()} **恒返回 false**，
 * 因此 vanilla 点击包（QUICK_MOVE/PICKUP）与 EMI {@code handler.craft}（AE2 只实现放料
 * transferRecipe）都无法取走产物。唯一途径是 AE2 自己的 action 机制：客户端发
 * {@code InventoryActionPacket} → 服务端 {@code AEBaseMenu.doAction} →
 * {@code CraftingTermSlot.doClick}（等价于玩家左键点击结果槽：消耗合成格材料，
 * 网络有料时等价补充，产物进玩家光标）。</p>
 *
 * <p>AE2 未安装时本类所有方法立即返回 false/无操作（方法体引用 AE2 类，类加载惰性，
 * 门禁短路后不会触发 NoClassDefFoundError）。</p>
 */
public final class Ae2Support {
    private Ae2Support() {
    }

    /** AE2 是否已加载（mod id {@code ae2}）。 */
    public static boolean isLoaded() {
        return ModList.get().isLoaded("ae2");
    }

    /** 当前菜单是否为 AE2 合成终端（含无线合成终端，其继承 CraftingTermMenu）。 */
    public static boolean isCraftingTermMenu(AbstractContainerMenu menu) {
        if (!isLoaded() || menu == null) {
            return false;
        }
        return menu instanceof appeng.menu.me.items.CraftingTermMenu;
    }

    /** 该槽是否为 AE2 合成终端结果槽（CraftingTermSlot）。 */
    public static boolean isCraftingTermSlot(Slot slot) {
        if (!isLoaded() || slot == null) {
            return false;
        }
        return slot instanceof appeng.menu.slot.CraftingTermSlot;
    }

    /**
     * 通过 AE2 action 机制取走合成终端结果槽产物（等价于玩家左键点击结果槽，合成一份到光标）。
     *
     * @param menu      当前合成终端菜单
     * @param slotIndex 结果槽在 {@code menu.slots} 中的 index
     */
    public static void takeOutput(AbstractContainerMenu menu, int slotIndex) {
        if (!isLoaded() || menu == null || slotIndex < 0) {
            return;
        }
        try {
            PacketDistributor.sendToServer(new appeng.core.network.serverbound.InventoryActionPacket(
                    appeng.helpers.InventoryAction.CRAFT_ITEM, slotIndex, 0L));
        } catch (Exception t) {
            com.cancan.emibettersynthesischain.EMIBettersynthesischain.LOGGER.warn(
                    "EBS AE2 takeOutput failed: {}", t.toString());
        }
    }

    /**
     * 清空合成终端合成格：AE2 的 CRAFT_ITEM 合成后会把**网络等价材料补回合成格**（网络借料设计），
     * 不清空会导致"合成后重新填充"（配方材料无法消耗、残留继续被 clientFill 清格又回背包）。
     *
     * <p>逐格发 vanilla QUICK_MOVE 点击（材料经 AEBaseMenu.quickMoveStack 移回玩家背包）：
     * 走 {@code CraftingMatrixSlot.remove} → {@code menu.slotsChanged} → 结果槽同步清空。
     * 不能用 {@code clearToPlayerInventory()}：它用 {@code setItemDirect} 绕过槽直接操作
     * InternalInventory，不触发 slotsChanged，**结果槽会残留上一个产物**（"卡一个东西在产物框"）。</p>
     */
    public static void clearCraftingGrid(AbstractContainerMenu menu) {
        if (!isLoaded() || menu == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu != menu) {
            return;
        }
        try {
            if (menu instanceof appeng.menu.me.items.CraftingTermMenu) {
                for (int i = 0; i < menu.slots.size(); i++) {
                    Slot s = menu.slots.get(i);
                    if (s != null && s.hasItem() && s instanceof appeng.menu.slot.CraftingMatrixSlot) {
                        try {
                            mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0,
                                    ClickType.QUICK_MOVE, mc.player);
                        } catch (Exception t) {
                            com.cancan.emibettersynthesischain.EMIBettersynthesischain.LOGGER.warn(
                                    "EBS AE2 clearCraftingGrid slot {} failed: {}", i, t.toString());
                        }
                    }
                }
            }
        } catch (Exception t) {
            com.cancan.emibettersynthesischain.EMIBettersynthesischain.LOGGER.warn(
                    "EBS AE2 clearCraftingGrid failed: {}", t.toString());
        }
    }
}
