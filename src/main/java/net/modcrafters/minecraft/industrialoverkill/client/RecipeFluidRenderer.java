package net.modcrafters.minecraft.industrialoverkill.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import org.lwjgl.opengl.GL11;

final class RecipeFluidRenderer {
    private RecipeFluidRenderer() {}

    static void draw(GuiGraphics graphics, int x, int y, int width, int height, Fluid fluid, float opacity) {
        if (height <= 0) return;
        graphics.flush();
        boolean blending = GL11.glIsEnabled(GL11.GL_BLEND);
        float[] previous = RenderSystem.getShaderColor().clone();
        var extensions = IClientFluidTypeExtensions.of(fluid);
        var sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(extensions.getStillTexture());
        int tint = extensions.getTintColor();
        try {
            graphics.setColor(((tint >>> 16) & 255) / 255f, ((tint >>> 8) & 255) / 255f,
                (tint & 255) / 255f, opacity);
            RenderSystem.enableBlend();
            graphics.blit(x, y, 0, width, height, sprite);
            graphics.flush();
        } finally {
            graphics.setColor(previous[0], previous[1], previous[2], previous[3]);
            if (!blending) RenderSystem.disableBlend();
        }
    }
}
