package com.cancan.emibettersynthesischain.client;

import java.util.List;
import java.util.function.Supplier;

import com.cancan.emibettersynthesischain.Config;

import dev.emi.emi.EmiPort;
import dev.emi.emi.screen.widget.config.ConfigEntryWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

/**
 * EMI 设置页条目：一键把本 mod 全部配置重置为默认值并落盘。
 * 必须在非 mixin 包（Mixin 类加载限制）。
 */
public class ConfigResetButton extends ConfigEntryWidget {
    private final Button resetBtn;

    public ConfigResetButton(Supplier<String> search) {
        super(Component.translatable("ebs.config.reset"), List.of(), search, 20);
        this.resetBtn = EmiPort.newButton(0, 0, 150, 20,
                Component.translatable("ebs.config.reset"),
                b -> Config.resetAll());
        setChildren(List.of(resetBtn));
    }

    @Override
    public void update(int y, int x, int width, int height) {
        resetBtn.setPosition(x + width - resetBtn.getWidth(), y);
    }

    // EMI 1.20.1（Forge）的 ConfigEntryWidget 未实现 ContainerEventHandler.children()
    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(resetBtn);
    }
}
