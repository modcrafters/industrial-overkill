package net.modcrafters.minecraft.industrialoverkill.agri;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.namespace.QName;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.modcrafters.minecraft.industrialoverkill.waste.FarmSupplies;
import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.drawer.DrawerDefinition;
import zone.moddev.mc.lib.gui.drawer.DrawerPlacement;
import zone.moddev.mc.lib.gui.drawer.DrawerRegistry;
import zone.moddev.mc.lib.gui.layout.ElementLayouts;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.ScreenTag;
import zone.moddev.mc.lib.gui.load.ScreenTags;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Edge;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.containers.SelectiveContainer;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.component.DrawerSession;
import zone.moddev.mc.lib.gui.runtime.component.MenuComponentKey;
import zone.moddev.mc.lib.gui.runtime.component.MenuComponentRegistry;
import zone.moddev.mc.lib.gui.sync.ElementStateView;
import zone.moddev.mc.lib.gui.sync.ElementSync;
import zone.moddev.mc.lib.gui.sync.SynchronizedElement;

/** A per-menu left drawer. Its two children use the normal cycle-button rendering and server actions. */
public record AgriSettings(Common common, List<Element> children) implements SynchronizedElement, SelectiveContainer {
    public static final ResourceLocation ID = FarmSupplies.id("agri_settings");
    public static final ElementSync<Boolean> OPEN = ElementSync.field(ID, ByteBufCodecs.BOOL.<RegistryFriendlyByteBuf>cast(),
        1, element -> false).affectsLayout().build();
    private static final MenuComponentKey<Session> SESSION = new MenuComponentKey<>(ID, Session.class);
    public static final ElementAction<Boolean> SET_OPEN = new ElementAction<>(ID, ByteBufCodecs.BOOL.<RegistryFriendlyByteBuf>cast(),
        1, (context, open) -> {
            var session = context.menu().components().require(SESSION);
            if (context.element() != session.element()) return false;
            if (open) context.menu().components().activate(session);
            context.menu().elementStates().set(session.element(), OPEN, open);
            return true;
        });

    public AgriSettings { children = List.copyOf(children); }

    static void register() {
        ScreenTags.GLOBAL.register(new QName("https://moddev.zone/industrial-overkill/screen", "settings-drawer"),
            ScreenTag.element(AgriSettings.class, xml -> new AgriSettings(xml.common(), xml.children()))
                .groups("dock").children("buttons", 2, 2).build());
        DrawerRegistry.GLOBAL.register(ID, context -> context.screen().tabs().stream()
            .filter(AgriSettings.class::isInstance)
            .map(tab -> new DrawerDefinition(ID, tab.common().id().orElseThrow(), DrawerPlacement.top(Edge.LEFT, 50),
                Optional.of(tab), 0, true)).toList());
        MenuComponentRegistry.GLOBAL.register(SESSION, 20, context -> context.menu().bound.screen().tabs().stream()
            .filter(AgriSettings.class::isInstance).map(AgriSettings.class::cast)
            .findFirst().map(tab -> new Session(context.menu(), tab)).orElse(null));
    }

    private record Session(MachineMenu menu, AgriSettings element) implements DrawerSession {
        @Override public ResourceLocation drawerType() { return ID; }
        @Override public void closeServer() { menu.elementStates().set(element, OPEN, false); }
    }

    @Override public List<ElementSync<?>> synchronizedValues() { return List.of(OPEN); }
    @Override public List<ElementAction<?>> actions() { return List.of(SET_OPEN); }
    @Override public boolean childVisible(Element child, ElementStateView state) { return state.get(this, OPEN); }
    @Override public int[] measure(TextMeasure text) { return new int[]{DrawerPlacement.DEFAULT_WIDTH, DrawerPlacement.DEFAULT_HEIGHT}; }

    public Rect body(Rect tab, TextMeasure text) {
        var size = ElementLayouts.stack(children, 2, text);
        int width = size[0] + 8;
        return new Rect(tab.x() - width, tab.y(), width, size[1] + 8);
    }

    @Override public Map<Element, Rect> childLayout(Rect tab, TextMeasure text) {
        var body = body(tab, text);
        return ElementLayouts.stackPositions(children, new Rect(body.x() + 4, body.y() + 4,
            body.width() - 8, body.height() - 8), 2, text);
    }
}
