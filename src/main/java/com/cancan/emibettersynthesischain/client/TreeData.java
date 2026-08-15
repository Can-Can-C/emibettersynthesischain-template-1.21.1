package com.cancan.emibettersynthesischain.client;

import java.util.List;

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
 */
public record TreeData(
        TreeItem goal,
        List<TreeItem> leafTotal,
        List<TreeItem> directInputs,
        List<List<TreeItem>> rows,
        List<EmiIngredient> byproducts) {

    public boolean isEmpty() {
        return goal == null || goal.content() == null;
    }

    /** 树节点：内容 + 玩家当前是否可**获得**（自身够或链条可合成，否则标红）+ 标签解析到的具体物品（无则 null）。 */
    public record TreeItem(EmiIngredient content, boolean canCraft, EmiIngredient resolvedTo) {
        public boolean hasResolved() {
            return resolvedTo != null && !resolvedTo.isEmpty();
        }
    }
}
