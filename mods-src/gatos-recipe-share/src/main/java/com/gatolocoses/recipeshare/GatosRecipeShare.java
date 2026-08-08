package com.gatolocoses.recipeshare;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod(GatosRecipeShare.MOD_ID)
public final class GatosRecipeShare {
    public static final String MOD_ID = "gatos_recipe_share";
    private static final Map<UUID, Long> LAST_SHARE_TICK = new HashMap<>();

    public GatosRecipeShare(IEventBus modBus) {
        modBus.addListener(GatosRecipeShare::registerPayloads);
        NeoForge.EVENT_BUS.addListener(GatosRecipeShare::registerCommands);
        NeoForge.EVENT_BUS.addListener(GatosRecipeShare::playerLoggedOut);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(ShareRecipePayload.TYPE, ShareRecipePayload.STREAM_CODEC, GatosRecipeShare::shareRecipe);
        registrar.playToClient(OpenRecipePayload.TYPE, OpenRecipePayload.STREAM_CODEC, ClientRecipeHandler::openRecipe);
        registrar.playToClient(SharedRecipePayload.TYPE, SharedRecipePayload.STREAM_CODEC, ClientRecipeHandler::showSharedRecipe);
    }

    private static void shareRecipe(ShareRecipePayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        ResourceLocation recipeId = ResourceLocation.tryParse(payload.recipeId());
        if (recipeId == null) {
            return;
        }

        long now = player.serverLevel().getGameTime();
        Long previous = LAST_SHARE_TICK.get(player.getUUID());
        if (previous != null && now - previous < 20) {
            return;
        }
        LAST_SHARE_TICK.put(player.getUUID(), now);
        PacketDistributor.sendToAllPlayers(new SharedRecipePayload(
                player.getGameProfile().getName(), recipeId.toString()));
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gatorecipe")
                .then(Commands.literal("view")
                        .then(Commands.argument("recipe", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String value = StringArgumentType.getString(context, "recipe");
                                    ResourceLocation recipeId = ResourceLocation.tryParse(value);
                                    if (recipeId == null) {
                                        context.getSource().sendFailure(Component.literal("Invalid recipe link"));
                                        return 0;
                                    }
                                    PacketDistributor.sendToPlayer(player, new OpenRecipePayload(recipeId.toString()));
                                    return 1;
                                }))));
    }

    private static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SHARE_TICK.remove(event.getEntity().getUUID());
    }
}
