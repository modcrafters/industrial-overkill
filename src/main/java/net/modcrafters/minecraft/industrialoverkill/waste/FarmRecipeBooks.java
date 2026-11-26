package net.modcrafters.minecraft.industrialoverkill.waste;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.gui.drawer.DrawerDefinition;
import zone.moddev.mc.lib.gui.drawer.DrawerPlacement;
import zone.moddev.mc.lib.gui.drawer.DrawerRegistry;
import zone.moddev.mc.lib.gui.model.Edge;

public final class FarmRecipeBooks {
    public static final ResourceLocation SEWAGE_FILTER = FarmSupplies.id("sewage_filter");
    public static final ResourceLocation FERTILIZER_MAKER = FarmSupplies.id("fertilizer_maker");
    public static final ResourceLocation DRAWER = FarmSupplies.id("farm_recipes");

    private FarmRecipeBooks() {}

    public static void register() {
        DrawerRegistry.GLOBAL.register(DRAWER, context -> {
            var machine = context.screen().id();
            return machine.equals(SEWAGE_FILTER) || machine.equals(FERTILIZER_MAKER)
                ? List.of(new DrawerDefinition(DRAWER, DRAWER.getPath(), DrawerPlacement.top(Edge.LEFT, 50)))
                : List.of();
        });
    }
}
