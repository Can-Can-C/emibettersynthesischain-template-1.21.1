package com.cancan.emibettersynthesischain.client;

import java.util.List;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;

/**
 * 一棵合成树的布局数据。
 *
 * <ul>
 *   <li>{@code goal}：最终产物（amount = 配方输出量）</li>
 *   <li>{@code leafTotal}：顶部"底层总材料"行（当前各叶节点聚合法所需量）</li>
 *   <li>{@code directInputs}：目标配方直接输入（amount = 配方消耗量）</li>
 *   <li>{@code rows}：聚合材料行，最深→最浅</li>
 *   <li>{@code byproducts}：副产物（过量产出）</li>
 * </ul>
 * 节点带 {@link TreeItem#canCraft()}：玩家当前能否获得该节点——自身数量足够，或可由**工作台配方**链式合成
 * （含标签成员；非工作台步骤 / 3×3 无工作台不可合成）。无法获得则标红。
 *
 * <p>需求 16（未知节点语义）：节点携带 {@link TreeItem#state()}——{@link ResolveState#RESOLVED}（有产出配方，
 * 正常展开）；{@link ResolveState#LEAF_KNOWN}（无配方但可计数的物品叶子，如"库存已足不拆解"）；
 * {@link ResolveState#LEAF_UNKNOWABLE}（虚拟/不可转 ItemStack 的叶子，如流体/自定义栈——只显示不参与
 * 拥有量/标红/自动合成）。需求量永远来自父配方输入量（等价 EMI TreeCost recipe==null 分支 addCost），
 * S 总材料照常计入。</p>
 */
public record TreeData(
        TreeItem goal,
        List<TreeItem> leafTotal,
        List<TreeItem> directInputs,
        List<List<TreeItem>> rows,
        List<EmiIngredient> byproducts) {

    /** 节点解析状态（需求 16）。 */
    public enum ResolveState {
        /** 有产出配方（已展开，正常路径）。 */
        RESOLVED,
        /** 无配方但可计数的物品叶（库存已足不拆解 / 直接获取）。 */
        LEAF_KNOWN,
        /** 虚拟栈等不可转 ItemStack 的叶（只显示，不计拥有量、不参与标红与自动合成）。 */
        LEAF_UNKNOWABLE
    }

    public boolean isEmpty() {
        return goal == null || goal.content() == null;
    }

    /**
     * 树节点：内容 + 玩家当前是否可**获得**（自身够或链条可合成，否则标红）+ 标签解析到的具体物品（无则 null）
     * + 产出该材料的配方（{@code null} = 无配方/叶节点/直接获取）+ 解析状态（需求 16）。
     * 供 tooltip 明细展示。
     */
    public record TreeItem(EmiIngredient content, boolean canCraft, EmiIngredient resolvedTo, EmiRecipe producer,
            ResolveState state) {
        public boolean hasResolved() {
            return resolvedTo != null && !resolvedTo.isEmpty();
        }

        /** 未知（不可计数）叶子：需求 16 语义——仅显示，不参与拥有量/标红/自动合成。 */
        public boolean isUnknown() {
            return state == ResolveState.LEAF_UNKNOWABLE;
        }
    }
}