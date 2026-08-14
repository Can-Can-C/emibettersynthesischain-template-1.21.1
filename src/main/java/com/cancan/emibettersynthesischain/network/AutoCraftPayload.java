package com.cancan.emibettersynthesischain.network;

import java.util.HashMap;
import java.util.Map;

import com.cancan.emibettersynthesischain.EMIBettersynthesischain;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 自动合成请求（C2S）：要自动合成的工作台配方 id + "材料 → 默认配方" 映射
 * （客户端 EMI BoM 取到的默认配方，服务端优先使用）+ 是否连续合成（Shift+V）。
 */
public record AutoCraftPayload(ResourceLocation recipeId, Map<ResourceLocation, ResourceLocation> preferredProducers,
        boolean repeat) implements CustomPacketPayload {
    public static final Type<AutoCraftPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EMIBettersynthesischain.MODID, "auto_craft"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AutoCraftPayload> STREAM_CODEC =
            StreamCodec.composite(ResourceLocation.STREAM_CODEC, AutoCraftPayload::recipeId,
                    ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ResourceLocation.STREAM_CODEC, 32),
                    AutoCraftPayload::preferredProducers,
                    ByteBufCodecs.BOOL, AutoCraftPayload::repeat,
                    AutoCraftPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
