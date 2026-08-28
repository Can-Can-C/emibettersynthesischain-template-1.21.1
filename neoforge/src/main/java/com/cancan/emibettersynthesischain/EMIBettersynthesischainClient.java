package com.cancan.emibettersynthesischain;

import org.lwjgl.glfw.GLFW;

import com.cancan.emibettersynthesischain.client.AutoCraftClient;
import com.cancan.emibettersynthesischain.client.ClientCraftChain;
import com.cancan.emibettersynthesischain.client.IEmiInternal;
import com.cancan.emibettersynthesischain.client.InternalHelperImpl;
import com.cancan.emibettersynthesischain.client.TreeManager;
import com.cancan.emibettersynthesischain.client.TreeMode;
import com.cancan.emibettersynthesischain.client.TreeRenderer;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.screen.RecipeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = EMIBettersynthesischain.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = EMIBettersynthesischain.MODID, value = Dist.CLIENT)
public class EMIBettersynthesischainClient {
    /** 上一 tick 的屏幕：用于检测"本次点击是否刚打开/关闭了界面"（如右键方块打开容器）。 */
    private static Screen lastScreen;
    /** 界面切换标志：ScreenEvent.Opening/Closing 置位，下一次鼠标 PRESS 跳过树交互。
     *  根因：关闭界面时 EMI 侧边栏 bounds 可能残留旧值，下一次打开界面时 getFavoritesPanelBounds()
     *  返回过期面板 → 鼠标坐标（方块处）可能命中树图标误删。 */
    private static boolean screenTransition = false;

    public EMIBettersynthesischainClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(EMIBettersynthesischainClient::onMouseButton);
        NeoForge.EVENT_BUS.addListener(EMIBettersynthesischainClient::onKeyInput);
        NeoForge.EVENT_BUS.addListener(EMIBettersynthesischainClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(EMIBettersynthesischainClient::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(EMIBettersynthesischainClient::onScreenClosing);
    }

    static void onScreenOpening(ScreenEvent.Opening event) {
        screenTransition = true;
    }

    static void onScreenClosing(ScreenEvent.Closing event) {
        screenTransition = true;
    }

    static void onKeyInput(InputEvent.Key event) {
        AutoCraftClient.onKeyInput(event);
    }

    /** 纯客户端自动合成链的 tick 推进（点击序列、等待、下一步）。 */
    static void onClientTick(ClientTickEvent.Post event) {
        ClientCraftChain.tick();
        lastScreen = Minecraft.getInstance().screen;
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // 加载已加入的合成树集合（多树持久化）
        TreeManager.INSTANCE.load();
        EMIBettersynthesischain.LOGGER.info("EMI Better Synthesis Chain client ready");
    }

    @SubscribeEvent
    static void onKeyRegister(RegisterKeyMappingsEvent event) {
        event.register(AutoCraftClient.KEY);
        EMIBettersynthesischain.LOGGER.info("EBS auto-craft keybind registered");
    }

    /** 树模式收藏页交互：左键查配方/拖滚动条；右键点最终产物删树。 */
    static void onMouseButton(InputEvent.MouseButton.Post event) {
        if (!TreeMode.isActive()) {
            return;
        }
        IEmiInternal helper = InternalHelperImpl.INSTANCE;
        Bounds bounds = helper.getFavoritesPanelBounds();
        if (bounds == null) {
            return;
        }
        int mx = helper.getMouseX();
        int my = helper.getMouseY();

        // 松开左键：结束滚动条拖动（无论鼠标在哪，都必须 stopDrag，防止拖出面板后卡住拖动）
        if (event.getAction() == GLFW.GLFW_RELEASE && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            TreeMode.stopDrag();
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        // 界面切换后的第一次 PRESS：EMI 侧边栏 bounds 可能仍是过期值。
        // **左键放行**（点击开配方界面无害，直接响应——避免"打开背包要点两次"）；
        // **右键保留拦截**（删树高代价，防"右键方块打开容器瞬间 bounds 残留误删树"）。
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            screenTransition = false; // 左键=主动交互，界面已就绪，清除切换标志
            lastScreen = Minecraft.getInstance().screen;
        } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && (screenTransition || Minecraft.getInstance().screen != lastScreen)) {
            screenTransition = false;
            lastScreen = Minecraft.getInstance().screen;
            return; // 右键在界面切换期 → 跳过（防误删树）
        }
        // 鼠标不在收藏面板内（如右键点方块打开界面、左键点世界）→ 不处理树交互，避免误删树/误开配方
        if (!bounds.contains(mx, my)) {
            return;
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // 滚动条滑块拖动
            TreeRenderer.Scrollbar sb = TreeRenderer.scrollbarRect(bounds.x(), bounds.y(), bounds.width(),
                    bounds.height(), TreeMode.getScroll(), TreeMode.getMaxScroll());
            if (sb != null && mx >= sb.x() && mx < sb.x() + sb.width() && my >= sb.y() && my < sb.y() + sb.height()) {
                TreeMode.startDrag(my);
                return;
            }
            // 点击物品查询配方：标签材料 → 打开可"选择具体材料"的界面（RecipeScreen.resolve + displayRecipes）
            TreeRenderer.Hit hit = TreeRenderer.hitTest(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    helper.buildTrees(), mx, my);
            if (hit == null || hit.content() == null || hit.content().isEmpty()) {
                EMIBettersynthesischain.LOGGER.info("EBS click: no hit or empty content at ({},{})", mx, my);
                return;
            }
            EMIBettersynthesischain.LOGGER.info("EBS click: tree#{} content={} isEmpty={} isGoal={}", hit.treeIndex(),
                    hit.content(), hit.content().isEmpty(), hit.isGoal());
            if (hit.content() instanceof TagEmiIngredient) {
                RecipeScreen.resolve = hit.content();
                EmiApi.displayRecipes(hit.content());
            } else {
                EmiApi.displayRecipes(hit.content());
            }
        } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            TreeRenderer.Hit hit = TreeRenderer.hitTest(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    helper.buildTrees(), mx, my);
            if (hit != null && hit.isGoal()) {
                TreeManager.INSTANCE.remove(hit.treeIndex());
            }
        }
    }
}
