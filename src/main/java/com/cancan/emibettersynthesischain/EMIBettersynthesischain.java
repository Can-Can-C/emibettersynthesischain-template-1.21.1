package com.cancan.emibettersynthesischain;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * EMI Better Synthesis Chain - NeoForge 26.1.2 branch skeleton (Phase 11.1).
 */
@Mod(EMIBettersynthesischain.MODID)
public class EMIBettersynthesischain {
    public static final String MODID = "emibettersynthesischain";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EMIBettersynthesischain() {
        LOGGER.info("[EBS] NeoForge 26.1.2 skeleton loaded");
    }
}