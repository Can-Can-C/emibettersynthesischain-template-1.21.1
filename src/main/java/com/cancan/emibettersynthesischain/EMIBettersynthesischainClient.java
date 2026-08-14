package com.cancan.emibettersynthesischain;

import org.lwjgl.glfw.GLFW;

import com.cancan.emibettersynthesischain.client.AutoCraftClient;
import com.cancan.emibettersynthesischain.client.IEmiInternal;
import com.cancan.emibettersynthesischain.client.InternalHelperImpl;
import com.cancan.emibettersynthesischain.client.TreeManager;
import com.cancan.emibettersynthesischain.client.TreeMode;
import com.cancan.emibettersynthesischain.client.TreeRenderer;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.screen.RecipeScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = EMIBettersynthesischain.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = EMIBettersynthesischain.MODID, value = Dist.CLIENT)
public class EMIBettersynthesischainClient {
    public EMIBettersynthesischainClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(EMIBettersynthesischainClient::onMouseButton);
        NeoForge.EVENT_BUS.addListener(EMIBettersynthesischainClient::onKeyInput);
    }

    static void onKeyInput(InputEvent.Key event) {
        AutoCraftClient.onKeyInput(event);
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

        // 松开左键：结束滚动条拖动
        if (event.getAction() == GLFW.GLFW_RELEASE && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            TreeMode.stopDrag();
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
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
                return;
            }
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
