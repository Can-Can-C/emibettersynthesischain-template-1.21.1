package com.cancan.emibettersynthesischain;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Platform（forge）入口：BOTH 端 @Mod——注册本 mod 的 ModConfigSpec（common 的 {@link Config}），
 * 并在客户端 setup 时初始化 {@link EMIBettersynthesischainClient}（事件总线/快捷键）。
 * 模块化（EMI 式 common+platform）：{@code common} 模块持有全部业务/mixin/配置值，
 * 本类与 {@code EMIBettersynthesischainClient} 是 forge 平台模块仅有的加载器胶水。
 */
@Mod(EMIBettersynthesischain.MODID)
public class EMIBettersynthesischainMod {

    public EMIBettersynthesischainMod() {
        // Forge 配置注册（COMMON → config/emibettersynthesischain-common.toml）
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        EMIBettersynthesischainClient.init();
        EMIBettersynthesischain.LOGGER.info("EMI Better Synthesis Chain (Forge 1.20.1) client ready");
    }
}