package com.cancan.emibettersynthesischain.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.cancan.emibettersynthesischain.Config;
import com.cancan.emibettersynthesischain.EMIBettersynthesischain;
import com.cancan.emibettersynthesischain.client.IEmiInternal;
import com.cancan.emibettersynthesischain.client.InternalHelperImpl;
import com.cancan.emibettersynthesischain.client.TreeMode;
import com.cancan.emibettersynthesischain.client.TreeRenderer;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.client.event.InputEvent;

/**
 * 自动合成客户端：悬停物品 + 快捷键（原始按键事件，仿 EMI 的绑定检测）→ 找"产出该物品的工作台配方"
 * → 用当前界面的 EMI handler 启动纯客户端点击链（ClientCraftChain）。
 */
public final class AutoCraftClient {
    public static final KeyMapping KEY = new KeyMapping(
            "key.emibettersynthesischain.auto_craft", org.lwjgl.glfw.GLFW.GLFW_KEY_V,
            "category.emibettersynthesischain");

    /** 单次合成数量改为**每树独立**（存 TreeManager 每条目，会话内默认 1）。 */
    private static final int AMOUNT_MAX = 999;

    /** 最近一次树悬停命中的树 index（-1 = 未命中；V 键与 ± 键用它取该树数量）。 */
    private static int hoveredTreeIndex = -1;

    /** 调节**指定树**的单次合成数量：dir=±1；Ctrl=翻倍/减半，Shift=±10，否则 ±1。钳制 1-999。 */
    public static void adjustAmount(int treeIndex, int dir, int mods) {
        int old = TreeManager.INSTANCE.getAmount(treeIndex);
        int next;
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) {
            next = dir > 0 ? Math.min(AMOUNT_MAX, old * 2) : Math.max(1, (old + 1) / 2);
        } else if ((mods & GLFW.GLFW_MOD_SHIFT) != 0) {
            next = clampAmount(old + dir * 10);
        } else {
            next = clampAmount(old + dir);
        }
        TreeManager.INSTANCE.setAmount(treeIndex, next);
        if (next != old) {
            MessageOverlay.show(Component.literal("单次合成数量: " + next), true);
        }
    }

    private static int clampAmount(int v) {
        return Math.max(1, Math.min(AMOUNT_MAX, v));
    }

    private AutoCraftClient() {
    }

    /** 仿 EMI：事件驱动，比较原始 keyCode（不用 KeyMapping.consumeClick，其在屏幕内不可靠）。
     *  V=按当前数量合成（得 N 个最终结果即停）；Shift+V=连续合成（一直合到材料用完）；
     *  Ctrl+V=强制合成（尽力而为）；树模式下 +/-（Shift ±10 / Ctrl 翻倍减半）调节单次合成数量。 */
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        int key = event.getKey();
        Minecraft mc = Minecraft.getInstance();
        // 树模式下 +/- 调节**悬停树**的单次合成数量（搜索框输入时不触发）
        boolean plusMinus = key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_MINUS
                || key == GLFW.GLFW_KEY_KP_ADD || key == GLFW.GLFW_KEY_KP_SUBTRACT;
        if (TreeMode.isActive() && plusMinus && hoveredTreeIndex >= 0
                && (mc.screen == null || !(mc.screen.getFocused() instanceof EditBox))) {
            boolean up = key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD;
            adjustAmount(hoveredTreeIndex, up ? 1 : -1, event.getModifiers());
            return;
        }
        if (key != KEY.getKey().getValue()) {
            return;
        }
        int mods = event.getModifiers();
        boolean repeat = mods == GLFW.GLFW_MOD_SHIFT;
        boolean force = mods == GLFW.GLFW_MOD_CONTROL;
        boolean plain = mods == 0;
        if (!plain && !repeat && !force) {
            return; // 只响应 V / Shift+V / Ctrl+V，排除其它修饰组合
        }
        attemptAutoCraft(repeat, force);
    }

    private static void attemptAutoCraft(boolean repeat, boolean force) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.screen instanceof AbstractContainerScreen<?>)) {
            return;
        }
        if (mc.screen.getFocused() instanceof EditBox) {
            return; // 正在输入文字，不触发
        }
        ItemStack hovered = getHoveredStack();
        if (hovered.isEmpty()) {
            showFail("请先悬停要合成的物品");
            return;
        }
        EmiRecipe recipe = findCraftingRecipe(hovered);
        Recipe<?> holder = recipe == null ? null : recipe.getBackingRecipe();
        if (!(holder instanceof CraftingRecipe)) {
            showFail("该物品无法自动合成（仅工作台配方）");
            return;
        }
        // 纯客户端：需要当前界面有 EMI handler（工作台/背包/AE2 终端/注册过 handler 的模组机器）
        if (!hasHandler(recipe)) {
            showFail("该界面不支持自动合成");
            return;
        }
        // V 定量：传当前悬停树的"单次合成数量"（Shift+V 连续 / Ctrl+V 强制在链条内自动忽略数量限制）
        long target = hoveredTreeIndex >= 0 ? TreeManager.INSTANCE.getAmount(hoveredTreeIndex) : 1;
        ClientCraftChain.start(recipe, repeat, force, target);
    }

    /** 当前界面是否注册了支持该配方的 EMI handler（纯客户端可合成的前提）。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean hasHandler(EmiRecipe recipe) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen)) {
            return false;
        }
        return EmiRecipeFiller.getFirstValidHandler(recipe, (AbstractContainerScreen) mc.screen) != null;
    }

    /** 无法合成的红字提示（可在 EMI 设置页关闭）。 */
    private static void showFail(String text) {
        if (Config.AUTO_CRAFT_FAILURE_MESSAGES.get()) {
            MessageOverlay.show(Component.literal(text), false);
        }
    }

    private static ItemStack getHoveredStack() {
        // V 键只在**树模式下、悬停合成树最终产物**时生效；
        // 非树模式或任何其它位置（物品栏/收藏夹/EMI 悬停等）按 V 都无反应
        if (!TreeMode.isActive()) {
            return ItemStack.EMPTY;
        }
        return getTreeHoveredStack();
    }

    /** 树模式下，鼠标所在树节点对应的物品（最终产物/材料；标签取首个成员；流体返回空）。 */
    private static ItemStack getTreeHoveredStack() {
        try {
            IEmiInternal helper = InternalHelperImpl.INSTANCE;
            Bounds bounds = helper.getFavoritesPanelBounds();
            if (bounds == null) {
                hoveredTreeIndex = -1;
                return ItemStack.EMPTY;
            }
            Minecraft mc = Minecraft.getInstance();
            double scale = mc.getWindow().getGuiScale();
            int mx = (int) Math.round(mc.mouseHandler.xpos() / scale);
            int my = (int) Math.round(mc.mouseHandler.ypos() / scale);
            TreeRenderer.Hit hit = TreeRenderer.hitTest(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    helper.buildTrees(), mx, my);
            if (hit == null || hit.content() == null || hit.content().isEmpty()) {
                hoveredTreeIndex = -1;
                return ItemStack.EMPTY;
            }
            hoveredTreeIndex = hit.treeIndex();
            // V 键只对合成树中的**最终产物**（goal）有效——中间材料/副产物不可自动合成
            if (!hit.isGoal()) {
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
                if (CraftInventory.canCraft(r)) {
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
        Recipe<?> holder = recipe.getBackingRecipe();
        return holder instanceof CraftingRecipe;
    }
}
