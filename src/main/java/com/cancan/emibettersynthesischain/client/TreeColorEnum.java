package com.cancan.emibettersynthesischain.client;

import dev.emi.emi.config.ConfigEnum;
import net.minecraft.network.chat.Component;

/**
 * 合成树线条配色预设的 EMI 设置页适配（仅设置页用）。
 *
 * <p>实现 {@link ConfigEnum}（EMI 内部接口）供 {@code ConfigScreen} 的 {@code EnumWidget}
 * 下拉选择；数据（id / 色值）在 {@link TreeColorPreset}（纯业务枚举），本枚举只做
 * id ↔ 枚举常量的映射与显示名，避免树渲染等业务代码触碰 EMI 内部类。显示名走
 * {@code ebs.config.tree.color.<id>} 翻译键。</p>
 *
 * <p><b>包位置说明</b>：不能放 {@code mixin} 包——Mixin 将其声明为受管控包，包内非 mixin
 * 类被包外引用会抛 {@code IllegalClassLoadError}（EnumWidget 的 Mutator 匿名内部类生成在
 * {@code dev.emi.emi.screen} 包下，属包外引用）。故与 {@link ConfigResetButton} 同为
 * 设置页 UI 适配类，放 {@code client} 包。</p>
 */
public enum TreeColorEnum implements ConfigEnum {
    TEAL(TreeColorPreset.TEAL),
    BLUE(TreeColorPreset.BLUE),
    PURPLE(TreeColorPreset.PURPLE),
    WHITE(TreeColorPreset.WHITE),
    GOLD(TreeColorPreset.GOLD),
    RED(TreeColorPreset.RED),
    GREEN(TreeColorPreset.GREEN),
    GRAY(TreeColorPreset.GRAY);

    private final TreeColorPreset preset;

    TreeColorEnum(TreeColorPreset preset) {
        this.preset = preset;
    }

    /** 按配置 id 取设置页枚举（未知回退 TEAL）。 */
    public static TreeColorEnum byId(String id) {
        for (TreeColorEnum e : values()) {
            if (e.preset.getId().equals(id)) {
                return e;
            }
        }
        return TEAL;
    }

    /** 存盘用的预设 id（与 {@link TreeColorPreset#getId()} 一致）。 */
    public String getId() {
        return preset.getId();
    }

    @Override
    public String getName() {
        return preset.getId();
    }

    @Override
    public Component getText() {
        return Component.translatable("ebs.config.tree.color." + preset.getId());
    }
}