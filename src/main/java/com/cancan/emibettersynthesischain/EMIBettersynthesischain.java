package com.cancan.emibettersynthesischain;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * EMI Better Synthesis Chain — Forge 1.20.1 分支。
 */
@Mod(EMIBettersynthesischain.MODID)
public class EMIBettersynthesischain {
    public static final String MODID = "emibettersynthesischain";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EMIBettersynthesischain() {
        // Forge 配置注册（COMMON → config/emibettersynthesischain-common.toml）
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        EMIBettersynthesischainClient.init();
        LOGGER.info("EMI Better Synthesis Chain (Forge 1.20.1) client ready");
    }
}