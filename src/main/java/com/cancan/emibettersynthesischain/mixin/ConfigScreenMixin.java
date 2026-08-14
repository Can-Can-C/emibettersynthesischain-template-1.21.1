package com.cancan.emibettersynthesischain.mixin;

import java.util.List;
import java.util.function.Supplier;

import com.cancan.emibettersynthesischain.Config;
import com.cancan.emibettersynthesischain.client.ConfigResetButton;
import com.llamalad7.mixinextras.sugar.Local;

import dev.emi.emi.screen.ConfigScreen;
import dev.emi.emi.screen.widget.config.BooleanWidget;
import dev.emi.emi.screen.widget.config.ConfigEntryWidget;
import dev.emi.emi.screen.widget.config.ConfigJumpButton;
import dev.emi.emi.screen.widget.config.ConfigSearch;
import dev.emi.emi.screen.widget.config.GroupNameWidget;
import dev.emi.emi.screen.widget.config.IntWidget;
import dev.emi.emi.screen.widget.config.ListWidget;
import dev.emi.emi.screen.widget.config.SubGroupNameWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 EMI 设置页（ConfigScreen）里注入本 mod 的设置组（EMI 1.1.24）。
 *
 * <p>参考 emi-plus-plus-2 的 ConfigScreenMixin：`init` 里 `addWidget(ListWidget)` 时挂一组
 * `GroupNameWidget/SubGroupNameWidget/IntWidget/BooleanWidget`，值读写 {@link Config}
 * （ModConfigSpec），改动后落盘。左上角加跳转按钮。</p>
 */
@Mixin(value = ConfigScreen.class, remap = false)
public abstract class ConfigScreenMixin extends Screen {
    @Shadow
    private ConfigSearch search;

    @Shadow
    public abstract void jump(String jump);

    protected ConfigScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private static final String GROUP_ID = "ebs";

    @ModifyArg(method = "init", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/screen/ConfigScreen;addWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"))
    private GuiEventListener onAddWidget(GuiEventListener widget) {
        if (widget instanceof ListWidget list) {
            attachConfig(list);
        }
        return widget;
    }

    @Unique
    private void attachConfig(ListWidget list) {
        ConfigScreen self = (ConfigScreen) (Object) this;
        Supplier<String> searchFn = () -> search == null ? "" : search.getSearch();

        GroupNameWidget root = new GroupNameWidget(GROUP_ID,
                Component.translatable("ebs.config.title"));
        list.addEntry(root);

        // ---- 合成树 ----
        SubGroupNameWidget tree = new SubGroupNameWidget("ebs.tree",
                Component.translatable("ebs.config.tree"));
        tree.parent = root;
        list.addEntry(tree);
        addInt(list, self, searchFn, "ebs.config.tree.sidebarWidth", Config.TREE_SIDEBAR_WIDTH, root, tree);
        addInt(list, self, searchFn, "ebs.config.tree.rowSpacing", Config.TREE_ROW_SPACING, root, tree);
        addInt(list, self, searchFn, "ebs.config.tree.itemGap", Config.TREE_ITEM_GAP, root, tree);
        addInt(list, self, searchFn, "ebs.config.tree.treeGap", Config.TREE_TREE_GAP, root, tree);
        addInt(list, self, searchFn, "ebs.config.tree.byproductGap", Config.TREE_BYPRODUCT_GAP, root, tree);
        addBool(list, self, searchFn, "ebs.config.tree.redMarking", Config.TREE_RED_MARKING, root, tree);

        // ---- 自动合成 ----
        SubGroupNameWidget craft = new SubGroupNameWidget("ebs.autoCraft",
                Component.translatable("ebs.config.autoCraft"));
        craft.parent = root;
        list.addEntry(craft);
        addInt(list, self, searchFn, "ebs.config.autoCraft.showTicks", Config.AUTO_CRAFT_SHOW_TICKS, root, craft);
        addInt(list, self, searchFn, "ebs.config.autoCraft.gapTicks", Config.AUTO_CRAFT_GAP_TICKS, root, craft);
        addBool(list, self, searchFn, "ebs.config.autoCraft.failureMessages",
                Config.AUTO_CRAFT_FAILURE_MESSAGES, root, craft);

        // 一键重置本 mod 全部配置
        ConfigResetButton reset = new ConfigResetButton(searchFn);
        list.addEntry(reset);
        root.children.add(reset);
        reset.parentGroups.add(root);
    }

    @Unique
    private void addInt(ListWidget list, ConfigScreen self, Supplier<String> searchFn, String key,
            ModConfigSpec.IntValue value, GroupNameWidget root, SubGroupNameWidget sub) {
        IntWidget w = new IntWidget(Component.translatable(key), List.of(), searchFn,
                self.new Mutator<Integer>() {
                    @Override
                    protected Integer getValue() {
                        return value.get();
                    }

                    @Override
                    protected void setValue(Integer v) {
                        value.set(v);
                        Config.save();
                    }
                });
        addToGroups(list, w, root, sub);
    }

    @Unique
    private void addBool(ListWidget list, ConfigScreen self, Supplier<String> searchFn, String key,
            ModConfigSpec.BooleanValue value, GroupNameWidget root, SubGroupNameWidget sub) {
        BooleanWidget w = new BooleanWidget(Component.translatable(key), List.of(), searchFn,
                self.new Mutator<Boolean>() {
                    @Override
                    protected Boolean getValue() {
                        return value.get();
                    }

                    @Override
                    protected void setValue(Boolean v) {
                        value.set(v);
                        Config.save();
                    }
                });
        addToGroups(list, w, root, sub);
    }

    @Unique
    private static void addToGroups(ListWidget list, ConfigEntryWidget widget,
            GroupNameWidget root, SubGroupNameWidget sub) {
        list.addEntry(widget);
        root.children.add(widget);
        widget.parentGroups.add(root);
        sub.children.add(widget);
        widget.parentGroups.add(sub);
    }

    @Inject(method = "addJumpButtons", at = @At(value = "TAIL"))
    private void onAddJumpButtons(CallbackInfo ci, @Local(name = "y") int y, @Local(name = "v") int v) {
        addRenderableWidget(new ConfigJumpButton(2, y, 0, v, w -> jump(GROUP_ID),
                List.of(Component.translatable("ebs.config.title"))));
    }
}
