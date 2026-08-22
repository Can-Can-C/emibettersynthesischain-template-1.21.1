package com.cancan.emibettersynthesischain.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cancan.emibettersynthesischain.Config;
import com.cancan.emibettersynthesischain.EMIBettersynthesischain;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.config.IntGroup;
import dev.emi.emi.config.SidebarSettings;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.RecipeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * {@link IEmiInternal} 的实现。所有对 EMI 内部类的访问集中于此（EMI 1.1.24）。
 *
 * <p>升级 EMI 版本时优先调整本类；若需访问 EMI 的私有成员，则在 {@code mixin/} 下添加
 * {@code @Shadow}/{@code @Accessor}/{@code @Invoker} 的 accessor mixin，并在此调用。</p>
 */
public class InternalHelperImpl implements IEmiInternal {
    public static final IEmiInternal INSTANCE = new InternalHelperImpl();

    private List<TreeData> treeCache;
    private int lastBoMHash = Integer.MIN_VALUE;
    private int lastInvHash = Integer.MIN_VALUE;
    private int boMCheckCounter = 0;
    private int invCheckCounter = 0;
    /** 树重建时构建一次的库存快照：hasEnough/canObtain 全部复用，避免每次计数重建快照（性能热点）。 */
    private EmiPlayerInventory invSnapshot;

    private InternalHelperImpl() {
    }

    @Override
    public void focusFavoritesSidebar() {
        EmiScreenManager.focusSidebarType(SidebarType.FAVORITES);
    }

