package com.gatolocoses.recipeshare;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

@EmiEntrypoint
public final class RecipeShareEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addRecipeDecorator((recipe, widgets) -> {
            if (recipe.getId() == null) {
                return;
            }

            int x = -17;
            int y = Math.max(0, widgets.getHeight() - 12);
            widgets.addButton(x, y, 12, 12, 0, 0, () -> true, (mouseX, mouseY, button) -> {
                if (button != 0) {
                    return;
                }
                PacketDistributor.sendToServer(new ShareRecipePayload(recipe.getId().toString()));
            });
            widgets.addDrawable(x, y, 12, 12, (draw, mouseX, mouseY, delta) -> {
                boolean hovered = mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + 12;
                draw.fill(0, 0, 12, 12, hovered ? 0xff6b6b6b : 0xff424242);
                draw.renderOutline(0, 0, 12, 12, 0xffbdbdbd);
                draw.drawString(Minecraft.getInstance().font, "↗", 3, 1, 0xffffffff, false);
            });
            widgets.addTooltipText(java.util.List.of(Component.literal("Share recipe in chat")), x, y, 12, 12);
        });
    }
}
