package com.gatolocoses.recipeshare;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShareRecipePayload(String recipeId) implements CustomPacketPayload {
    public static final Type<ShareRecipePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(GatosRecipeShare.MOD_ID, "share_recipe"));
    public static final StreamCodec<ByteBuf, ShareRecipePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), ShareRecipePayload::recipeId,
            ShareRecipePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
