package com.cancan.emibettersynthesischain.client;

import java.util.List;

import dev.emi.emi.api.widget.Bounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 本项目业务需要的 EMI 能力清单。
 *
 * <p>隔离策略：EMI 内部类（dev.emi.emi.screen.* / dev.emi.emi.bom.* 等）的引用
 * 只允许出现在 {@link InternalHelperImpl} 与 {@code mixin/} 包中；业务代码只依赖本接口
 * 与 {@link TreeData} 等纯业务模型。升级 EMI 版本时，只需调整实现类与 accessor mixin。</p>
 */
public interface IEmiInternal {
    /** 把侧边栏切换到"收藏"页（EMI {@code SidebarType.FAVORITES}）。 */
    void focusFavoritesSidebar();

    /** 当前鼠标悬停物品对应的 ItemStack（EMI {@code EmiApi.getHoveredStack} → ItemStack）。 */
    ItemStack getHoveredItemStack();

    /** 加入合成树的"目标物品"：配方查询界面（RecipeScreen）取当前配方产物，其余界面取悬停物品。 */
    ItemStack getAddTarget();

    /** 当前 EMI BoM 目标配方的 id（"最后一步配方"，目标拆解不需要默认）。 */
    ResourceLocation getGoalRecipeId();

    /** 收藏面板的屏幕坐标范围（不依赖具体面板对象）。 */
    Bounds getFavoritesPanelBounds();

    /** 当前鼠标屏幕坐标（EMI 记录的缩放坐标，与悬停检测一致）。 */
    int getMouseX();

    int getMouseY();

    /** 为 {@link TreeManager} 中所有已加入物品递归构建合成树（任意配方类型，含烧炼/机器等）。 */
    List<TreeData> buildTrees();

    /** 指定对象是否为"收藏"侧边栏面板。 */
    boolean isFavoritesPanel(Object panel);

    /** 指定面板的屏幕坐标范围。 */
    Bounds getPanelBounds(Object panel);

    /** 进入树模式：加宽左侧栏（保存原宽，退出时还原）。 */
    void widenFavoritesPanel();

    /** 退出树模式：还原左侧栏宽度。 */
    void restoreFavoritesPanel();
}
