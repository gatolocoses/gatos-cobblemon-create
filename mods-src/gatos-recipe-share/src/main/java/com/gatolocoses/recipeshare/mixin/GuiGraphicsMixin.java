package com.gatolocoses.recipeshare.mixin;

import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.render.EmiTooltipComponents;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    private static final String VIEW_COMMAND = "/gatorecipe view ";

    @Inject(method = "renderComponentHoverEffect", at = @At("HEAD"), cancellable = true)
    private void gatosRecipeShare$renderRecipePreview(
            Font font,
            Style style,
            int mouseX,
            int mouseY,
            CallbackInfo callback) {
        if (style == null) {
            return;
        }

        ClickEvent click = style.getClickEvent();
        if (click == null
                || click.getAction() != ClickEvent.Action.RUN_COMMAND
                || !click.getValue().startsWith(VIEW_COMMAND)) {
            return;
        }

        ResourceLocation recipeId = ResourceLocation.tryParse(click.getValue().substring(VIEW_COMMAND.length()));
        if (recipeId == null || Minecraft.getInstance().screen == null) {
            return;
        }

        var recipe = EmiApi.getRecipeManager().getRecipe(recipeId);
        if (recipe == null) {
            return;
        }

        GuiGraphics draw = (GuiGraphics) (Object) this;
        EmiRenderHelper.drawTooltip(
                Minecraft.getInstance().screen,
                EmiDrawContext.wrap(draw),
                List.of(EmiTooltipComponents.getRecipeTooltipComponent(recipe)),
                mouseX,
                mouseY);
        callback.cancel();
    }
}
