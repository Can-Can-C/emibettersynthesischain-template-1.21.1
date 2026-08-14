package com.cancan.emibettersynthesischain.network;

import com.cancan.emibettersynthesischain.Config;
import com.cancan.emibettersynthesischain.EMIBettersynthesischain;
import com.cancan.emibettersynthesischain.client.MessageOverlay;
import com.cancan.emibettersynthesischain.server.AutoCraftHandler;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 自定义 payload 注册（MOD 总线）。
 */
@EventBusSubscriber(modid = EMIBettersynthesischain.MODID)
public class ModPayloads {
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(EMIBettersynthesischain.MODID);
        registrar.playToServer(AutoCraftPayload.TYPE, AutoCraftPayload.STREAM_CODEC, AutoCraftHandler::handle);
        registrar.playToClient(AutoCraftResultPayload.TYPE, AutoCraftResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    // 失败提示可在 EMI 设置页关闭；成功提示始终显示
                    if (payload.success() || Config.AUTO_CRAFT_FAILURE_MESSAGES.get()) {
                        MessageOverlay.show(Component.literal(payload.message()), payload.success());
                    }
                }));
    }
}
