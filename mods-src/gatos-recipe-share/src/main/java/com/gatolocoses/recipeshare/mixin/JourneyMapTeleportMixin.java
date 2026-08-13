package com.gatolocoses.recipeshare.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "journeymap.common.util.JourneyMapTeleport", remap = false)
public class JourneyMapTeleportMixin {
    @Inject(method = "attemptTeleport", at = @At("HEAD"), cancellable = true)
    private void gatosRecipeShare$blockTeleport(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (entity == null) {
            return;
        }

        MinecraftServer server = entity.getServer();
        if (server != null && server.isDedicatedServer()) {
            if (entity instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal("JourneyMap teleports are disabled on this server. / Teletransportes de JourneyMap deshabilitados."));
            }
            callback.setReturnValue(false);
        }
    }
}
