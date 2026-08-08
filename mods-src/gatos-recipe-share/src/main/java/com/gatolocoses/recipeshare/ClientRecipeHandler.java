package com.gatolocoses.recipeshare;

import dev.emi.emi.api.EmiApi;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

final class ClientRecipeHandler {
    private ClientRecipeHandler() {
    }

    static void openRecipe(OpenRecipePayload payload, IPayloadContext context) {
        ResourceLocation recipeId = ResourceLocation.tryParse(payload.recipeId());
        if (recipeId == null) {
            return;
        }

        var recipe = EmiApi.getRecipeManager().getRecipe(recipeId);
        if (recipe != null) {
            EmiApi.displayRecipe(recipe);
        } else if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("EMI could not find recipe " + recipeId), false);
        }
    }

    static void showSharedRecipe(SharedRecipePayload payload, IPayloadContext context) {
        ResourceLocation recipeId = ResourceLocation.tryParse(payload.recipeId());
        if (recipeId == null || Minecraft.getInstance().player == null) {
            return;
        }

        var recipe = EmiApi.getRecipeManager().getRecipe(recipeId);
        String label = recipe == null || recipe.getOutputs().isEmpty()
                ? recipeId.toString()
                : recipe.getOutputs().getFirst().getName().getString();
        Component link = Component.literal("[View Recipe]").withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/gatorecipe view " + recipeId))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Open this recipe in EMI"))));
        Component message = Component.literal(payload.sender() + " shared " + label + " ").append(link);
        Minecraft.getInstance().player.displayClientMessage(message, false);
    }
}
