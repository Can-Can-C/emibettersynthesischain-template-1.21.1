package com.cancan.emibettersynthesischain;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * EMI Better Synthesis Chain — Forge 1.20.1 分支（多版本移植骨架）。
 *
 * <p>当前为 Phase 11.1 空骨架：仅注册 mod + 客户端启动钩子。移植阶段（11.2+）把主线的
 * 合成树 / 自动合成 / 配置 UI / 联动代码按模块搬回本分支并适配 Forge 1.20.1 API。</p>
 */
@Mod(EMIBettersynthesischain.MODID)
public class EMIBettersynthesischain {
    public static final String MODID = "emibettersynthesischain";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EMIBettersynthesischain() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("[EBS] Forge 1.20.1 skeleton loaded");
    }
}