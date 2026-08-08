package com.gatolocoses.recipeshare;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SharedRecipePayload(String sender, String recipeId) implements CustomPacketPayload {
    public static final Type<SharedRecipePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(GatosRecipeShare.MOD_ID, "shared_recipe"));
    public static final StreamCodec<ByteBuf, SharedRecipePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), SharedRecipePayload::sender,
            ByteBufCodecs.stringUtf8(256), SharedRecipePayload::recipeId,
            SharedRecipePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
