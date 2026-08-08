package com.gatolocoses.recipeshare;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ShareRecipeWidget extends Widget {
    private final int x;
    private final int y;
    private final EmiRecipe recipe;

    public ShareRecipeWidget(int x, int y, EmiRecipe recipe) {
        this.x = x;
        this.y = y;
        this.recipe = recipe;
    }

    @Override
    public Bounds getBounds() {
        return new Bounds(x, y, 12, 12);
    }

    @Override
    public void render(GuiGraphics draw, int mouseX, int mouseY, float delta) {
        boolean hovered = getBounds().contains(mouseX, mouseY);
        draw.fill(x, y, x + 12, y + 12, hovered ? 0xff167f8d : 0xff174f59);
        draw.renderOutline(x, y, 12, 12, hovered ? 0xffffffff : 0xff9fdbe3);
        draw.drawString(Minecraft.getInstance().font, "↗", x + 3, y + 1, 0xffffffff, false);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || recipe.getId() == null) {
            return false;
        }

        PacketDistributor.sendToServer(new ShareRecipePayload(recipe.getId().toString()));
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        return true;
    }
}
