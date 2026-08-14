package com.cancan.emibettersynthesischain.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cancan.emibettersynthesischain.network.AutoCraftPayload;
import com.cancan.emibettersynthesischain.network.AutoCraftResultPayload;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 自动合成服务端处理：**权威校验**（防作弊），仅处理工作台配方（CraftingRecipe），每次 1 个。
 *
 * <p>合成方式：**模拟玩家合成**——把材料从背包摆放进合成格，按原版合成逻辑取结果，余料/容器（如桶）
 * 放回背包。有可见合成格（打开工作台/背包）时用 {@link AutoCraftChain} **逐步显示**；否则回退到
 * 本类的不可见临时格合成。默认配方优先：客户端把"材料→默认配方"映射随请求发来。</p>
 */
public class AutoCraftHandler {
    private AutoCraftHandler() {
    }

    public static void handle(AutoCraftPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (!AutoCraftChain.start(player, payload.recipeId(), payload.preferredProducers(), payload.repeat())) {
                    performCraftInvisible(player, payload.recipeId(), payload.preferredProducers(), payload.repeat());
                }
            }
        });
    }

    /** 不可见回退：无可见合成格时，用临时合成格一次合成。repeat=true（Shift+V）则一直合到材料用完。 */
    private static void performCraftInvisible(ServerPlayer player, ResourceLocation recipeId,
            Map<ResourceLocation, ResourceLocation> preferred, boolean repeat) {
        Optional<RecipeHolder<?>> holder = player.serverLevel().getRecipeManager().byKey(recipeId);
        if (holder.isEmpty()) {
            return;
        }
        Recipe<?> recipe = holder.get().value();
        if (!(recipe instanceof CraftingRecipe crafting)) {
            sendFail(player, "无法自动合成：仅支持工作台配方");
            return;
        }
        if (!is2x2(crafting) && !hasCraftingMenuOpen(player)) {
            sendFail(player, "3×3 配方需打开工作台界面");
            return;
        }
        List<Ingredient> ingredients = nonEmpty(crafting.getIngredients());
        if (ingredients.isEmpty()) {
            return;
        }
        int count = 0;
        while (count < 64) {
            for (int attempt = 0; attempt < 12 && !canAfford(player, ingredients); attempt++) {
                boolean progressed = false;
                for (Ingredient ing : ingredients) {
                    if (ensureMaterialOnce(player, ing, needed(ingredients, ing), 0, preferred)) {
                        progressed = true;
                    }
                }
                if (!progressed) {
                    break;
                }
            }
            if (!canAfford(player, ingredients)) {
                break;
            }
            ItemStack result = craftInTransient(player, crafting);
            if (result.isEmpty()) {
                break;
            }
            PacketDistributor.sendToPlayer(player,
                    new AutoCraftResultPayload("合成成功: " + result.getHoverName().getString(), true));
            count++;
            if (!repeat) {
                break;
            }
        }
        if (count == 0) {
            sendFail(player, "无法自动合成：材料不足");
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    private static void sendFail(ServerPlayer player, String text) {
        PacketDistributor.sendToPlayer(player, new AutoCraftResultPayload(text, false));
    }

    private static boolean ensureMaterialOnce(ServerPlayer player, Ingredient ing, int required, int depth,
            Map<ResourceLocation, ResourceLocation> preferred) {
        if (depth > 6) {
            return false;
        }
        if (countMatching(player, ing) >= required) {
            return false;
        }
        for (CraftingRecipe producer : findProducers(player, ing, preferred)) {
            if (!is2x2(producer) && !hasCraftingMenuOpen(player)) {
                continue;
            }
            boolean subsOk = true;
            for (Ingredient sub : producer.getIngredients()) {
                if (sub.isEmpty()) {
                    continue;
                }
                int subNeeded = needed(producer.getIngredients(), sub);
                ensureMaterialOnce(player, sub, subNeeded, depth + 1, preferred);
                if (countMatching(player, sub) < subNeeded) {
                    subsOk = false;
                    break;
                }
            }
            if (!subsOk) {
                continue;
            }
            if (!craftInTransient(player, producer).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 一次性合成（供不可见回退）：临时格摆入 + 取结果。 */
    private static ItemStack craftInTransient(ServerPlayer player, CraftingRecipe recipe) {
        int size = is2x2(recipe) ? 2 : 3;
        TransientCraftingContainer container = new TransientCraftingContainer(player.containerMenu, size, size);
        return craftInGrid(player, recipe, container);
    }

    /** 摆入 + 取结果（共用底层，供 {@link AutoCraftChain} 分步使用）。 */
    static ItemStack craftInGrid(ServerPlayer player, CraftingRecipe recipe, CraftingContainer grid) {
        if (!placeInGrid(player, recipe, grid)) {
            returnGridToInventory(player, grid);
            return ItemStack.EMPTY;
        }
        return takeResult(player, recipe, grid);
    }

    /**
     * 把配方材料从背包摆进合成格（Shaped 按 pattern 左上角对齐；Shapeless/自定义按输入顺序填）。
     * 失败返回 false（已放入格子的材料保留，由调用方决定归还）。
     */
    static boolean placeInGrid(ServerPlayer player, CraftingRecipe recipe, CraftingContainer container) {
        int cols = container.getWidth();
        if (recipe instanceof ShapedRecipe shaped) {
            int rw = shaped.getWidth();
            int rh = shaped.getHeight();
            for (int r = 0; r < rh; r++) {
                for (int c = 0; c < rw; c++) {
                    Ingredient ing = shaped.getIngredients().get(r * rw + c);
                    if (ing.isEmpty()) {
                        continue;
                    }
                    ItemStack item = takeFromInventory(player, ing);
                    if (item.isEmpty()) {
                        return false;
                    }
                    container.setItem(r * cols + c, item);
                }
            }
        } else {
            int idx = 0;
            for (Ingredient ing : recipe.getIngredients()) {
                if (ing.isEmpty()) {
                    continue;
                }
                ItemStack item = takeFromInventory(player, ing);
                if (item.isEmpty()) {
                    return false;
                }
                container.setItem(idx++, item);
            }
        }
        return true;
    }

    /**
     * 取结果：校验格子仍匹配该配方 → assemble 产物 + 余料/容器放回背包 + 清格 + 产物入背包。
     * 格子被改动不再匹配时返回 EMPTY 且**不动格子**。
     */
    static ItemStack takeResult(ServerPlayer player, CraftingRecipe recipe, CraftingContainer grid) {
        CraftingInput input = CraftingInput.of(grid.getWidth(), grid.getHeight(), grid.getItems());
        if (!recipe.matches(input, player.serverLevel())) {
            return ItemStack.EMPTY;
        }
        ItemStack result = recipe.assemble(input, player.serverLevel().registryAccess());
        if (result.isEmpty()) {
            clearContainer(grid);
            return ItemStack.EMPTY;
        }
        NonNullList<ItemStack> remaining = recipe.getRemainingItems(input);
        for (ItemStack r : remaining) {
            if (!r.isEmpty() && !player.getInventory().add(r)) {
                player.drop(r, false);
            }
        }
        clearContainer(grid);
        if (!player.getInventory().add(result.copy())) {
            player.drop(result.copy(), false);
        }
        return result;
    }

    /** 把合成格里的物品全部放回背包（清格）。 */
    static void returnGridToInventory(ServerPlayer player, CraftingContainer grid) {
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack s = grid.getItem(i);
            if (!s.isEmpty()) {
                grid.setItem(i, ItemStack.EMPTY);
                if (!player.getInventory().add(s)) {
                    player.drop(s, false);
                }
            }
        }
    }

    /** 从背包取 1 个匹配材料（拆堆），没有则返回 EMPTY。 */
    private static ItemStack takeFromInventory(ServerPlayer player, Ingredient ing) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            if (!s.isEmpty() && ing.test(s)) {
                return s.split(1);
            }
        }
        return ItemStack.EMPTY;
    }

    private static void clearContainer(CraftingContainer container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, ItemStack.EMPTY);
        }
    }

    static List<Ingredient> nonEmpty(NonNullList<Ingredient> list) {
        List<Ingredient> out = new ArrayList<>();
        for (Ingredient ing : list) {
            if (!ing.isEmpty()) {
                out.add(ing);
            }
        }
        return out;
    }

    static int countMatching(ServerPlayer player, Ingredient ing) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) {
            if (!s.isEmpty() && ing.test(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** 配方中该材料需要的总数量 = 匹配它的输入槽位数（重叠槽各需一个物品，跨槽可共享计数）。 */
    static int needed(List<Ingredient> all, Ingredient ing) {
        int n = 0;
        for (Ingredient other : all) {
            if (overlaps(ing, other)) {
                n++;
            }
        }
        return n;
    }

    private static boolean overlaps(Ingredient a, Ingredient b) {
        ItemStack[] ai = a.getItems();
        ItemStack[] bi = b.getItems();
        for (ItemStack x : ai) {
            for (ItemStack y : bi) {
                if (ItemStack.isSameItemSameComponents(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 材料键（与客户端一致）：标签用 tag:id，否则物品注册表 id。 */
    private static ResourceLocation ingredientKey(Ingredient ing) {
        try {
            for (Ingredient.Value v : ing.getValues()) {
                if (v instanceof Ingredient.TagValue tv) {
                    return tv.tag().location();
                }
            }
            ItemStack[] items = ing.getItems();
            if (items.length > 0) {
                return BuiltInRegistries.ITEM.getKey(items[0].getItem());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 产出匹配该材料的所有工作台配方（标签材料：输出是任一成员即可）；跳过分解类（铁块→9铁锭）避免环；
     *  客户端设的**默认配方优先**。 */
    static List<CraftingRecipe> findProducers(ServerPlayer player, Ingredient ing,
            Map<ResourceLocation, ResourceLocation> preferred) {
        ServerLevel level = player.serverLevel();
        ResourceLocation key = ingredientKey(ing);
        List<CraftingRecipe> preferredList = new ArrayList<>();
        List<CraftingRecipe> rest = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> h : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe r = h.value();
            ItemStack result = r.getResultItem(level.registryAccess());
            if (result.isEmpty() || !ing.test(result)) {
                continue;
            }
            if (isDecomposition(player, r)) {
                continue;
            }
            if (preferred != null && key != null && h.id().equals(preferred.get(key))) {
                preferredList.add(r);
            } else {
                rest.add(r);
            }
        }
        preferredList.addAll(rest);
        return preferredList;
    }

    /** 该工作台配方是否"反向/分解"类：输出数量>输入数量，且可逆（存在配方用其产物再产出其输入，如 铁块→9铁锭）。 */
    private static boolean isDecomposition(ServerPlayer player, CraftingRecipe recipe) {
        ServerLevel level = player.serverLevel();
        ItemStack out = recipe.getResultItem(level.registryAccess());
        int inAmt = 0;
        for (Ingredient ing : recipe.getIngredients()) {
            if (!ing.isEmpty()) {
                inAmt++;
            }
        }
        if (inAmt <= 0 || out.isEmpty() || out.getCount() <= inAmt) {
            return false;
        }
        for (Ingredient input : recipe.getIngredients()) {
            if (input.isEmpty()) {
                continue;
            }
            for (RecipeHolder<CraftingRecipe> h : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                CraftingRecipe r2 = h.value();
                if (r2 == recipe) {
                    continue;
                }
                boolean usesOut = false;
                for (Ingredient r2in : r2.getIngredients()) {
                    if (!r2in.isEmpty() && r2in.test(out)) {
                        usesOut = true;
                        break;
                    }
                }
                if (!usesOut) {
                    continue;
                }
                ItemStack r2out = r2.getResultItem(level.registryAccess());
                if (!r2out.isEmpty() && input.test(r2out)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean is2x2(CraftingRecipe crafting) {
        if (crafting instanceof ShapelessRecipe shapeless) {
            return shapeless.getIngredients().size() <= 4;
        }
        if (crafting instanceof ShapedRecipe shaped) {
            return shaped.getWidth() <= 2 && shaped.getHeight() <= 2;
        }
        return false;
    }

    /** 3×3 合成需玩家**打开工作台界面**：containerMenu 为 CraftingMenu（打开工作台时服务端同步）。 */
    private static boolean hasCraftingMenuOpen(ServerPlayer player) {
        return player.containerMenu instanceof CraftingMenu;
    }

    /** 干跑：每个配方输入槽各匹配一个"有剩余数量"的物品（同一物品堆可跨槽共享计数，但每槽需消耗 1）。 */
    static boolean canAfford(ServerPlayer player, List<Ingredient> ingredients) {
        Inventory inv = player.getInventory();
        int[] available = new int[inv.items.size()];
        for (int i = 0; i < inv.items.size(); i++) {
            available[i] = inv.items.get(i).getCount();
        }
        for (Ingredient ing : ingredients) {
            boolean found = false;
            for (int i = 0; i < inv.items.size(); i++) {
                ItemStack s = inv.items.get(i);
                if (available[i] > 0 && !s.isEmpty() && ing.test(s)) {
                    available[i]--;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }
}