    @Override
    public ItemStack getHoveredItemStack() {
        net.minecraft.client.Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container) {
            net.minecraft.world.inventory.Slot slot = container.getSlotUnderMouse();
            if (slot != null && slot.hasItem()) {
                return slot.getItem();
            }
        }
        EmiStackInteraction interaction = EmiApi.getHoveredStack(true);
        if (interaction != null && !interaction.isEmpty()) {
            ItemStack stack = firstStack(interaction.getStack());
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getAddTarget() {
        // 配方查询界面：EMI 的 per-recipe 树按钮点击时已 BoM.setGoal(recipe)，goal 即当前配方产物
        if (Minecraft.getInstance().screen instanceof RecipeScreen) {
            if (BoM.tree != null && BoM.tree.goal != null) {
                ItemStack stack = firstStack(BoM.tree.goal.ingredient);
                if (!stack.isEmpty()) {
                    return stack;
                }
                // Productive Bees 蜜蜂：goal.ingredient 是虚拟 BeeEmiStack（getItemStack 空）→ 蜂笼物品
                ItemStack cage = cageFromIngredient(BoM.tree.goal.ingredient);
                if (cage != null && !cage.isEmpty()) {
                    return cage;
                }
            }
            EMIBettersynthesischain.LOGGER.info("EBS getAddTarget: RecipeScreen but BoM goal missing (tree={} goal={})",
                    BoM.tree == null ? "null" : "set", BoM.tree != null && BoM.tree.goal == null ? "null" : "set");
        }
        ItemStack hovered = getHoveredItemStack();
        if (hovered.isEmpty()) {
            hovered = cageFromHovered(); // 悬停是蜜蜂（虚拟）→ 蜂笼物品
        }
        if (hovered.isEmpty()) {
            EMIBettersynthesischain.LOGGER.info("EBS getAddTarget: hovered fallback EMPTY (screen={})",
                    Minecraft.getInstance().screen == null ? "null" : Minecraft.getInstance().screen.getClass().getName());
        }
        return hovered;
    }

    /** Productive Bees 蜜蜂 EmiIngredient → 蜂笼物品；非蜜蜂 → null。 */
    private ItemStack cageFromIngredient(EmiIngredient ing) {
        try {
            List<EmiStack> stacks = ing.getEmiStacks();
            if (!stacks.isEmpty()) {
                return ProductiveBeesSupport.toBeeCageStack(stacks.get(0));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 悬停物品若是 Productive Bees 蜜蜂（虚拟 BeeEmiStack）→ 蜂笼物品；否则 EMPTY。 */
    private ItemStack cageFromHovered() {
        try {
            EmiStackInteraction interaction = EmiApi.getHoveredStack(true);
            if (interaction != null && !interaction.isEmpty()) {
                List<EmiStack> stacks = interaction.getStack().getEmiStacks();
                if (!stacks.isEmpty()) {
                    ItemStack cage = ProductiveBeesSupport.toBeeCageStack(stacks.get(0));
                    if (cage != null) {
                        return cage;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ResourceLocation getGoalRecipeId() {
        // 当前 BoM 目标配方（加入时 EMI 刚 setGoal 的那个配方 = "最后一步配方"）
        if (BoM.tree != null && BoM.tree.goal != null && BoM.tree.goal.recipe != null) {
            return BoM.tree.goal.recipe.getId();
        }
        return null;
    }

    @Override
    public Bounds getFavoritesPanelBounds() {
        EmiScreenManager.SidebarPanel panel = EmiScreenManager.getPanelFor(SidebarType.FAVORITES);
        return panel == null ? null : panel.getBounds();
    }

    @Override
    public int getMouseX() {
        return EmiScreenManager.lastMouseX;
    }

    @Override
    public int getMouseY() {
        return EmiScreenManager.lastMouseY;
    }

    @Override
    public boolean isFavoritesPanel(Object panel) {
        if (panel instanceof EmiScreenManager.SidebarPanel p) {
            return p.getType() == SidebarType.FAVORITES;
        }
        return false;
    }

    @Override
    public Bounds getPanelBounds(Object panel) {
        if (panel instanceof EmiScreenManager.SidebarPanel p) {
            return p.getBounds();
        }
        return Bounds.EMPTY;
    }

    @Override
    public void widenFavoritesPanel() {
        IntGroup size = SidebarSettings.LEFT.size();
        if (!TreeMode.hasOriginalWidth()) {
            TreeMode.setOriginalWidth(size.values.getInt(0));
        }
        size.values.set(0, Config.TREE_SIDEBAR_WIDTH.get());
        EmiScreenManager.forceRecalculate();
    }

    @Override
    public void restoreFavoritesPanel() {
        if (!TreeMode.hasOriginalWidth()) {
            return;
        }
        IntGroup size = SidebarSettings.LEFT.size();
        size.values.set(0, TreeMode.getOriginalWidth());
        TreeMode.clearOriginalWidth();
        EmiScreenManager.forceRecalculate();
    }

    @Override
    public List<TreeData> buildTrees() {
        maybeRefreshOnBoMChange();
        maybeRefreshOnInventoryChange();
        if (treeCache == null || TreeManager.INSTANCE.isDirty()) {
            // 树重建时刷新一次库存快照：hasEnough/canObtain 全部用同一快照，
            // 避免每个节点每次计数都重建 EmiPlayerInventory（合成量大时是主要卡顿源）。
            invSnapshot = CraftInventory.currentScreenInventory();
            // 每树独立"单次合成数量"（各自调数量互不影响；数量变化后 setAmount 已 markDirty 重建）
            List<TreeData> trees = new ArrayList<>();
            for (int i = 0; i < TreeManager.INSTANCE.size(); i++) {
                ItemStack item = TreeManager.INSTANCE.getItem(i);
                // 树显示：普通物品原样；Productive Bees 蜜蜂（持久化为蜂笼）→ buildTree 内用
                // 配方输出的原始蜜蜂 EmiStack 显示（原版 EMI 同路径，渲染蜜蜂本体）
                TreeData tree = buildTree(EmiStack.of(item), TreeManager.INSTANCE.getRecipeId(i),
                        TreeManager.INSTANCE.getAmount(i));
                if (tree != null && !tree.isEmpty()) {
                    trees.add(tree);
                }
            }
            treeCache = trees;
            TreeManager.INSTANCE.markClean();
        }
        return treeCache;
    }

    /** 玩家在 EMI 里改了"设为默认配方"/标签解析时，刷新树缓存（每 20 帧校验一次 BoM 状态）。 */
    private void maybeRefreshOnBoMChange() {
        if (++boMCheckCounter % 20 != 0) {
            return;
        }
        int hash;
        try {
            hash = BoM.defaultRecipes.hashCode() + BoM.addedRecipes.hashCode();
        } catch (Exception e) {
            return;
        }
        if (hash != lastBoMHash) {
            lastBoMHash = hash;
            TreeManager.INSTANCE.markDirty();
        }
    }

    /** 玩家背包变化（或打开/关闭工作台等换菜单）时刷新树缓存（标红"能否获得"用最新状态）。
     *  <p>除玩家背包 36 格外，还会检测：当前容器菜单**槽位内容**（背包等容器内挪动/存取）与
     *  **AE 网络存储**（AE 合成终端，走 {@link Ae2Support#networkSignature}）——否则只在 AE/背包
     *  里动存储、玩家背包没变时，树标红/可合成判定停留在旧快照（"存储物读取不准"根因）。</p> */
    private void maybeRefreshOnInventoryChange() {
        try {
            Player player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            int hash = 0;
            // 背包 hash 每 5 tick 算一次（0.25s 微延迟，避免每帧 36 格组件哈希）；菜单类名每帧
            // （打开/关闭工作台等容器切换要即时触发标红刷新）
            if (++invCheckCounter % 5 == 0) {
                for (ItemStack s : player.getInventory().items) {
                    if (!s.isEmpty()) {
                        // 组件感知：用 StackCompat.hashStack（item + NBT，等价的组件感知），区分同 item 不同 NBT（药水/时长/附魔）
                        hash = hash * 31 + StackCompat.hashStack(s);
                        hash = hash * 31 + s.getCount();
                    }
                }
                // 当前容器菜单的槽位内容（背包本体等）：容器内挪动/存取物品、合成格变化也触发刷新
                AbstractContainerMenu menu = player.containerMenu;
                if (menu != null) {
                    for (Slot slot : menu.slots) {
                        ItemStack s = slot.getItem();
                        if (!s.isEmpty()) {
                            hash = hash * 31 + StackCompat.hashStack(s);
                            hash = hash * 31 + s.getCount();
                        }
                    }
                }
                // AE 网络存储（仅合成终端，非 AE 返回 0）：网络条目增减/数量变化触发刷新
                hash = hash * 31 + Long.hashCode(Ae2Support.networkSignature());
            }
            // 3×3 标红依赖"是否打开工作台界面"，菜单切换也触发刷新
            hash = hash * 31 + (player.containerMenu == null ? 0
                    : player.containerMenu.getClass().getName().hashCode());
            if (hash != lastInvHash) {
                lastInvHash = hash;
                TreeManager.INSTANCE.markDirty();
            }
        } catch (Exception e) {
            // 忽略：下次再试
        }
    }

    /**
     * 构建一棵树的布局数据：worklist 成本聚合（复刻 EMI BoM 行为）。
     *
     * <p>对每个材料：need = 总需要量；batch = ceil(need / 单次产出)；produced = batch×单次产出；
     * excess = produced - need（&gt;0 即副产物）。目标节点 amount = 配方单次输出量 × 目标份数
     * （需求 13：单次合成数量 N，树按 N 倍展示材料与最终产物）。</p>
     */
    private TreeData buildTree(EmiIngredient goalIng, ResourceLocation goalRecipeId, long targetAmount) {
        if (goalIng == null || goalIng.isEmpty()) {
            return null;
        }
        long target = Math.max(1, targetAmount);
        Map<String, Agg> aggs = new HashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        // 目标配方（"加入时查看的配方"，不需要默认配方）
        EmiRecipe goalRecipe = resolveGoalRecipe(goalIng, goalRecipeId);
        // 显示目标：Productive Bees 配方输出是蜜蜂（原版 BeeEmiStack，实体/品种数据正确，渲染蜜蜂本体）
        // → 直接用配方输出显示（与原版 EMI 相同路径），避免自造 BeeEmiStack（渲染空白/key 错乱）；
        // 非蜜蜂配方 → 原样 goalIng。
        EmiIngredient goalDisplay = goalIng;
        if (goalRecipe != null) {
            List<EmiStack> outs = goalRecipe.getOutputs();
            if (outs != null && !outs.isEmpty() && ProductiveBeesSupport.isBeeEmiStack(outs.get(0))) {
                goalDisplay = outs.get(0);
            }
        }
        // 目标（最终结果）：need = 目标份数（N 倍展开材料）
        addGoal(aggs, queue, goalDisplay, goalRecipe, target);
        int guard = 0;
        while (!queue.isEmpty()) {
            // 兜底：防止配方环（如药水 NBT 同 id）导致 worklist 无限增长
            if (++guard > 3000) {
                EMIBettersynthesischain.LOGGER.warn("EBS tree build iteration limit reached, aborting build");
                break;
            }
            String key = queue.poll();
            Agg agg = aggs.get(key);
            if (agg.recipe == null) {
                continue;
            }
            long outputPerCraft = outputAmount(agg.recipe, agg.content);
            if (outputPerCraft <= 0) {
                continue;
            }
            long newBatch = ceilDiv(agg.need, outputPerCraft);
            if (newBatch == agg.batch) {
                continue;
            }
            long deltaBatch = newBatch - agg.batch;
            agg.batch = newBatch;
            // EMI 式防环：禁止子材料用"到达本材料的路径上"已用过的配方
            Set<EmiRecipe> forbidden = new HashSet<>(agg.path);
            if (agg.recipe != null) {
                forbidden.add(agg.recipe);
            }
            for (EmiIngredient input : agg.recipe.getInputs()) {
                addNeed(aggs, queue, input, input.getAmount() * deltaBatch, agg.depth + 1, forbidden);
            }
        }

        Agg goalAgg = aggs.get(key(goalIng));
        if (goalAgg == null || goalAgg.need <= 0) {
            return null;
        }
        long goalOutput = outputAmount(goalAgg.recipe, goalAgg.content);
        if (goalOutput <= 0) {
            goalOutput = Math.max(1, goalAgg.need);
        }
        // 目标显示：单批输出量 × 目标份数（N 个最终产物）
        EmiIngredient goalDisp = goalAgg.content.copy().setAmount(Math.max(1, target));

        // 直接输入行（目标配方输入，同材料聚合，amount = 配方消耗量 × 目标批次）
        List<EmiIngredient> directInputs = new ArrayList<>();
        if (goalAgg.recipe != null) {
            Map<String, EmiIngredient> seen = new LinkedHashMap<>();
            Map<String, Long> amounts = new LinkedHashMap<>();
            for (EmiIngredient input : goalAgg.recipe.getInputs()) {
                if (input.isEmpty()) {
                    continue;
                }
                String k = key(input);
                seen.putIfAbsent(k, input);
                amounts.merge(k, input.getAmount() * goalAgg.batch, Long::sum);
            }
            for (String k : seen.keySet()) {
                directInputs.add(seen.get(k).copy().setAmount(amounts.get(k)));
            }
        }

        // 聚合材料行（排除目标；仅当"非直接输入"或"聚合量≠直接消耗量"时显示），按深度降序
        List<Agg> materials = new ArrayList<>();
        for (Agg agg : aggs.values()) {
            if (agg == goalAgg) {
                continue;
            }
            long direct = directAmountOf(goalAgg, agg);
            if (direct >= 0 && agg.need == direct) {
                continue;
            }
            materials.add(agg);
        }
        materials.sort(Comparator.comparingLong((Agg a) -> a.depth).reversed());
        List<List<EmiIngredient>> rows = new ArrayList<>();
        List<EmiIngredient> currentRow = null;
        int lastDepth = -1;
        for (Agg agg : materials) {
            if (currentRow == null || agg.depth != lastDepth) {
                currentRow = new ArrayList<>();
                rows.add(currentRow);
                lastDepth = agg.depth;
            }
            currentRow.add(agg.content.copy().setAmount(Math.max(1, agg.need)));
        }

        // 副产物（过量产出，排除目标）
        List<EmiIngredient> byproducts = new ArrayList<>();
        for (Agg agg : aggs.values()) {
            if (agg == goalAgg || agg.recipe == null) {
                continue;
            }
            long produced = agg.batch * outputAmount(agg.recipe, agg.content);
            long excess = produced - agg.need;
            if (excess > 0) {
                byproducts.add(agg.content.copy().setAmount(excess));
            }
        }

        // 封装为 TreeItem（标红：能否获得该节点——自身够或可由工作台链式合成，含标签成员/3×3 需工作台）
        // 目标节点按 N 份判断能否获得（goalAgg.need = 目标份数）
        TreeData.TreeItem goalItem = new TreeData.TreeItem(goalDisp,
                canObtain(goalDisp, goalAgg.need, goalAgg.recipe, 0, new HashSet<>()), resolvedToFor(goalAgg),
                goalAgg.recipe);
        List<TreeData.TreeItem> directItems = new ArrayList<>();
        for (EmiIngredient input : directInputs) {
            Agg agg = aggs.get(key(input));
            directItems.add(new TreeData.TreeItem(input,
                    canObtain(input, input.getAmount(), agg == null ? null : agg.recipe, 0, new HashSet<>()),
                    resolvedToFor(agg), agg == null ? null : agg.recipe));
        }
        List<List<TreeData.TreeItem>> rowItems = new ArrayList<>();
        for (List<EmiIngredient> row : rows) {
            List<TreeData.TreeItem> items = new ArrayList<>();
            for (EmiIngredient ing : row) {
                Agg agg = aggs.get(key(ing));
                items.add(new TreeData.TreeItem(ing,
                        canObtain(ing, ing.getAmount(), agg == null ? null : agg.recipe, 0, new HashSet<>()),
                        resolvedToFor(agg), agg == null ? null : agg.recipe));
            }
            rowItems.add(items);
        }

        // Q3: 顶部"底层总材料"行 = 当前所有叶节点（recipe==null，即递归到底/库存已足不再展开）所需量汇总。
        // 目标本身不算；same 材料若出现多次会用 need 聚合成单条。
        List<TreeData.TreeItem> leafTotal = new ArrayList<>();
        for (Map.Entry<String, Agg> e : aggs.entrySet()) {
            Agg agg = e.getValue();
            if (agg == goalAgg || agg.recipe != null || agg.need <= 0) {
                continue; // 跳过目标与中间产物（非叶）
            }
            leafTotal.add(new TreeData.TreeItem(agg.content.copy().setAmount(Math.max(1, agg.need)),
                    true, // 叶节点 = 库存已足可提供
                    resolvedToFor(agg), null)); // 叶节点无产出配方
        }
        // 保持视觉稳定：已解析标签在前，再按内容键排序（避免 HashMap 乱序）
        leafTotal.sort((a, b) -> {
            int rv = Boolean.compare(b.hasResolved(), a.hasResolved());
            if (rv != 0) {
                return rv;
            }
            return key(a.content()).compareTo(key(b.content()));
        });

        return new TreeData(goalItem, leafTotal, directItems, rowItems, byproducts);
    }

    /** 当前屏幕库存中该材料数量是否达到所需量（材料节点标红依据；跟随当前界面，如 AE2 终端=网络+背包）。 */
    private boolean hasEnough(EmiIngredient content, long amount) {
        try {
            if (amount <= 0) {
                return true;
            }
            // 仅判断"是否为可计数的物品"（流体/空不可计数视为足够）；不 copy ItemStack（canObtain 递归热点）
            List<EmiStack> stacks = content.getEmiStacks();
            if (stacks.isEmpty()) {
                return true;
            }
            ItemStack item = stacks.get(0).getItemStack();
            if (item == null || item.isEmpty()) {
                // Productive Bees 蜜蜂（虚拟 BeeEmiStack，getItemStack 空）：**可计数**（蜂笼等价）——
                // 不能走"空视为足够"分支，否则恒判库存够 → 永不拆解（"无法拆分"根因）
                if (ProductiveBeesSupport.isBeeEmiStack(stacks.get(0))) {
                    long owned = invSnapshot != null ? CraftInventory.count(invSnapshot, content)
                            : CraftInventory.count(content);
                    return owned >= amount;
                }
                return true; // 流体/空物品：不可计数，视为足够
            }
            if (invSnapshot != null) {
                // 树构建期间：复用本次重建的统一快照（避免每个节点每次重建）
                return CraftInventory.count(invSnapshot, content) >= amount;
            }
            return CraftInventory.hasEnough(content, amount);
        } catch (Exception e) {
            // 无法确认足够时按"不足"处理（标红保守），不再掩盖错误静默放行
            return false;
        }
    }

    /** 标签节点解析到的具体物品：始终查 BoM.getRecipe(tag)（即使因背包已够未拆解也保留）。非标签返回 null。 */
    private EmiIngredient resolvedToFor(Agg agg) {
        if (agg == null || !(agg.content instanceof TagEmiIngredient tag)) {
            return null;
        }
        try {
            EmiRecipe r = BoM.getRecipe(tag);
            if (r != null) {
                var outputs = r.getOutputs();
                if (!outputs.isEmpty()) {
                    return outputs.get(0);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 玩家当前能否**获得**该材料（链条可达，客户端干跑）：自身数量足够 → true；否则**任一**产出它的
     * **工作台配方**（首选 BoM 默认，含标签成员；中间 3×3 需打开工作台界面）的子材料链式可得 → true。
     * **EMI 祖先配方栈防环**：递归路径上配方重复即剪枝。**非工作台步骤不可合成**。
     * 深度上限（{@value #CAN_OBTAIN_MAX_DEPTH}）仅为**防递归指数爆炸**的性能约束（配方分支因子大），
     * 正常工作台配方链远浅于此；更深链按"不可获得"标红（保守）。
     */
    private static final int CAN_OBTAIN_MAX_DEPTH = 8;

    private boolean canObtain(EmiIngredient content, long amount, EmiRecipe preferred, int depth,
            Set<EmiRecipe> ancestors) {
        if (depth > CAN_OBTAIN_MAX_DEPTH) {
            return false;
        }
        if (hasEnough(content, amount)) {
            return true; // 自身数量足够
        }
        for (ProducerEntry producer : producersOf(content, preferred)) {
            EmiRecipe r = producer.recipe();
            if (ancestors.contains(r)) {
                continue; // 祖先栈上已有该配方 → 环，剪枝
            }
            if (!producer.is2x2() && !hasCraftingMenuOpen()) {
                continue; // 中间 3×3 需打开工作台界面
            }
            long batches = ceilDiv(amount, producer.perBatch());
            ancestors.add(r);
            boolean ok = true;
            for (EmiIngredient input : r.getInputs()) {
                if (input.isEmpty()) {
                    continue;
                }
                if (!canObtain(input, input.getAmount() * batches, null, depth + 1, ancestors)) {
                    ok = false;
                    break;
                }
            }
            ancestors.remove(r);
            if (ok) {
                return true;
            }
        }
        return false;
    }

    /** 产出该材料的配方候选（预计算 perBatch/is2x2，canObtain 直接消费，避免重复查询/计算）。 */
    private record ProducerEntry(EmiRecipe recipe, long perBatch, boolean is2x2) {
    }

    /**
     * 产出该材料的**工作台**配方：**只用默认配方**——preferred（父配方视角，如目标最后一步配方 /
     * 该材料已解析的默认配方）优先；否则查 {@link BoM#getRecipe}（玩家设为默认 / EMI 数据默认）。
     * 不逐个尝试其它产出配方（"设默认，断了就断了"：默认链断 → 标红/链条停止，尊重玩家配方选择）。
     */
    private List<ProducerEntry> producersOf(EmiIngredient content, EmiRecipe preferred) {
        List<ProducerEntry> out = new ArrayList<>();
        if (preferred != null && isWorkbenchRecipe(preferred)) {
            out.add(new ProducerEntry(preferred, outputAmount(preferred, content), is2x2Recipe(preferred)));
            return out;
        }
        try {
            EmiRecipe def = BoM.getRecipe(content);
            if (def != null && isWorkbenchRecipe(def)) {
                out.add(new ProducerEntry(def, outputAmount(def, content), is2x2Recipe(def)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private boolean isWorkbenchRecipe(EmiRecipe recipe) {
        Recipe<?> holder = recipe.getBackingRecipe();
        return holder instanceof CraftingRecipe;
    }

    /** 客户端 2×2 判定：Shapeless 输入≤4 / Shaped 宽高≤2；非工作台配方返回 false。 */
    private boolean is2x2Recipe(EmiRecipe recipe) {
        Recipe<?> holder = recipe.getBackingRecipe();
        if (!(holder instanceof CraftingRecipe crafting)) {
            return false;
        }
        if (crafting instanceof ShapelessRecipe shapeless) {
            return shapeless.getIngredients().size() <= 4;
        }
        if (crafting instanceof ShapedRecipe shaped) {
            return shaped.getWidth() <= 2 && shaped.getHeight() <= 2;
        }
        return false;
    }

    /** 客户端：3×3 合成可用（玩家**打开工作台界面**，或 **AE2 合成终端**——其合成格为 3×3，
     *  与工作台等价；与服务端一致）。 */
    private boolean hasCraftingMenuOpen() {
        try {
            Player player = Minecraft.getInstance().player;
            if (player == null) {
                return false;
            }
            return player.containerMenu instanceof CraftingMenu
                    || Ae2Support.isCraftingTermMenu(player.containerMenu);
        } catch (Exception ignored) {
        }
        return false;
    }

    private static class Agg {
        final EmiIngredient content;
        EmiRecipe recipe;
        long need;
        long batch;
        int depth;
        /** 到达该材料的祖先配方集（EMI 式防环：路径上配方重复即剪枝）。 */
        Set<EmiRecipe> path = new HashSet<>();

        Agg(EmiIngredient content, EmiRecipe recipe) {
            this.content = content;
            this.recipe = recipe;
        }
    }

    private void addNeed(Map<String, Agg> aggs, ArrayDeque<String> queue, EmiIngredient content, long delta, int depth,
            Set<EmiRecipe> forbidden) {
        if (content == null || content.isEmpty() || delta <= 0) {
            return;
        }
        // 材料 content 保留蜜蜂 EmiStack（树显示蜜蜂本体；key 匹配由 key() 统一蜜蜂↔蜂笼）
        String key = key(content);
        Agg agg = aggs.get(key);
        if (agg == null) {
            // 创建：叶子判断用"本次需要量"（不是单次 getAmount，避免"背包有 1 个木板就不拆解"）
            agg = new Agg(content, findRecipe(content, delta, forbidden));
            agg.need = delta;
            agg.depth = depth;
            agg.path = new HashSet<>(forbidden);
            aggs.put(key, agg);
        } else {
            long oldNeed = agg.need;
            agg.need += delta;
            agg.depth = Math.min(agg.depth, depth);
            // 解叶子化：原来是叶子（recipe==null），但累加后总需要量已超过库存 → 重新找配方并展开
            if (agg.recipe == null && !hasEnough(content, agg.need)) {
                EmiRecipe r = findRecipe(content, agg.need, agg.path);
                if (r != null) {
                    agg.recipe = r;
                    agg.batch = 0; // 强制重新计算批次并展开子材料
                    queue.add(key);
                }
            }
        }
        queue.add(key);
    }

    /** 目标节点：用显式配方创建（不经过 findRecipe，目标不需要默认配方）。need = 目标份数 target（≥1）。 */
    private void addGoal(Map<String, Agg> aggs, ArrayDeque<String> queue, EmiIngredient content, EmiRecipe recipe,
            long target) {
        if (content == null || content.isEmpty()) {
            return;
        }
        String key = key(content);
        Agg agg = new Agg(content, recipe);
        agg.need = Math.max(1, target);
        agg.depth = 0;
        aggs.put(key, agg);
        queue.add(key);
    }

    /** 该材料在目标配方直接输入中的总消耗量（同材料聚合 × 目标批次）；不是直接输入则返回 -1。 */
    private long directAmountOf(Agg goalAgg, Agg agg) {
        if (goalAgg.recipe == null) {
            return -1;
        }
        String k = key(agg.content);
        long total = 0;
        boolean found = false;
        for (EmiIngredient input : goalAgg.recipe.getInputs()) {
            if (key(input).equals(k)) {
                total += input.getAmount() * goalAgg.batch;
                found = true;
            }
        }
        return found ? total : -1;
    }

    /** 配方产出该材料的单次数量；无配方时返回 0。标签内容：输出为其成员时也算匹配。 */
    private long outputAmount(EmiRecipe recipe, EmiIngredient content) {
        if (recipe == null) {
            return 0;
        }
        String k = key(content);
        for (EmiStack out : recipe.getOutputs()) {
            if (key(out).equals(k)) {
                return Math.max(1, out.getAmount());
            }
        }
        // 标签：解析配方输出的是具体成员（如橡木木板），与其任一成员匹配即可
        if (content instanceof TagEmiIngredient) {
            for (EmiStack out : recipe.getOutputs()) {
                ItemStack outItem = out.getItemStack();
                if (outItem.isEmpty()) {
                    continue;
                }
                for (EmiStack member : content.getEmiStacks()) {
                    ItemStack m = member.getItemStack();
                    if (!m.isEmpty() && ItemStack.isSameItem(m, outItem)) {
                        return Math.max(1, out.getAmount());
                    }
                }
            }
        }
        return 0;
    }

    /** 产出某材料的配方：**只用玩家设为默认 / EMI 数据驱动默认的配方**（BoM.getRecipe 已处理取消默认；
     *  分解类默认由 EMI 默认数据本身避免，运行时用**祖先配方栈**（forbidden）剪环）；
     *  **当前屏幕库存已有足够该材料（≥ needAmount 总需要量）时视为叶子（不拆解）**（含蜜蜂：
     *  有蜂笼装的蜜蜂就不拆）。needAmount 用"该材料累计总需要量"而非单次 getAmount。
     *  **Productive Bees 蜜蜂不做回退拆解**：产蜜蜂的配方除繁殖外还有蜂巢获取/转化等（输入巨大、
     *  数量爆炸），且"由哪种蜜蜂繁殖"已由目标配方的直接输入行展示——蜜蜂材料无默认配方即成叶子。 */
    private EmiRecipe findRecipe(EmiIngredient content, long needAmount, Set<EmiRecipe> forbidden) {
        // 库存 ≥ 总需要量 → 叶子（不需要再合成；蜜蜂也如此——已有的就不拆分）
        if (needAmount > 0 && hasEnough(content, needAmount)) {
            return null;
        }
        try {
            EmiRecipe r = BoM.getRecipe(content);
            if (r != null && !forbidden.contains(r)) {
                return r;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 目标（最终结果）的"最后一步配方"：加入时查看的配方 → BoM 默认 → 回退正向产出配方。不需要默认配方。 */
    private EmiRecipe resolveGoalRecipe(EmiIngredient goal, ResourceLocation goalRecipeId) {
        if (goalRecipeId != null) {
            try {
                EmiRecipeManager m = EmiApi.getRecipeManager();
                if (m != null) {
                    EmiRecipe r = m.getRecipe(goalRecipeId);
                    if (r != null && !isReverse(r, goal)) {
                        return r;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        try {
            EmiRecipe r = BoM.getRecipe(goal);
            if (r != null && !isReverse(r, goal)) {
                return r;
            }
        } catch (Exception ignored) {
        }
        return firstForwardRecipe(goal);
    }

    /** 回退：产出该材料的"正向"配方（跳过分解类，如金块→金锭、铁块↔铁锭）。 */
    private EmiRecipe firstForwardRecipe(EmiIngredient content) {
        EmiRecipeManager m = EmiApi.getRecipeManager();
        if (m == null) {
            return null;
        }
        List<EmiStack> stacks = content.getEmiStacks();
        if (stacks.isEmpty()) {
            return null;
        }
        EmiStack stack = stacks.get(0);
        EmiRecipe first = null;
        for (EmiRecipe r : m.getRecipesByOutput(stack)) {
            if (isReverse(r, content)) {
                continue;
            }
            if ("minecraft:crafting".equals(r.getCategory().getId().toString())) {
                return r;
            }
            if (first == null) {
                first = r;
            }
        }
        return first;
    }

    /** 该配方是否"反向/分解"类：**输出数量>输入数量**（把块拆成组件，如 铁块→9铁锭、红石块→9红石）
     *  且**可逆**（该输入能被本材料再合成，如 铁锭→铁块 存在）→ 视为分解，跳过以避免无限相加。
     *  不误判正向配方（如 9铁锭→铁块 输出1<输入9；log→4木板 虽输出多但不可逆）。 */
    private boolean isReverse(EmiRecipe recipe, EmiIngredient content) {
        try {
            long outAmt = outputAmount(recipe, content);
            if (outAmt <= 0) {
                return false;
            }
            long inAmt = 0;
            for (EmiIngredient input : recipe.getInputs()) {
                inAmt += input.getAmount();
            }
            if (inAmt <= 0 || outAmt <= inAmt) {
                return false; // 输出不多于输入 → 不是分解方向
            }
            EmiRecipeManager m = EmiApi.getRecipeManager();
            if (m == null) {
                return false;
            }
            List<EmiStack> stacks = content.getEmiStacks();
            if (stacks.isEmpty()) {
                return false;
            }
            List<EmiRecipe> using = m.getRecipesByInput(stacks.get(0));
            if (using.isEmpty()) {
                return false;
            }
            for (EmiIngredient input : recipe.getInputs()) {
                for (EmiRecipe r2 : using) {
                    for (EmiStack out : r2.getOutputs()) {
                        if (key(out).equals(key(input))) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** 材料去重键：标签用 tag:id；物品/流体用其资源 id + 组件哈希（区分不同 NBT 物品如药水）。
     *  Productive Bees 蜜蜂（虚拟 BeeEmiStack 或蜂笼物品）统一用 {@code bee:<品种>}——不同品种分开。 */
    private String key(EmiIngredient content) {
        if (content == null) {
            return "";
        }
        if (content instanceof TagEmiIngredient tag) {
            ResourceLocation loc = tag.key.location();
            return loc == null ? "tag:?" : "tag:" + loc;
        }
        List<EmiStack> stacks = content.getEmiStacks();
        if (stacks.isEmpty()) {
            return "";
        }
        EmiStack first = stacks.get(0);
        // Productive Bees 蜜蜂品种 EmiStack（getItemStack 空）
        ResourceLocation beeType = ProductiveBeesSupport.beeTypeOf(first);
        if (beeType != null) {
            return "bee:" + beeType;
        }
        ResourceLocation id = first.getId();
        String base = id == null ? "" : id.toString();
        ItemStack item = first.getItemStack();
        if (item != null && !item.isEmpty()) {
            // 蜂笼物品（装 Productive Bees 蜜蜂）→ 与蜜蜂品种同 key
            ResourceLocation cageBee = ProductiveBeesSupport.beeTypeOfStack(item);
            if (cageBee != null) {
                return "bee:" + cageBee;
            }
            // 同 id 不同 NBT（不同药水/时长/附魔）分开，避免自环死循环。
            // 必须用 StackCompat.hashStack（含 NBT），不能用 item.hashCode()（身份哈希，每次不同）
            return base + "#" + Integer.toHexString(StackCompat.hashStack(item));
        }
        return base;
    }

    private static long ceilDiv(long a, long b) {
        return b <= 0 ? a : (a + b - 1) / b;
    }

    private ItemStack firstStack(EmiIngredient ingredient) {
        if (ingredient == null) {
            return ItemStack.EMPTY;
        }
        try {
            List<EmiStack> stacks = ingredient.getEmiStacks();
            if (stacks.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = stacks.get(0).getItemStack();
            return stack == null ? ItemStack.EMPTY : stack.copy();
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
