package com.cancan.emibettersynthesischain;

import org.slf4j.Logger;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Platform（neoforge）入口：BOTH 端 @Mod——注册本 mod 的 ModConfigSpec（common 的 {@link Config}）。
 * 客户端延伸（EMI 设置页扩展点、快捷键、事件总线）见同包的 {@code EMIBettersynthesischainClient}。
 * 模块化（EMI 式 common+platform）：{@code common} 模块持有全部业务/mixin/配置值，
 * 本类与 {@code EMIBettersynthesischainClient} 是 neoforge 平台模块仅有的加载器胶水。
 */
@Mod(EMIBettersynthesischain.MODID)
public class EMIBettersynthesischainMod {
    // Directly reference a slf4j logger
    private static final Logger LOGGER = EMIBettersynthesischain.LOGGER;

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public EMIBettersynthesischainMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("EMI Better Synthesis Chain loaded");
    }
}