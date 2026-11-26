package net.modcrafters.minecraft.industrialoverkill.ranch;

import java.util.List;

import javax.xml.namespace.QName;

import net.minecraft.network.codec.ByteBufCodecs;
import zone.moddev.mc.lib.gui.layout.LayoutContext;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.ScreenTag;
import zone.moddev.mc.lib.gui.load.ScreenTags;
import zone.moddev.mc.lib.gui.model.Align;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.containers.BoxElement;
import zone.moddev.mc.lib.gui.model.containers.SelectiveContainer;
import zone.moddev.mc.lib.gui.sync.ElementStateView;
import zone.moddev.mc.lib.gui.sync.ElementSync;
import zone.moddev.mc.lib.gui.sync.SynchronizedElement;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;

/** Shows the shared herd target while either add-on uses it. */
final class RanchSettings extends BoxElement implements SynchronizedElement, SelectiveContainer {
    private static final ElementSync<Boolean> INSTALLED = ElementSync.field(RanchAddons.id("ranch_settings_installed"),
        ByteBufCodecs.BOOL.cast(), 1, element -> false)
        .sample(source -> source.machine() instanceof ProcessorMachine host
            && RanchControllerProcessor.managesHerdSize(host))
        .affectsLayout().build();

    private RanchSettings(Common common, List<Element> children) {
        super(common, true, 0, 2, Align.CENTER, children);
    }

    static void register() {
        ScreenTags.GLOBAL.register(new QName("https://moddev.zone/industrial-overkill/screen", "ranch-settings"),
            ScreenTag.element(RanchSettings.class, xml -> new RanchSettings(xml.common(), xml.children()))
                .children("widget", 1, -1).build());
    }

    @Override
    public List<ElementSync<?>> synchronizedValues() { return List.of(INSTALLED); }

    @Override
    public boolean childVisible(Element child, ElementStateView state) { return state.get(this, INSTALLED); }

    @Override
    public int[] measure(TextMeasure text) {
        var size = super.measure(text);
        if (text instanceof LayoutContext layout && !layout.state().get(this, INSTALLED)) size[1] = 0;
        return size;
    }
}
