package net.modcrafters.minecraft.industrialoverkill.client;

import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.stew.RockStewScreen;
import zone.moddev.mc.lib.gui.client.ElementRenderers;
import zone.moddev.mc.lib.gui.client.ElementBehaviors;
import zone.moddev.mc.lib.gui.client.DefaultElementBehavior;

@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RockStewClient {
    // Installed by the optional JEI plugin; this class never links JEI types.
    public static java.util.function.BooleanSupplier recipeViewer = () -> false;
    private RockStewClient() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RecipesUpdatedEvent updated) -> RockStewRecipeCatalog.invalidate());
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.TagsUpdatedEvent updated) -> RockStewRecipeCatalog.invalidate());
            zone.moddev.mc.lib.gui.client.DrawerFactories.GLOBAL.register(RockStewScreen.RECIPE_DRAWER,
                (screen, definition) -> new zone.moddev.mc.lib.gui.client.recipe.RecipeDrawer(
                    screen, definition, new RockStewRecipeCatalog(screen)));
            ElementBehaviors.GLOBAL.register(RockStewScreen.Display.class, (element, access) -> new DefaultElementBehavior(access) {
                @Override protected Click click(double x, double y, int button) {
                    if (element.kind.equals("operation")) {
                        if (!(access.value() instanceof ItemStack stack) || stack.isEmpty()) return Click.PASS;
                        if (button == 0 && recipeViewer.getAsBoolean()) return Click.CONSUME;
                        if (button == 0 && access.service(zone.moddev.mc.lib.gui.client.DrawerOpener.class)
                            .map(opener -> opener.open(RockStewScreen.RECIPE_DRAWER_ID)).orElse(false)) return Click.CONSUME;
                    }
                    return super.click(x, y, button);
                }
            });
            ElementRenderers.GLOBAL.register(RockStewScreen.Display.class, (element, context) -> {
                var r = context.bounds();
                var g = context.graphics();
                if (element.kind.equals("progress")) {
                    var control = (RockStewScreen.Control) context.access().find("stage_stock_" + element.index).orElseThrow();
                    g.fill(r.x(), r.y(), r.right(), r.bottom(), control.enabled(context.access().state()) ? 0xff555555 : 0xffb4b4b4);
                    g.fill(r.x(), r.y(), r.x() + (int) (r.width() * context.ratio()), r.bottom(), 0xff58bd98);
                } else if (context.value() instanceof ItemStack stack && !stack.isEmpty()) {
                    g.renderItem(stack, r.x() + 2, r.y() + 2);
                } else {
                    g.fill(r.x() + 7, r.y() + 9, r.x() + 13, r.y() + 11, 0xffaaaaaa);
                }
            });
            ElementRenderers.GLOBAL.registerOverlay(RockStewScreen.Line.class, (element, context) -> {
                // This pass precedes native item rendering, so leftover products remain visible and extractable.
                for (int i = 0; i < context.access().state().get(element, RockStewScreen.Line.COUNT); i++) {
                    var control = (RockStewScreen.Control) context.access().find("stage_stock_" + i).orElseThrow();
                    if (control.enabled(context.access().state())) continue;
                    var slot = context.access().find("stage_item_" + i).orElseThrow();
                    var r = context.access().bounds(slot);
                    context.graphics().fill(r.x(), r.y(), r.right(), r.bottom(), 0xffc6c6c6);
                    context.graphics().fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1, 0xffb9b9b9);
                    context.graphics().renderOutline(r.x(), r.y(), r.width(), r.height(), 0xffaaaaaa);
                }
            });
            ElementRenderers.GLOBAL.register(RockStewScreen.Control.class, (element, context) -> {
                if (element.enabled(context.access().state())) {
                    zone.moddev.mc.lib.gui.client.ButtonRenderer.draw(element, context);
                } else {
                    var r = context.bounds();
                    context.graphics().fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1, 0xffb9b9b9);
                    context.graphics().renderOutline(r.x() + 1, r.y() + 1, r.width() - 2, r.height() - 2, 0xffaaaaaa);
                }
            });
            ElementRenderers.GLOBAL.registerOverlay(RockStewScreen.Control.class, (element, context) -> {
                var r = context.bounds();
                var g = context.graphics();
                if (context.value() instanceof ItemStack stack && !stack.isEmpty()) g.renderItem(stack, r.x() + 2, r.y() + 2);
                else if (element.kind.equals("stock") && context.value() instanceof Number number) {
                    if (!element.enabled(context.access().state())) {
                        g.fill(r.x() + 6, r.y() + 9, r.x() + 14, r.y() + 11, 0xff9c9c9c);
                    } else if (number.intValue() == 0) {
                        g.renderOutline(r.x() + 4, r.y() + 4, 12, 12, 0xffd1d9df);
                        g.fill(r.x() + 5, r.y() + 5, r.x() + 15, r.y() + 15, 0xff555e66);
                        for (int i = 0; i < 12; i++)
                            g.fill(r.x() + 4 + i, r.y() + 14 - i, r.x() + 6 + i, r.y() + 16 - i, 0xffe08378);
                    } else {
                        int copies = number.intValue() == 1 ? 1 : 3;
                        for (int i = copies - 1; i >= 0; i--) {
                            int x = r.x() + (copies == 1 ? 5 : 3) + i * 2;
                            int y = r.y() + (copies == 1 ? 5 : 7) - i * 2;
                            g.fill(x, y, x + 10, y + 10, 0xff343e47);
                            g.fill(x + 1, y + 1, x + 9, y + 9, 0xffa3bbc9);
                            g.fill(x + 1, y + 1, x + 9, y + 3, 0xffe0eef5);
                            g.fill(x + 7, y + 3, x + 9, y + 9, 0xff6c8697);
                        }
                    }
                }
            });
        });
    }
}
