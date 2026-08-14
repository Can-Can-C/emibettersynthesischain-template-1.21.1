package com.cancan.emibettersynthesischain.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * {@code AbstractContainerScreen} 私有/受保护字段 accessor（用于按鼠标坐标定位槽位）。
 * 通过"目标实例 cast 到本接口"调用。
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int ebs$leftPos();

    @Accessor("topPos")
    int ebs$topPos();
}
