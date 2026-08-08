package com.gatolocoses.recipeshare;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenRecipePayload(String recipeId) implements CustomPacketPayload {
    public static final Type<OpenRecipePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(GatosRecipeShare.MOD_ID, "open_recipe"));
    public static final StreamCodec<ByteBuf, OpenRecipePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), OpenRecipePayload::recipeId,
            OpenRecipePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
