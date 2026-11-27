package net.modcrafters.minecraft.industrialoverkill.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.agri.AgriSettings;
import zone.moddev.mc.lib.gui.client.Drawer;
import zone.moddev.mc.lib.gui.client.DrawerFactories;
import zone.moddev.mc.lib.gui.client.ElementRenderers;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.client.SideDrawerRenderer;
import zone.moddev.mc.lib.gui.drawer.DrawerDefinition;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.SideDrawerLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Edge;

@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AgriSettingsDrawer extends Drawer {
    private static final int COLOUR = 0xb5cc9c;
    private final AgriSettings element;

    private AgriSettingsDrawer(MachineScreen screen, DrawerDefinition definition) {
        super(screen, definition, AgriSettingsDrawer::drawSliders, COLOUR);
        element = (AgriSettings) definition.element().orElseThrow();
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            DrawerFactories.GLOBAL.register(AgriSettings.ID, AgriSettingsDrawer::new);
            ElementRenderers.GLOBAL.register(ElementRenderers.Pass.BACKGROUND, AgriSettings.class, ElementRenderers.Clip.VIEWPORT,
                (element, context) -> {
                    if (context.access().state().get(element, AgriSettings.OPEN))
                        SideDrawerRenderer.open(context.graphics(), context.bounds(), element.body(context.bounds(), TextMeasure.NONE),
                            Edge.LEFT, COLOUR);
                });
        });
    }

    private static void drawSliders(GuiGraphics graphics, int x, int y) {
        for (int row = 0; row < 3; row++) {
            int top = y + 2 + row * 5;
            int knob = x + (row == 1 ? 9 : 4);
            graphics.fill(x + 1, top + 1, x + 15, top + 3, 0xff40523b);
            graphics.fill(knob, top, knob + 3, top + 4, 0xff273a24);
            graphics.fill(knob, top, knob + 2, top + 2, 0xffeef3d9);
        }
    }

    @Override public boolean open() { return screen.getMenu().elementStates().get(element, AgriSettings.OPEN); }
    @Override public boolean pending() { return screen.getMenu().elementPending(element); }

    private boolean setOpen(boolean open) {
        return !pending() && screen.submitElementAction(element, AgriSettings.SET_OPEN, open);
    }

    @Override public void close() { if (open()) setOpen(false); }

    @Override public boolean restore(Selection selection) {
        if (pending()) return false;
        if (open()) {
            activated();
            return true;
        }
        setOpen(true);
        return false;
    }

    @Override public boolean click(double x, double y, int button) {
        if (button != 0 || !tabContains(x, y)) return false;
        boolean next = !open();
        if (setOpen(next)) {
            if (next) activated();
            remember(next, selection());
        }
        return true;
    }

    private Rect bounds() {
        var tab = tabBounds();
        return open() ? SideDrawerLayout.frame(tab, element.body(tab, TextMeasure.NONE)) : tab;
    }

    @Override public List<Rect> exclusions() { return List.of(bounds()); }

    @Override public boolean contains(double x, double y) {
        return bounds().contains(x, y);
    }

    @Override public void tooltip(GuiGraphics graphics, int x, int y) {
        if (tabContains(x, y)) graphics.renderTooltip(Minecraft.getInstance().font,
            Component.translatable("agri.settings"), x, y);
    }
}
