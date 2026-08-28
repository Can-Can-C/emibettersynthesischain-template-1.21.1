package com.cancan.emibettersynthesischain;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

/**
 * 模块化常量宿主（common 模块，EMI 式 common+platform 结构的一部分）。
 *
 * <p>{@code @Mod} 入口类已移至 platform 模块（neoforge/forge）：
 * <ul>
 *   <li>{@code neoforge} → {@code EMIBettersynthesischainMod}（BOTH 端：注册配置）、
 *       {@code EMIBettersynthesischainClient}（CLIENT 端：快捷键/事件总线）；</li>
 *   <li>common 业务代码只引用本类的 {@link #MODID} 与 {@link #LOGGER}，不依赖加载器。</li>
 * </ul>
 */
public final class EMIBettersynthesischain {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "emibettersynthesischain";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    private EMIBettersynthesischain() {
    }
}