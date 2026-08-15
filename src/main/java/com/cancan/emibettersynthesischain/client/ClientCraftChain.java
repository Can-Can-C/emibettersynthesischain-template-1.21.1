package com.cancan.emibettersynthesischain.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.cancan.emibettersynthesischain.Config;

import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.registry.EmiRecipeFiller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * 纯客户端自动合成链：tick 驱动，在当前打开界面上用 vanilla 点击包（模拟玩家点击）一步步合成。
 *
 * <p>放料复用 EMI {@link EmiRecipeFiller#clientFill}（{@code Destination.NONE}）一次性放置全部材料，
 * 光标受控、形状感知；取结果后清空光标。V=得最终结果即停 / Shift+V=连续 / Ctrl+V=强制（缺料跳过）。</p>
 */
public final class ClientCraftChain {
    private static final int MAX_STEPS = 128;
    private static final int RESULT_RETRY = 10;
    private static ClientCraftChain active;

    private final EmiRecipe goalRecipe;
    private final boolean repeat;
    private final boolean force;
    private final StandardRecipeHandler handler;
    private final AbstractContainerScreen<?> screen;
    private final AbstractContainerMenu menu;
    private final Set<EmiRecipe> ancestors = new HashSet<>();

    private EmiRecipe currentRecipe;
    private boolean currentIsGoal;
    private int countdown;
    private int retries;
    private int steps;
    private Phase phase;
    private boolean done;
    /** 取走产物验证：记录本次要取的结果与取走前的背包数量（模组界面 output.hasItem 可能不刷新）。 */
    private ItemStack pendingResult;
    private long pendingBefore;

    private enum Phase { SHOW, GAP }

    private ClientCraftChain(EmiRecipe goalRecipe, boolean repeat, boolean force, StandardRecipeHandler handler,
            AbstractContainerScreen<?> screen, AbstractContainerMenu menu) {
        this.goalRecipe = goalRecipe;
        this.repeat = repeat;
        this.force = force;
        this.handler = handler;
        this.screen = screen;
        this.menu = menu;
    }

    /** 尝试启动；已进行中 / 无标准 handler / 光标非空 → 返回 false。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean start(EmiRecipe goalRecipe, boolean repeat, boolean force) {
        if (active != null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.screen instanceof AbstractContainerScreen<?> screen)
                || !mc.player.containerMenu.getCarried().isEmpty()) {
            return false;
        }
        EmiRecipeHandler handler = EmiRecipeFiller.getFirstValidHandler(goalRecipe, screen);
        if (!(handler instanceof StandardRecipeHandler std)) {
            return false;
        }
        active = new ClientCraftChain(goalRecipe, repeat, force, std, screen, mc.player.containerMenu);
        active.placeStep();
        if (active.done) {
            active = null;
            return false;
        }
        return true;
    }

    /** 每客户端 tick 调用一次（EMIBettersynthesischainClient 注册）。 */
    public static void tick() {
        if (active != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                active = null;
                return;
            }
            active.tickInternal();
            if (active.done) {
                active = null;
            }
        }
    }

    private void tickInternal() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != this.screen || mc.player == null || mc.player.containerMenu != this.menu) {
            done = true;
            return;
        }
        if (--countdown > 0) {
            return;
        }
        switch (phase) {
            case SHOW:
                onShowEnd();
                break;
            case GAP:
                onGapEnd();
                break;
        }
    }

    /** 开始一步：解析配方 → 预检 → EMI 一次性放料 → 进入 SHOW 等结果槽。 */
    private void placeStep() {
        if (steps >= MAX_STEPS) {
            done = true;
            return;
        }
        EmiRecipe recipe = pickNext();
        if (recipe == null) {
            // 区分失败原因：存在非工作台默认配方（熔炉/锻造/机器等，链条不支持）→ 专有提示
            fail(!force, hasNonWorkbenchDefault() ? "该配方无法在工作台内进行" : "无法自动合成：材料不足");
            done = true;
            return;
        }
        currentRecipe = recipe;
        currentIsGoal = (recipe == goalRecipe);
        // 硬检查：链条只合成**工作台配方**（pickNext 的中间产物也过滤了，这里是防御）。
        // 非工作台配方（如默认熔炉）放不进合成格，直接停止提示。
        if (!isWorkbenchRecipe(recipe)) {
            fail(!force, "该配方无法在工作台内进行");
            done = true;
            return;
        }
        if (!canFitCurrentGrid(recipe)) {
            fail(true, "该配方需要更大的合成格（请打开工作台后合成）");
            done = true;
            return;
        }
        // 数量感知预检：EMI 的 canCraft 按"种类"判断（同种物品需 2 个时 1 个也算够），必须按数量判定
        if (!canAfford(recipe)) {
            fail(!force, "无法自动合成：材料不足");
            done = true;
            return;
        }
        // 放料：复用 EMI clientFill（Destination.NONE）一次性放置全部材料，光标受控、形状感知。
        if (!fillViaEmi()) {
            done = true;
            return;
        }
        steps++;
        retries = 0;
        phase = Phase.SHOW;
        countdown = showTicks();
    }

    /** 参照 EMI 原版 shift 填充：用 {@link EmiRecipeFiller#clientFill} 一次性放置当前配方所需材料。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean fillViaEmi() {
        try {
            List<ItemStack> stacks = EmiRecipeFiller.getStacks(handler, currentRecipe, screen, 1);
            if (stacks == null) {
                return false;
            }
            boolean ok = EmiRecipeFiller.clientFill(handler, currentRecipe, screen, stacks,
                    EmiCraftContext.Destination.NONE);
            if (!ok) {
                fail(true, "无法放置该配方的材料");
            }
            return ok;
        } catch (Exception e) {
            fail(true, "无法放置该配方的材料：" + e);
            return false;
        }
    }

    /** 显示结束：取走结果（QUICK_MOVE/PICKUP/handler.craft），用**背包产物数量增加**验证取走成功
     *  （模组界面 output.hasItem 可能恒 true 不刷新，不能依赖它判断完成）。 */
    private void onShowEnd() {
        Slot output = handler.getOutputSlot(menu);
        // 1) 有待验证的取走 → 先查背包数量是否增加（取走成功）
        if (pendingResult != null) {
            // 取产物可能把产物留在光标（AE2 CRAFT_ITEM / PICKUP 阶段）：先放回背包再验证
            if (!menu.getCarried().isEmpty()) {
                placeCursorIntoInventory();
            }
            long now = CraftInventory.count(EmiStack.of(pendingResult));
            if (now > pendingBefore) {
                pendingResult = null;
                afterOutputTaken(output);
                return;
            }
            if (retries++ >= RESULT_RETRY) {
                pendingResult = null;
                clearCursorIfHeld();
                fail(true, "无法取走合成产物");
                done = true;
                return;
            }
            // 未增加（同步延迟或需再取）→ 若 output 仍有货则再取一次
            if (output != null && output.hasItem()) {
                takeOutput(output);
            }
            phase = Phase.SHOW;
            countdown = resultRetryTicks();
            return;
        }
        // 2) 无待验证：output 有货 → 发起取走并记录基线
        if (output != null && output.hasItem()) {
            pendingResult = output.getItem().copy();
            pendingBefore = CraftInventory.count(EmiStack.of(pendingResult));
            takeOutput(output);
            if (done) {
                return;
            }
            phase = Phase.SHOW;
            countdown = resultRetryTicks();
            return;
        }
        // 3) 无待验证且 output 空 → 正常进入下一步/目标处理
        afterOutputTaken(output);
    }

    /** 取走成功后的处理：目标步提示/停；中间步进入 GAP 继续。 */
    private void afterOutputTaken(Slot output) {
        if (done) {
            return;
        }
        if (currentIsGoal) {
            String name = "";
            var outs = goalRecipe.getOutputs();
            if (!outs.isEmpty()) {
                ItemStack goalOut = outs.get(0).getItemStack();
                if (goalOut != null && !goalOut.isEmpty()) {
                    name = goalOut.getHoverName().getString();
                }
            }
            if (name.isEmpty() && output != null) {
                name = output.getItem().getHoverName().getString();
            }
            MessageOverlay.show(Component.literal("合成成功: " + name), true);
            if (!repeat) {
                done = true;
                return;
            }
        }
        phase = Phase.GAP;
        countdown = gapTicks();
    }

    /** 光标被物品占用时，左键点一个空源槽把它放回去（清空光标）。 */
    private void clearCursorIfHeld() {
        if (menu.getCarried().isEmpty()) {
            return;
        }
        Slot empty = findEmptySource();
        if (empty != null) {
            clickSlot(empty, 0, ClickType.PICKUP);
        }
    }

    /** 取走结果槽物品（分阶段，配合 onShowEnd 的背包数量验证）：
     *  <ul>
     *   <li>retries==0：QUICK_MOVE（shift，服务端自动堆叠到背包）——优先，成功后不再走后续阶段；</li>
     *   <li>retries==1：PICKUP 手动拾起 + 放回背包空槽（shift 无效的模组槽）；</li>
     *   <li>retries>=2：handler.craft fallback（AE2/精妙背包等 override 取产物）。</li>
     *  </ul>
     *  每次只做一个阶段，onShowEnd 用"背包数量增加"验证；未增加才进入下一阶段。
     *  这样 QUICK_MOVE 生效时（堆叠）不会被后续 fallback 干扰产生第二份不堆叠的产物。 */
    private void takeOutput(Slot output) {
        if (output == null || !output.hasItem()) {
            return;
        }
        // AE2 合成终端：结果槽 CraftingTermSlot.mayPickup() 恒 false，vanilla QUICK_MOVE/PICKUP
        // 与 handler.craft（AE2 只放料）都取不走产物；改用 AE2 action 机制
        // （InventoryActionPacket(CRAFT_ITEM) = 玩家左键点击结果槽，合成一份到光标）。
        if (Ae2Support.isCraftingTermMenu(menu) && Ae2Support.isCraftingTermSlot(output)) {
            int idx = findMenuSlotIndex(output);
            if (idx >= 0) {
                Ae2Support.takeOutput(menu, idx);
                // CRAFT_ITEM 合成后 AE2 会把网络等价材料补回合成格（网络借料设计）→ 立即清格，
                // 残留材料移回玩家背包，防止"合成后重新填充"（配方材料无法消耗）。
                Ae2Support.clearCraftingGrid(menu);
            }
            return;
        }
        if (retries <= 0) {
            // 阶段 0：shift 取走（服务端自动堆叠）
            clickSlot(output, 0, ClickType.QUICK_MOVE);
        } else if (retries == 1) {
            // 阶段 1：手动拾起 → 放回背包空槽
            clickSlot(output, 0, ClickType.PICKUP);
            placeCursorIntoInventory();
        } else {
            // 阶段 2+：handler.craft fallback（模组 override 的取产物逻辑）
            try {
                @SuppressWarnings({"rawtypes", "unchecked"})
                EmiCraftContext ctx = new EmiCraftContext(screen,
                        CraftInventory.current(currentRecipe), EmiCraftContext.Type.CRAFTABLE,
                        EmiCraftContext.Destination.CURSOR, 1);
                handler.craft(currentRecipe, ctx);
            } catch (Exception e) {
                com.cancan.emibettersynthesischain.EMIBettersynthesischain.LOGGER.warn(
                        "EBS takeOutput fallback craft failed: {}", e.toString());
            }
            placeCursorIntoInventory(); // fallback 后光标可能残留 → 清回背包
        }
    }

    /** 光标有物 → 放回背包：**优先堆叠到已有同种且未满的物品格**（连续合成产物堆叠，
     *  不散落空槽）；无同种可堆叠再放空槽；光标空 → no-op。 */
    private void placeCursorIntoInventory() {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            return;
        }
        Slot stackable = findStackableInventorySource(carried);
        if (stackable != null) {
            clickSlot(stackable, 0, ClickType.PICKUP);
            return;
        }
        Slot empty = findEmptyInventorySource();
        if (empty != null) {
            clickSlot(empty, 0, ClickType.PICKUP);
        }
    }

    /** 找一个**非合成格**的、已有同种且未满的物品格（用于堆叠放回结果）。 */
    private Slot findStackableInventorySource(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        List<Slot> sources = handler.getInputSources(menu);
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Slot> grid = handler.getCraftingSlots(menu);
        for (Slot s : sources) {
            if (s == null || grid.contains(s)) {
                continue;
            }
            ItemStack it = s.getItem();
            if (!it.isEmpty() && ItemStack.isSameItemSameComponents(it, item)
                    && it.getCount() < it.getMaxStackSize()) {
                return s;
            }
        }
        return null;
    }

    /** 找一个**非合成格**的空输入槽（背包/库存侧），用于放回取出的结果。 */
    private Slot findEmptyInventorySource() {
        List<Slot> sources = handler.getInputSources(menu);
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Slot> grid = handler.getCraftingSlots(menu);
        for (Slot s : sources) {
            if (s == null || grid.contains(s)) {
                continue;
            }
            if (!s.hasItem()) {
                return s;
            }
        }
        return null;
    }

    private void onGapEnd() {
        placeStep();
    }

    private Slot findEmptySource() {
        List<Slot> sources = handler.getInputSources(menu);
        for (Slot s : sources) {
            if (s != null && !s.hasItem()) {
                return s;
            }
        }
        return null;
    }

    /** 该配方是否为**工作台配方**（backingRecipe 为 CraftingRecipe，EMI 的 EmiCraftingRecipe 包装）。
     *  链条只合成工作台配方：非工作台（熔炉/锻造/模组机器）放不进合成格。 */
    private static boolean isWorkbenchRecipe(EmiRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        RecipeHolder<?> holder = recipe.getBackingRecipe();
        return holder != null && holder.value() instanceof CraftingRecipe;
    }

    /** 当前菜单合成格能否放下该配方（2×2 背包 vs 3×3 工作台）。用配方非空输入 bounding box 判定。 */
    private boolean canFitCurrentGrid(EmiRecipe recipe) {
        if (!(recipe instanceof EmiCraftingRecipe crafting)) {
            return false; // 非工作台配方（EmiCraftingRecipe）一律不能放进合成格
        }
        int w, h;
        if (menu instanceof RecipeBookMenu rbm) {
            w = rbm.getGridWidth();
            h = rbm.getGridHeight();
        } else {
            @SuppressWarnings({"rawtypes", "unchecked"})
            List<Slot> gs = handler.getCraftingSlots(menu);
            int n = 0;
            for (Slot s : gs) {
                if (s != null) {
                    n++;
                }
            }
            w = h = Math.max(1, (int) Math.ceil(Math.sqrt(n)));
        }
        return fitsGrid(crafting, w, h);
    }

    /** 用配方非空输入 bounding box 判断给定网格 (w,h) 能否放下。 */
    private static boolean fitsGrid(EmiCraftingRecipe r, int w, int h) {
        List<EmiIngredient> in = r.getInputs();
        if (in == null || in.isEmpty()) {
            return true;
        }
        if (in.size() > 9) {
            return false;
        }
        int nonEmpty = 0;
        for (EmiIngredient e : in) {
            if (e != null && !e.isEmpty()) {
                nonEmpty++;
            }
        }
        if (nonEmpty == 0) {
            return true;
        }
        if (in.size() != 9) {
            return nonEmpty <= w * h;
        }
        int minC = 9, maxC = -1, minR = 9, maxR = -1;
        for (int i = 0; i < 9; i++) {
            EmiIngredient e = in.get(i);
            if (e == null || e.isEmpty()) {
                continue;
            }
            int col = i % 3;
            int row = i / 3;
            minC = Math.min(minC, col);
            maxC = Math.max(maxC, col);
            minR = Math.min(minR, row);
            maxR = Math.max(maxR, row);
        }
        int needW = maxC - minC + 1;
        int needH = maxR - minR + 1;
        return needW <= w && needH <= h;
    }

    /** 发一个 vanilla 点击包（核心操作：越界/异常 → 中止链）。 */
    private void click(int slotIndex, int button, ClickType type) {
        click(slotIndex, button, type, true);
    }

    /** 对菜单槽点击：**用 menu.slots 的 index**。模组 handler 返回的 Slot.index 可能是容器 index
     *  （如精妙背包输出槽 172 > menu.slots.size()=156），且对象可能不是 menu.slots 实例 → 按
     *  container+containerSlot 匹配。辅助操作失败不中止链。 */
    private void clickSlot(Slot slot, int button, ClickType type) {
        int idx = findMenuSlotIndex(slot);
        if (idx < 0) {
            return; // 不在当前菜单（无法点击）
        }
        click(idx, button, type, false);
    }

    /** 在 menu.slots 里找到该槽的菜单 index（先按引用，再按 container+containerSlot 匹配）。 */
    private int findMenuSlotIndex(Slot slot) {
        if (slot == null) {
            return -1;
        }
        int byRef = menu.slots.indexOf(slot);
        if (byRef >= 0) {
            return byRef;
        }
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s != null && s.container == slot.container && s.getContainerSlot() == slot.getContainerSlot()) {
                return i;
            }
        }
        return -1;
    }

    private void click(int slotIndex, int button, ClickType type, boolean fatal) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (slotIndex < 0 || slotIndex >= mc.player.containerMenu.slots.size()) {
                com.cancan.emibettersynthesischain.EMIBettersynthesischain.LOGGER.warn(
                        "EBS click OUT-OF-BOUNDS slot={} slotsSize={} type={}", slotIndex,
                        mc.player.containerMenu.slots.size(), type);
                if (fatal) {
                    fail(true, "合成槽位越界，中止");
                    done = true;
                }
                return;
            }
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slotIndex, button, type,
                    mc.player);
        } catch (Exception e) {
            com.cancan.emibettersynthesischain.EMIBettersynthesischain.LOGGER.warn(
                    "EBS click EXCEPTION slot={} type={} : {}", slotIndex, type, e.toString());
            if (fatal) {
                fail(true, "自动合成点击出错，中止");
                done = true;
            }
        }
    }

    /** 材料去重键：标签用 tag:id；物品用 id#组件哈希（与树/库存计数一致）。 */
    private static String matKey(EmiIngredient ing) {
        if (ing instanceof TagEmiIngredient tag) {
            var loc = tag.key.location();
            return "tag:" + loc;
        }
        var stacks = ing.getEmiStacks();
        if (stacks.isEmpty()) {
            return "";
        }
        EmiStack s = stacks.get(0);
        String id = s.getId() == null ? "" : s.getId().toString();
        ItemStack it = s.getItemStack();
        if (it != null && !it.isEmpty()) {
            id += "#" + Integer.toHexString(ItemStack.hashItemAndComponents(it));
        }
        return id;
    }

    /** 该材料在配方中**所有同种输入槽**的总需要量（如 6 个木板槽 → 6，而非单槽 1）。 */
    private static long totalNeedOf(EmiRecipe recipe, EmiIngredient input) {
        String k = matKey(input);
        long total = 0;
        for (EmiIngredient in : recipe.getInputs()) {
            if (in.isEmpty()) {
                continue;
            }
            if (matKey(in).equals(k)) {
                total += Math.max(1, in.getAmount());
            }
        }
        return total;
    }

    /** 数量感知：目标配方所有输入（**同种材料聚合总量**）是否足够。 */
    private boolean goalReady() {
        for (EmiIngredient input : goalRecipe.getInputs()) {
            if (input.isEmpty()) {
                continue;
            }
            if (CraftInventory.count(goalRecipe, input) < totalNeedOf(goalRecipe, input)) {
                return false;
            }
        }
        return true;
    }

    /** 数量感知：当前库存能否直接合成该配方（每个输入按**同种材料聚合总量**判断）。 */
    private boolean canAfford(EmiRecipe recipe) {
        try {
            for (EmiIngredient input : recipe.getInputs()) {
                if (input.isEmpty()) {
                    continue;
                }
                if (CraftInventory.count(recipe, input) < totalNeedOf(recipe, input)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 下一步合成什么：目标材料齐（**数量感知**）→ 合目标；否则先合中间材料（祖先栈防环）。
     *  强制模式：遍历所有缺料输入，返回任一能合成的中间产物（缺料跳过）。 */
    private EmiRecipe pickNext() {
        if (goalReady()) {
            return goalRecipe;
        }
        if (force) {
            for (EmiIngredient input : goalRecipe.getInputs()) {
                if (input.isEmpty()) {
                    continue;
                }
                if (CraftInventory.count(goalRecipe, input) < totalNeedOf(goalRecipe, input)) {
                    EmiRecipe p = findProducerToCraft(input, 0);
                    if (p != null) {
                        return p;
                    }
                }
            }
            return goalRecipe;
        }
        for (EmiIngredient input : goalRecipe.getInputs()) {
            if (input.isEmpty()) {
                continue;
            }
            long cnt = CraftInventory.count(goalRecipe, input);
            long amt = totalNeedOf(goalRecipe, input);
            if (cnt < amt) {
                return findProducerToCraft(input, 0); // 可能 null → placeStep 报材料不足
            }
        }
        return goalRecipe;
    }

    /** 目标链路上是否存在**非工作台默认配方**（如玩家把熔炉配方"设为默认"）→ 链条无法合成该中间材料，
     *  用于提示"该配方无法在工作台内进行"（区别于单纯的材料不足）。 */
    private boolean hasNonWorkbenchDefault() {
        for (EmiIngredient input : goalRecipe.getInputs()) {
            if (input.isEmpty()) {
                continue;
            }
            try {
                EmiRecipe r = BoM.getRecipe(input);
                if (r != null && !isWorkbenchRecipe(r)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private EmiRecipe findProducerToCraft(EmiIngredient ing, int depth) {
        if (depth > 6) {
            return null;
        }
        EmiRecipe producer;
        try {
            producer = BoM.getRecipe(ing);
        } catch (Exception e) {
            producer = null;
        }
        // 只接受**工作台配方**：非工作台（如玩家把熔炉配方设为默认）不能放进合成格，
        // 视为"该材料无法在链条中合成"（仅工作台配方自动合成）。
        if (producer == null || ancestors.contains(producer) || !isWorkbenchRecipe(producer)) {
            return null;
        }
        ancestors.add(producer);
        EmiRecipe found = null;
        for (EmiIngredient sub : producer.getInputs()) {
            if (sub.isEmpty()) {
                continue;
            }
            if (CraftInventory.count(goalRecipe, sub) < totalNeedOf(producer, sub)) {
                // 缺料的子材料：若是 tag，遍历其具体成员找可合成的（BoM.getRecipe(tag) 恒 null，
                // 不能直接递归 tag）；若是具体物品，直接递归。
                if (sub instanceof TagEmiIngredient tag) {
                    for (EmiStack member : tag.getEmiStacks()) {
                        ItemStack m = member.getItemStack();
                        if (m == null || m.isEmpty()) {
                            continue;
                        }
                        EmiRecipe p = findProducerToCraft(EmiStack.of(m), depth + 1);
                        if (p != null) {
                            found = p;
                            break;
                        }
                    }
                } else {
                    found = findProducerToCraft(sub, depth + 1);
                }
                break;
            }
        }
        if (found == null) {
            boolean allPresent = true;
            for (EmiIngredient sub : producer.getInputs()) {
                if (!sub.isEmpty()
                        && CraftInventory.count(goalRecipe, sub) < totalNeedOf(producer, sub)) {
                    allPresent = false;
                    break;
                }
            }
            if (allPresent) {
                found = producer;
            }
        }
        ancestors.remove(producer);
        return found;
    }

    /** 取产物验证的等待 tick：AE2 终端每步发 CRAFT_ITEM + 清格最多 10 个包，同步慢，
     *  用更长等待避免"同步未到 → 误重试 → 多合成一份"；普通界面 2 tick 足够
     *  （验证失败且 output 空时不会重复合成，只是多一次空循环）。 */
    private int resultRetryTicks() {
        return Ae2Support.isCraftingTermMenu(menu) ? 12 : 2;
    }

    /** 材料进格显示时长：直接用设置值（defineInRange 已限定 2-40，不再加隐藏下限，
     *  否则设置页调到 2 也不生效、合成速度无法最快）。 */
    private static int showTicks() {
        return Math.max(2, Config.AUTO_CRAFT_SHOW_TICKS.get());
    }

    private static int gapTicks() {
        return Math.max(0, Config.AUTO_CRAFT_GAP_TICKS.get());
    }

    private void fail(boolean show, String text) {
        if (show) {
            MessageOverlay.show(Component.literal(text), false);
        } else {
            com.cancan.emibettersynthesischain.EMIBettersynthesischain.LOGGER.debug("EBS auto-craft (quiet stop): {}", text);
        }
    }
}