package com.gatolocoses.recipeshare.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "journeymap.common.util.PermissionsManager", remap = false)
public class JourneyMapPermissionsMixin {
    @Inject(method = "canTeleport", at = @At("HEAD"), cancellable = true)
    private void gatosRecipeShare$denyTeleportPermission(ServerPlayer player, CallbackInfoReturnable<Boolean> callback) {
        MinecraftServer server = player.getServer();
        if (server != null && server.isDedicatedServer()) {
            callback.setReturnValue(false);
        }
    }
}
