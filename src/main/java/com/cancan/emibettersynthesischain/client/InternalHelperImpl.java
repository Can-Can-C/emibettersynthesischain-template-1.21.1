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
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
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
    /** 树重建时构建一次的库存快照：hasEnough/canObtain 全部复用，避免每次计数重建快照（性能热点）。 */
    private EmiPlayerInventory invSnapshot;
    /** 产出配方候选缓存：EMI 配方集合会话内静态，按材料键缓存（canObtain 递归热点）。 */
    private final Map<String, List<ProducerEntry>> producerCache = new HashMap<>();

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
            }
        }
        return getHoveredItemStack();
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
            List<TreeData> trees = new ArrayList<>();
            for (int i = 0; i < TreeManager.INSTANCE.size(); i++) {
                ItemStack item = TreeManager.INSTANCE.getItem(i);
                TreeData tree = buildTree(EmiStack.of(item), TreeManager.INSTANCE.getRecipeId(i));
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

    /** 玩家背包变化（或打开/关闭工作台等换菜单）时刷新树缓存（标红"能否获得"用最新状态）。 */
    private void maybeRefreshOnInventoryChange() {
        try {
            Player player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            int hash = 0;
            for (ItemStack s : player.getInventory().items) {
                if (!s.isEmpty()) {
                    // 组件感知：用 hashItemAndComponents（含 NBT），区分同 item 不同组件（药水/时长/附魔）
                    hash = hash * 31 + ItemStack.hashItemAndComponents(s);
                    hash = hash * 31 + s.getCount();
                }
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
     * excess = produced - need（&gt;0 即副产物）。目标节点 amount = 配方单次输出量。</p>
     */
    private TreeData buildTree(EmiIngredient goalIng, ResourceLocation goalRecipeId) {
        if (goalIng == null || goalIng.isEmpty()) {
            return null;
        }
        Map<String, Agg> aggs = new HashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        // 目标（最终结果）：用"加入时查看的配方"，不需要默认配方
        addGoal(aggs, queue, goalIng, resolveGoalRecipe(goalIng, goalRecipeId));
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
        EmiIngredient goalDisp = goalAgg.content.copy().setAmount(goalOutput);

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
        TreeData.TreeItem goalItem = new TreeData.TreeItem(goalDisp,
                canObtain(goalDisp, goalOutput, goalAgg.recipe, 0, new HashSet<>()), resolvedToFor(goalAgg));
        List<TreeData.TreeItem> directItems = new ArrayList<>();
        for (EmiIngredient input : directInputs) {
            Agg agg = aggs.get(key(input));
            directItems.add(new TreeData.TreeItem(input,
                    canObtain(input, input.getAmount(), agg == null ? null : agg.recipe, 0, new HashSet<>()),
                    resolvedToFor(agg)));
        }
        List<List<TreeData.TreeItem>> rowItems = new ArrayList<>();
        for (List<EmiIngredient> row : rows) {
            List<TreeData.TreeItem> items = new ArrayList<>();
            for (EmiIngredient ing : row) {
                Agg agg = aggs.get(key(ing));
                items.add(new TreeData.TreeItem(ing,
                        canObtain(ing, ing.getAmount(), agg == null ? null : agg.recipe, 0, new HashSet<>()),
                        resolvedToFor(agg)));
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
                    resolvedToFor(agg)));
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
            ItemStack item = firstStack(content);
            if (item.isEmpty()) {
                return true; // 流体/不可作为物品计数
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
     */
    private boolean canObtain(EmiIngredient content, long amount, EmiRecipe preferred, int depth,
            Set<EmiRecipe> ancestors) {
        if (depth > 6) {
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

    /** 产出该材料的**工作台**配方候选：首选 preferred（BoM 默认）前置，其余取缓存（EMI 配方集合会话内静态）。
     *  防环由祖先栈承担。preferred 不在缓存内（每次单独构建），其余配方按材料键缓存一次。 */
    private List<ProducerEntry> producersOf(EmiIngredient content, EmiRecipe preferred) {
        String k = key(content);
        List<ProducerEntry> cached = producerCache.get(k);
        if (cached == null) {
            cached = buildProducers(content);
            producerCache.put(k, cached);
        }
        if (preferred != null && isWorkbenchRecipe(preferred)) {
            List<ProducerEntry> out = new ArrayList<>();
            out.add(new ProducerEntry(preferred, outputAmount(preferred, content), is2x2Recipe(preferred)));
            for (ProducerEntry e : cached) {
                if (e.recipe() != preferred) {
                    out.add(e);
                }
            }
            return out;
        }
        return cached;
    }

    private List<ProducerEntry> buildProducers(EmiIngredient content) {
        List<ProducerEntry> out = new ArrayList<>();
        try {
            EmiRecipeManager m = EmiApi.getRecipeManager();
            if (m == null) {
                return out;
            }
            List<EmiStack> stacks = content.getEmiStacks();
            if (stacks.isEmpty()) {
                return out;
            }
            for (EmiRecipe r : m.getRecipesByOutput(stacks.get(0))) {
                if (!isWorkbenchRecipe(r)) {
                    continue;
                }
                long perBatch = outputAmount(r, content);
                if (perBatch <= 0) {
                    continue; // 该配方并不产出此材料（如标签候选中的其它配方）→ 对 canObtain 无意义
                }
                out.add(new ProducerEntry(r, perBatch, is2x2Recipe(r)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private boolean isWorkbenchRecipe(EmiRecipe recipe) {
        RecipeHolder<?> holder = recipe.getBackingRecipe();
        return holder != null && holder.value() instanceof CraftingRecipe;
    }

    /** 客户端 2×2 判定：Shapeless 输入≤4 / Shaped 宽高≤2；非工作台配方返回 false。 */
    private boolean is2x2Recipe(EmiRecipe recipe) {
        RecipeHolder<?> holder = recipe.getBackingRecipe();
        if (holder == null || !(holder.value() instanceof CraftingRecipe crafting)) {
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

    /** 客户端：3×3 合成需玩家**打开工作台界面**（containerMenu 为 CraftingMenu，与服务端一致）。 */
    private boolean hasCraftingMenuOpen() {
        try {
            Player player = Minecraft.getInstance().player;
            return player != null && player.containerMenu instanceof CraftingMenu;
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

    /** 目标节点：用显式配方创建（不经过 findRecipe，目标不需要默认配方）。 */
    private void addGoal(Map<String, Agg> aggs, ArrayDeque<String> queue, EmiIngredient content, EmiRecipe recipe) {
        if (content == null || content.isEmpty()) {
            return;
        }
        String key = key(content);
        Agg agg = new Agg(content, recipe);
        agg.need = 1;
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
     *  **当前屏幕库存已有足够该材料（≥ needAmount 总需要量）时视为叶子（不拆解）**。
     *  needAmount 用"该材料累计总需要量"而非单次 getAmount——否则背包有 1 个木板就不向下拆解。 */
    private EmiRecipe findRecipe(EmiIngredient content, long needAmount, Set<EmiRecipe> forbidden) {
        // 库存 ≥ 总需要量 → 叶子（不需要再合成）
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

    /** 材料去重键：标签用 tag:id；物品/流体用其资源 id + 组件哈希（区分不同 NBT 物品如药水）。 */
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
        ResourceLocation id = first.getId();
        String base = id == null ? "" : id.toString();
        ItemStack item = first.getItemStack();
        if (item != null && !item.isEmpty()) {
            // 同 id 不同组件（不同药水/时长/附魔）分开，避免自环死循环。
            // 必须用 hashItemAndComponents（含组件），不能用 item.hashCode()（身份哈希，每次不同）
            return base + "#" + Integer.toHexString(ItemStack.hashItemAndComponents(item));
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
