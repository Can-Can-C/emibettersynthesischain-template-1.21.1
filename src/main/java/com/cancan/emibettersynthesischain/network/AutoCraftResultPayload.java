package com.cancan.emibettersynthesischain.network;

import com.cancan.emibettersynthesischain.EMIBettersynthesischain;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 自动合成结果（S2C）：合成成功/失败通知客户端显示提示（成功白字、失败红字）。
 */
public record AutoCraftResultPayload(String message, boolean success) implements CustomPacketPayload {
    public static final Type<AutoCraftResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EMIBettersynthesischain.MODID, "auto_craft_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AutoCraftResultPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, AutoCraftResultPayload::message,
                    ByteBufCodecs.BOOL, AutoCraftResultPayload::success, AutoCraftResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
