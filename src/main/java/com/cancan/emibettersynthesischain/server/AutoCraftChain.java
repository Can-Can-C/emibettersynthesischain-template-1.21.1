package com.cancan.emibettersynthesischain.server;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.cancan.emibettersynthesischain.Config;
import com.cancan.emibettersynthesischain.network.AutoCraftResultPayload;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * **可见自动合成链**：把目标 + 每一步中间材料都摆进玩家**当前打开的工作台/背包合成格**，带延时逐步显示
 * （像鼠标宏，不移动鼠标）。每步：材料进格 → 显示 → 取结果 → 清格 → 下一步。服务端权威校验；
 * 玩家中途关界面/动格子则中止。无可用合成格时由调用方回退到不可见的临时格合成。
 */
@EventBusSubscriber
public final class AutoCraftChain {
    private static final int MAX_STEPS = 128;

    /** 显示/间隔时长从 Config 读取（EMI 设置页可调）。 */
    private static int showTicks() {
        return Math.max(2, Config.AUTO_CRAFT_SHOW_TICKS.get());
    }

    private static int gapTicks() {
        return Math.max(0, Config.AUTO_CRAFT_GAP_TICKS.get());
    }

    private static final Map<UUID, AutoCraftChain> ACTIVE = new HashMap<>();

    private final ServerPlayer player;
    private final CraftingRecipe goalRecipe;
    private final Map<ResourceLocation, ResourceLocation> preferred;
    private final CraftingContainer grid;
    private final boolean grid3x3;
    private final boolean repeat;

    private CraftingRecipe currentRecipe;
    private boolean currentIsGoal;
    private int countdown;
    private int steps;
    private State state;
    private boolean done;

    private enum State { SHOW, GAP }

    private AutoCraftChain(ServerPlayer player, CraftingRecipe goalRecipe,
            Map<ResourceLocation, ResourceLocation> preferred, CraftingContainer grid, boolean grid3x3,
            boolean repeat) {
        this.player = player;
        this.goalRecipe = goalRecipe;
        this.preferred = preferred;
        this.grid = grid;
        this.grid3x3 = grid3x3;
        this.repeat = repeat;
    }

