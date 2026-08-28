package com.cancan.emibettersynthesischain;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * 模块化常量宿主（common 模块，EMI 式 common+platform 结构的一部分）。
 *
 * <p>{@code @Mod} 入口类已移至 platform 模块（neoforge/forge）：
 * <ul>
 *   <li>{@code forge} → {@code EMIBettersynthesischainMod}（BOTH 端：注册配置、客户端 setup）、
 *       {@code EMIBettersynthesischainClient}（客户端事件/快捷键，由入口初始化）；</li>
 *   <li>common 业务代码只引用本类的 {@link #MODID} 与 {@link #LOGGER}，不依赖加载器。</li>
 * </ul>
 */
public final class EMIBettersynthesischain {
    public static final String MODID = "emibettersynthesischain";
    public static final Logger LOGGER = LogUtils.getLogger();

    private EMIBettersynthesischain() {
    }
}