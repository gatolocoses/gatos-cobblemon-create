package com.gatolocoses.recipeshare;

import dev.emi.emi.api.EmiApi;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = GatosRecipeShare.MOD_ID, value = Dist.CLIENT)
public final class ClientSmokeTest {
    private static int ticks;
    private static boolean opened;

    private ClientSmokeTest() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("gatos_recipe_share.smokeTest")
                || opened
                || Minecraft.getInstance().player == null
                || ++ticks < 40) {
            return;
        }

        var recipe = EmiApi.getRecipeManager().getRecipes().stream()
                .filter(candidate -> candidate.getId() != null)
                .findFirst()
                .orElse(null);
        if (recipe != null) {
            opened = true;
            GatosRecipeShare.LOGGER.info("Opening {} for recipe share smoke test", recipe.getId());
            EmiApi.displayRecipe(recipe);
            PacketDistributor.sendToServer(new ShareRecipePayload(recipe.getId().toString()));
        }
    }
}
