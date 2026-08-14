package com.cancan.emibettersynthesischain.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cancan.emibettersynthesischain.Config;
import com.cancan.emibettersynthesischain.EMIBettersynthesischain;
import com.cancan.emibettersynthesischain.client.IEmiInternal;
import com.cancan.emibettersynthesischain.client.InternalHelperImpl;
import com.cancan.emibettersynthesischain.client.TreeMode;
import com.cancan.emibettersynthesischain.client.TreeRenderer;
import com.cancan.emibettersynthesischain.mixin.AbstractContainerScreenAccessor;
import com.cancan.emibettersynthesischain.network.AutoCraftPayload;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 自动合成客户端：悬停物品 + 快捷键（原始按键事件，仿 EMI 的绑定检测）→ 找"产出该物品的工作台配方" → 发包。
 */
public final class AutoCraftClient {
    public static final KeyMapping KEY = new KeyMapping(
            "key.emibettersynthesischain.auto_craft", org.lwjgl.glfw.GLFW.GLFW_KEY_V,
            "category.emibettersynthesischain");

    private AutoCraftClient() {
    }

    /** 仿 EMI：事件驱动，比较原始 keyCode（不用 KeyMapping.consumeClick，其在屏幕内不可靠）。
     *  V=合成一次（得最终结果即停）；Shift+V=连续合成（一直合到材料用完）。 */
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            return;
        }
        if (event.getKey() != KEY.getKey().getValue()) {
            return;
        }
        int mods = event.getModifiers();
        boolean repeat = mods == org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT;
        boolean plain = mods == 0;
        if (!plain && !repeat) {
            return; // 只响应 V 或 Shift+V，排除 Ctrl+V 等
        }
        EMIBettersynthesischain.LOGGER.info("EBS auto-craft key pressed{}", repeat ? " (repeat)" : "");
        attemptAutoCraft(repeat);
    }

    private static void attemptAutoCraft(boolean repeat) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.screen instanceof AbstractContainerScreen<?>)) {
            return;
        }
        if (mc.screen.getFocused() instanceof EditBox) {
            return; // 正在输入文字，不触发
        }
        ItemStack hovered = getHoveredStack();
        EMIBettersynthesischain.LOGGER.info("EBS auto-craft hovered: {}", hovered.isEmpty() ? "EMPTY" : hovered.toString());
        if (hovered.isEmpty()) {
            showFail("请先悬停要合成的物品");
            return;
        }
        EmiRecipe recipe = findCraftingRecipe(hovered);
        RecipeHolder<?> holder = recipe == null ? null : recipe.getBackingRecipe();
        if (holder == null || !(holder.value() instanceof CraftingRecipe crafting)) {
            showFail("该物品无法自动合成（仅工作台配方）");
            return;
        }
        // 3×3 目标需**打开工作台界面**（服务端同样校验 containerMenu 为 CraftingMenu）
        if (!is2x2Craft(crafting) && !(mc.screen instanceof CraftingScreen)) {
            showFail("3×3 配方需打开工作台界面");
            return;
        }
        EMIBettersynthesischain.LOGGER.info("EBS auto-craft sending recipe {}", holder.id());
        PacketDistributor.sendToServer(new AutoCraftPayload(holder.id(), collectPreferredProducers(recipe), repeat));
    }

    /** 无法合成的红字提示（可在 EMI 设置页关闭）。 */
    private static void showFail(String text) {
        if (Config.AUTO_CRAFT_FAILURE_MESSAGES.get()) {
            MessageOverlay.show(Component.literal(text), false);
        }
    }

    /** 是否 2×2 配方（Shapeless 输入≤4 / Shaped 宽高≤2）；非工作台配方返回 false。 */
    private static boolean is2x2Craft(CraftingRecipe crafting) {
        if (crafting instanceof ShapelessRecipe shapeless) {
            return shapeless.getIngredients().size() <= 4;
        }
        if (crafting instanceof ShapedRecipe shaped) {
            return shaped.getWidth() <= 2 && shaped.getHeight() <= 2;
        }
        return false;
    }

    /** 收集链上各材料的默认配方（客户端 EMI BoM，尊重玩家设的默认）→ "材料键 → 配方 id"。 */
    private static Map<ResourceLocation, ResourceLocation> collectPreferredProducers(EmiRecipe goalRecipe) {
        Map<ResourceLocation, ResourceLocation> out = new HashMap<>();
        collectPreferred(goalRecipe, out, 0);
        return out;
    }

    private static void collectPreferred(EmiRecipe recipe, Map<ResourceLocation, ResourceLocation> out, int depth) {
        if (depth > 6) {
            return;
        }
        for (EmiIngredient input : recipe.getInputs()) {
            if (input == null || input.isEmpty()) {
                continue;
            }
            ResourceLocation key = ingredientKey(input);
            if (key == null || out.containsKey(key)) {
                continue;
            }
            EmiRecipe producer = null;
            try {
                producer = BoM.getRecipe(input);
            } catch (Exception ignored) {
            }
            if (producer == null || !isWorkbenchCraft(producer)) {
                continue;
            }
            RecipeHolder<?> holder = producer.getBackingRecipe();
            if (holder != null) {
                out.put(key, holder.id());
                collectPreferred(producer, out, depth + 1);
            }
        }
    }

    /** 材料键（与服务端一致）：标签用 tag:id，否则物品注册表 id。 */
    private static ResourceLocation ingredientKey(EmiIngredient input) {
        try {
            if (input instanceof TagEmiIngredient tag) {
                ResourceLocation loc = tag.key.location();
                return loc == null ? null : loc;
            }
            List<EmiStack> stacks = input.getEmiStacks();
            if (!stacks.isEmpty()) {
                ItemStack s = stacks.get(0).getItemStack();
                if (s != null && !s.isEmpty()) {
                    return BuiltInRegistries.ITEM.getKey(s.getItem());
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static ItemStack getHoveredStack() {
        Minecraft mc = Minecraft.getInstance();
        // 1) 树模式：鼠标在树节点上 → 用该节点物品（最终产物/中间材料均可自动合成）
        if (TreeMode.isActive()) {
            ItemStack treeStack = getTreeHoveredStack();
            if (!treeStack.isEmpty()) {
                return treeStack;
            }
        }
        if (!(mc.screen instanceof AbstractContainerScreen<?> container)) {
            return ItemStack.EMPTY;
        }
        double scale = mc.getWindow().getGuiScale();
        int mx = (int) Math.round(mc.mouseHandler.xpos() / scale);
        int my = (int) Math.round(mc.mouseHandler.ypos() / scale);
        EMIBettersynthesischain.LOGGER.info("EBS auto-craft mouse at ({},{})", mx, my);
        // 2) 仿 EMI keyPressed：用当前鼠标坐标取 EMI 悬停
        try {
            EmiStackInteraction inter = EmiScreenManager.getHoveredStack(mx, my, true, false);
            if (inter != null && !inter.isEmpty()) {
                var stacks = inter.getStack().getEmiStacks();
                if (!stacks.isEmpty()) {
                    ItemStack s = stacks.get(0).getItemStack();
                    if (s != null && !s.isEmpty()) {
                        return s;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        // 3) 兜底：直接遍历容器菜单槽位
        try {
            AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) (Object) container;
            int leftPos = acc.ebs$leftPos();
            int topPos = acc.ebs$topPos();
            for (Slot slot : container.getMenu().slots) {
                int sx = leftPos + slot.x;
                int sy = topPos + slot.y;
                if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16 && slot.hasItem()) {
                    return slot.getItem();
                }
            }
        } catch (Exception ignored) {
        }
        return ItemStack.EMPTY;
    }

    /** 树模式下，鼠标所在树节点对应的物品（最终产物/材料；标签取首个成员；流体返回空）。 */
    private static ItemStack getTreeHoveredStack() {
        try {
            IEmiInternal helper = InternalHelperImpl.INSTANCE;
            Bounds bounds = helper.getFavoritesPanelBounds();
            if (bounds == null) {
                return ItemStack.EMPTY;
            }
            Minecraft mc = Minecraft.getInstance();
            double scale = mc.getWindow().getGuiScale();
            int mx = (int) Math.round(mc.mouseHandler.xpos() / scale);
            int my = (int) Math.round(mc.mouseHandler.ypos() / scale);
            TreeRenderer.Hit hit = TreeRenderer.hitTest(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    helper.buildTrees(), mx, my);
            if (hit == null || hit.content() == null || hit.content().isEmpty()) {
                return ItemStack.EMPTY;
            }
            // 已解析标签 → 用玩家选择的物品
            if (hit.resolvedTo() != null && !hit.resolvedTo().isEmpty()) {
                var rs = hit.resolvedTo().getEmiStacks();
                if (!rs.isEmpty()) {
                    ItemStack s = rs.get(0).getItemStack();
                    if (s != null && !s.isEmpty() && findCraftingRecipe(s) != null) {
                        return s;
                    }
                }
            }
            // 否则：优先取"材料够"的成员，其次第一个有工作台配方的成员（标签如"任意木板"）
            ItemStack fallback = ItemStack.EMPTY;
            var stacks = hit.content().getEmiStacks();
            for (var es : stacks) {
                ItemStack s = es.getItemStack();
                if (s == null || s.isEmpty()) {
                    continue;
                }
                EmiRecipe r = findCraftingRecipe(s);
                if (r == null) {
                    continue;
                }
                if (canCraftRecipe(r)) {
                    return s;
                }
                if (fallback.isEmpty()) {
                    fallback = s;
                }
            }
            return fallback;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static EmiRecipe findCraftingRecipe(ItemStack stack) {
        EmiStack emi = EmiStack.of(stack);
        // 玩家"设为默认配方"优先（尊重其选择）
        try {
            EmiRecipe def = BoM.getRecipe(emi);
            if (def != null && isWorkbenchCraft(def)) {
                return def;
            }
        } catch (Exception ignored) {
        }
        EmiRecipeManager manager = EmiApi.getRecipeManager();
        if (manager == null) {
            return null;
        }
        for (EmiRecipe r : manager.getRecipesByOutput(emi)) {
            if (isWorkbenchCraft(r)) {
                return r;
            }
        }
        return null;
    }

    private static boolean isWorkbenchCraft(EmiRecipe recipe) {
        RecipeHolder<?> holder = recipe.getBackingRecipe();
        return holder != null && holder.value() instanceof CraftingRecipe;
    }

    /** 客户端材料是否充足（只查材料，不管格子大小）。 */
    private static boolean canCraftRecipe(EmiRecipe recipe) {
        RecipeHolder<?> holder = recipe.getBackingRecipe();
        if (holder == null || !(holder.value() instanceof CraftingRecipe crafting)) {
            return false;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        Inventory inv = player.getInventory();
        int[] available = new int[inv.items.size()];
        for (int i = 0; i < inv.items.size(); i++) {
            available[i] = inv.items.get(i).getCount();
        }
        for (Ingredient ing : crafting.getIngredients()) {
            if (ing.isEmpty()) {
                continue;
            }
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
