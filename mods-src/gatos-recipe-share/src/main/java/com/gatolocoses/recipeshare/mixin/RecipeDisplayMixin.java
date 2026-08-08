package com.gatolocoses.recipeshare.mixin;

import com.gatolocoses.recipeshare.ShareRecipeWidget;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.screen.RecipeDisplay;
import dev.emi.emi.screen.WidgetGroup;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = RecipeDisplay.class, remap = false)
public abstract class RecipeDisplayMixin {
    @Shadow
    @Final
    public EmiRecipe recipe;

    @Shadow
    private int leftWidth;

    @Inject(method = "<init>(Ldev/emi/emi/api/recipe/EmiRecipe;)V", at = @At("TAIL"))
    private void gatosRecipeShare$reserveButtonSpace(EmiRecipe recipe, CallbackInfo callback) {
        if (recipe.getId() != null) {
            leftWidth += 14;
        }
    }

    @Inject(method = "getWidgets", at = @At("RETURN"))
    private void gatosRecipeShare$addButton(
            int x,
            int y,
            int availableWidth,
            int availableHeight,
            CallbackInfoReturnable<WidgetGroup> callback) {
        if (recipe.getId() == null) {
            return;
        }

        WidgetGroup widgets = callback.getReturnValue();
        int buttonX = -leftWidth - 3;
        int buttonY = Math.max(0, widgets.getHeight() - 12);
        widgets.add(new ShareRecipeWidget(buttonX, buttonY, recipe));
        widgets.addTooltipText(List.of(Component.literal("Share recipe in chat")), buttonX, buttonY, 12, 12);
    }
}