    /** 尝试开始可见自动合成链；配方不合法 / 无可见合成格 / 目标 3×3 但格不够大 → 返回 false（回退不可见方式）。 */
    static boolean start(ServerPlayer player, ResourceLocation recipeId,
            Map<ResourceLocation, ResourceLocation> preferred, boolean repeat) {
        Optional<RecipeHolder<?>> holder = player.serverLevel().getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof CraftingRecipe goal)) {
            return false;
        }
        if (ACTIVE.containsKey(player.getUUID())) {
            return true; // 已有进行中的链，忽略本次请求
        }
        CraftingContainer grid = findGrid(player.containerMenu);
        if (grid == null) {
            return false; // 无可见合成格（如箱子）→ 不可见方式
        }
        boolean grid3x3 = grid.getWidth() >= 3 && grid.getHeight() >= 3;
        if (!AutoCraftHandler.is2x2(goal) && !grid3x3) {
            return false; // 目标 3×3 需 3×3 工作台格（与"打开工作台界面"一致）
        }
        AutoCraftChain c = new AutoCraftChain(player, goal, preferred, grid, grid3x3, repeat);
        ACTIVE.put(player.getUUID(), c);
        c.clearGridToInventory();
        c.placeNext();
        if (c.done) {
            ACTIVE.remove(player.getUUID()); // 开局即无法推进（材料需非工作台步骤）→ 直接移除，不残留
        }
        return true;
    }

    /** 从打开菜单里找可见的合成格（工作台 3×3 / 背包 2×2），没有则 null。 */
    static CraftingContainer findGrid(AbstractContainerMenu menu) {
        for (Slot s : menu.slots) {
            if (s.container instanceof CraftingContainer cc) {
                return cc;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<AutoCraftChain> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            AutoCraftChain c = it.next();
            // 先查 done：placeNext 无法推进时可能没初始化 state，不能进入 tick
            if (c.done || !c.isValid()) {
                it.remove();
                continue;
            }
            c.tick();
            if (c.done) {
                it.remove();
            }
        }
    }

    /** 玩家还开着同一张合成界面（同一网格）才继续；关界面/换菜单即中止（格子物品由原版归还）。 */
    private boolean isValid() {
        return !player.isRemoved() && findGrid(player.containerMenu) == this.grid;
    }

    private void tick() {
        if (state == null || done) {
            done = true; // 防御：状态未初始化即中止
            return;
        }
        if (--countdown > 0) {
            return;
        }
        switch (state) {
            case SHOW:
                takeShown();
                break;
            case GAP:
                placeNext();
                break;
        }
    }

    /** 决定下一步并摆入合成格 → SHOW 倒计时。 */
    private void placeNext() {
        if (steps >= MAX_STEPS) {
            done = true;
            return;
        }
        CraftingRecipe recipe = pickNext();
        if (recipe == null) {
            sendFail("无法自动合成：材料不足");
            done = true; // 无法推进（材料链断了）→ 中止
            return;
        }
        if (!AutoCraftHandler.placeInGrid(player, recipe, grid)) {
            AutoCraftHandler.returnGridToInventory(player, grid);
            done = true;
            return;
        }
        player.containerMenu.broadcastChanges();
        currentRecipe = recipe;
        currentIsGoal = (recipe == goalRecipe);
        steps++;
        state = State.SHOW;
        countdown = showTicks();
    }

    /** 显示结束：取结果（产物入背包、余料返还、清格）。目标步发成功提示；**默认 V 得最终结果即停**，Shift+V 继续。 */
    private void takeShown() {
        ItemStack result = AutoCraftHandler.takeResult(player, currentRecipe, grid);
        player.containerMenu.broadcastChanges();
        if (result.isEmpty()) {
            done = true; // 取不到（玩家动了格子/无产物）→ 中止
            return;
        }
        if (currentIsGoal) {
            PacketDistributor.sendToPlayer(player,
                    new AutoCraftResultPayload("合成成功: " + result.getHoverName().getString(), true));
            if (!repeat) {
                done = true; // 得到最终结果即停
                return;
            }
        }
        state = State.GAP;
        countdown = gapTicks();
    }

    private void sendFail(String text) {
        PacketDistributor.sendToPlayer(player, new AutoCraftResultPayload(text, false));
    }

    /** 下一步合成什么：目标材料齐 → 合目标；否则先合某个缺的中间材料（最深处先合，一批一合）。 */
    private CraftingRecipe pickNext() {
        List<Ingredient> goalIngs = AutoCraftHandler.nonEmpty(goalRecipe.getIngredients());
        if (AutoCraftHandler.canAfford(player, goalIngs)) {
            return goalRecipe;
        }
        for (Ingredient ing : goalIngs) {
            if (AutoCraftHandler.countMatching(player, ing) < AutoCraftHandler.needed(goalIngs, ing)) {
                return findProducerToCraft(ing, 0);
            }
        }
        return goalRecipe;
    }

    /** 返回"现在就能合成一个"的配方（最深处缺的材料先合，其子材料递归可得）；不可得返回 null。 */
    private CraftingRecipe findProducerToCraft(Ingredient ing, int depth) {
        if (depth > 6) {
            return null;
        }
        for (CraftingRecipe producer : AutoCraftHandler.findProducers(player, ing, preferred)) {
            if (!grid3x3 && !AutoCraftHandler.is2x2(producer)) {
                continue; // 2×2 格放不下 3×3 配方
            }
            boolean unusable = false;
            for (Ingredient sub : producer.getIngredients()) {
                if (sub.isEmpty()) {
                    continue;
                }
                int subNeed = AutoCraftHandler.needed(producer.getIngredients(), sub);
                if (AutoCraftHandler.countMatching(player, sub) < subNeed) {
                    CraftingRecipe subProducer = findProducerToCraft(sub, depth + 1);
                    if (subProducer == null) {
                        unusable = true; // 子材料搞不到 → 此配方不可用，试下一个
                        break;
                    }
                    return subProducer; // 先合成缺的子材料（最深处）
                }
            }
            if (!unusable) {
                return producer; // 子材料都齐 → 合成这个
            }
        }
        return null;
    }

    /** 开链前把格子里已有的物品放回背包（像先把台面清空）。 */
    private void clearGridToInventory() {
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack s = grid.getItem(i);
            if (!s.isEmpty()) {
                grid.setItem(i, ItemStack.EMPTY);
                if (!player.getInventory().add(s)) {
                    player.drop(s, false);
                }
            }
        }
        player.containerMenu.broadcastChanges();
    }
}
