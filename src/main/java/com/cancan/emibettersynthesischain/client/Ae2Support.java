package com.cancan.emibettersynthesischain.client;

import java.util.ArrayList;
import java.util.List;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiRecipeFiller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
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
    /**
     * 网络条目数量**防溢出**上限（Long.MAX_VALUE/4）：AE2 无限/创造存储条目可能接近 Long.MAX_VALUE，
     * 直接进入 EmiPlayerInventory 合并（同 key 数量相加）会 long 溢出成负数。
     * 钳制仅防溢出（阈值极大，正常真实数量不受影响；"足够"判定更不受影响）。
     */
    private static final long MAX_REASONABLE_STORED = Long.MAX_VALUE / 4;

    private Ae2Support() {
    }

    /** AE2 是否已加载（mod id {@code ae2}）。 */
    public static boolean isLoaded() {
        return ModList.get().isLoaded("ae2");
    }

    /**
     * AE 网络存储的轻量签名（供树缓存刷新检测）。
     *
     * <p>{@code InternalHelperImpl.maybeRefreshOnInventoryChange} 只用玩家背包 36 格哈希判断
     * "库存变了要重建树"——**网络存储变化（且玩家背包没动）检测不到**，树标红/可合成判定会
     * 停留在旧快照（"AE 里存储物读取不准"根因之一）。本方法对网络条目（what + amount）做
     * 组合哈希，ME 终端（含合成终端）打开时每 5 tick 参与刷新判定；非 ME 终端返回 0（不干扰）。</p>
     *
     * @return 网络签名；AE2 未装 / 非 ME 终端 / 异常 → 0
     */
    public static long networkSignature() {
        if (!isLoaded()) {
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.containerMenu instanceof appeng.menu.me.common.MEStorageMenu menu)) {
            return 0;
        }
        try {
            appeng.menu.me.common.IClientRepo repo =
                    ((appeng.menu.me.common.MEStorageMenu) menu).getClientRepo();
            if (repo == null) {
                return 0;
            }
            long h = 0;
            for (appeng.menu.me.common.GridInventoryEntry e : repo.getAllEntries()) {
                // getWhat() = GenericStack（what + amount 参与 hashCode → 数量/种类变化都会反映）
                h = h * 31 + e.getWhat().hashCode();
            }
            return h;
        } catch (Exception t) {
            return 0;
        }
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
     * 通过 AE2 action 机制取走合成终端结果槽产物。
     *
     * <p>{@code CRAFT_ITEM} = 玩家左键点击结果槽：合成**一份**到光标（链条逐步合成 / 中间产物攒料用，
     * 产物先到光标再由链条放回背包）。
     * {@code CRAFT_SHIFT} = 玩家 shift 点击结果槽：服务端**连续合成到一组**（maxStack 个），材料从
     * **网络自动补**（合成格等价材料），产物**直接进玩家背包**（不经光标）——批量合成目标用，
     * 免客户端逐步点击、无光标残留；网络材料不足时服务端合到材料耗尽自动停（合成实际数量）。</p>
     *
     * @param menu       当前合成终端菜单
     * @param slotIndex  结果槽在 {@code menu.slots} 中的 index
     * @param shiftBatch true = CRAFT_SHIFT（合成一组直接进背包）；false = CRAFT_ITEM（一份到光标）
     */
    public static void takeOutput(AbstractContainerMenu menu, int slotIndex, boolean shiftBatch) {
        if (!isLoaded() || menu == null || slotIndex < 0) {
            return;
        }
        try {
            PacketDistributor.sendToServer(new appeng.core.network.serverbound.InventoryActionPacket(
                    shiftBatch ? appeng.helpers.InventoryAction.CRAFT_SHIFT : appeng.helpers.InventoryAction.CRAFT_ITEM,
                    slotIndex, 0L));
        } catch (Exception t) {
            com.cancan.emibettersynthesischain.EMIBettersynthesischain.LOGGER.warn(
                    "EBS AE2 takeOutput failed: {}", t.toString());
        }
    }

    /**
     * 玩家库存槽（背包 + 快捷栏）：直接遍历 {@code menu.slots} 排除合成格/结果槽。
     *
     * <p>为什么不走 AE2 handler 的 {@code getInputSources}：javap 实证 AE2 19.2.17 的
     * {@code AEBaseMenu.getSlots(SlotSemantic)} 返回 {@code slotsBySemantic.get(semantic)}，
     * 而合成终端的玩家背包槽**未注册进该语义表**（实测返回 0 个槽）→ handler 的
     * inputSources / getInventory 里**只有网络没有背包**（树标红、放回槽查找都缺背包）。
     * 直接遍历标准 {@code menu.slots}（vanilla addSlot 必含背包槽）绕过该问题。</p>
     *
     * @return 玩家库存槽列表；AE2 未装 / 异常 → null（调用方回退原逻辑）
     */
    public static List<Slot> playerSlots(AbstractContainerMenu menu) {
        if (!isLoaded() || menu == null) {
            return null;
        }
        try {
            List<Slot> out = new ArrayList<>();
            for (Slot s : menu.slots) {
                if (s != null && !(s instanceof appeng.menu.slot.CraftingMatrixSlot)
                        && !(s instanceof appeng.menu.slot.CraftingTermSlot)) {
                    out.add(s);
                }
            }
            return out;
        } catch (Exception t) {
            return null;
        }
    }

    /**
     * 自建"玩家背包槽位 + AE2 网络存储"合并库存（EMI {@link EmiPlayerInventory}）。
     *
     * <p>背景（javap 实证 AE2 19.2.17）：AE2 的 {@code AbstractRecipeHandler.getInventory}
     * 只有在配置 {@code exposeNetworkInventoryToEmi=true}（**默认 false**，注释还警告可能
     * 引起性能问题）时才把网络条目并入；默认关闭时 handler 的库存只有玩家背包槽 → 树标红
     * 与链条预检都看不到网络材料。本方法**不依赖该配置**，始终自建"槽位（inputSources）
     * + 网络（getClientRepo 条目）"合并视图，行为与 AE2 配置开启时一致。</p>
     *
     * <p>槽位部分用 EMI handler 的 {@code getInputSources}（AE2 = 背包 + 快捷栏 + 合成格，
     * 与 AE2 原 getInventory 的 default 实现一致）；网络部分用 AE2 自己的
     * {@code EmiStackHelper.toEmiStack(GenericStack)} 转换（组件保真、数量无 64 上限）。</p>
     *
     * @param screen 当前容器界面（须为 AE2 ME 终端——普通存储/合成终端等，否则返回 null 由调用方回退）
     * @return 合并库存；非 AE2 终端 / 构建失败 → null
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static EmiPlayerInventory mergedInventory(AbstractContainerScreen<?> screen) {
        if (!isLoaded() || screen == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        AbstractContainerMenu menu = screen.getMenu();
        // 所有 ME 终端（普通存储终端 / 合成终端 / 无线等，CraftingTermMenu extends MEStorageMenu）：
        // 树/预检都能看到网络存储；非 ME 终端返回 null 由调用方回退原逻辑
        if (!(menu instanceof appeng.menu.me.common.MEStorageMenu)) {
            return null;
        }
        try {
            List<EmiStack> stacks = new ArrayList<>();
            // 1) 槽位（玩家背包 + 快捷栏）：直接遍历 menu.slots 排除合成格/结果槽
            //    （AE2 handler getInputSources 依赖 slotsBySemantic，实测空 → 会漏掉背包）
            List<Slot> playerSlots = playerSlots(menu);
            if (playerSlots != null) {
                for (Slot s : playerSlots) {
                    if (s.hasItem()) {
                        stacks.add(EmiStack.of(s.getItem()));
                    }
                }
            }
            // 2) 网络存储条目（storedAmount > 0）
            appeng.menu.me.common.IClientRepo repo =
                    ((appeng.menu.me.common.MEStorageMenu) menu).getClientRepo();
            if (repo != null) {
                for (appeng.menu.me.common.GridInventoryEntry e : repo.getAllEntries()) {
                    long stored = e.getStoredAmount();
                    if (stored <= 0) {
                        continue;
                    }
                    // 防溢出：AE2 无限/创造模式存储条目可能接近 Long.MAX_VALUE，与普通数量在
                    // EmiPlayerInventory 合并（数量相加）时会 long 溢出成负数 → 误判材料不足/显示负数。
                    // 钳制仅防溢出（阈值极大，正常真实数量不受影响）。
                    long capped = Math.min(stored, MAX_REASONABLE_STORED);
                    EmiStack es = appeng.integration.modules.emi.EmiStackHelper.toEmiStack(
                            new appeng.api.stacks.GenericStack(e.getWhat(), capped));
                    if (es != null) {
                        stacks.add(es);
                    }
                }
            }
            return new EmiPlayerInventory(stacks);
        } catch (Exception t) {
            com.cancan.emibettersynthesischain.EMIBettersynthesischain.LOGGER.warn(
                    "EBS AE2 mergedInventory failed: {}", t.toString());
            return null;
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
