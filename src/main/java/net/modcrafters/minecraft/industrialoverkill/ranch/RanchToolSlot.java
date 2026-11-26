package net.modcrafters.minecraft.industrialoverkill.ranch;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.xml.namespace.QName;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierProcessor;
import zone.moddev.mc.lib.gui.load.ScreenAttribute;
import zone.moddev.mc.lib.gui.load.ScreenTag;
import zone.moddev.mc.lib.gui.load.ScreenTags;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.model.SlotSize;
import zone.moddev.mc.lib.gui.model.elements.ItemSlotElement;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.sync.ElementStateView;
import zone.moddev.mc.lib.gui.sync.ElementSync;
import zone.moddev.mc.lib.gui.sync.SynchronizedElement;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

/** An ordinary tool slot with a synchronized indication of its add-on requirement. */
public final class RanchToolSlot extends ItemSlotElement implements SynchronizedElement {
    private static final ElementSync<Boolean> ENABLED = ElementSync.field(RanchAddons.id("ranch_tool_enabled"),
        ByteBufCodecs.BOOL.cast(), 1, element -> ((RanchToolSlot) element).tool == Tool.BUCKET)
        .sample(source -> ((RanchToolSlot) source.element()).enabledOn(source.machine())).build();

    private enum Tool {
        SHEARS(RanchAddons.Socket.SHEARING), BUCKET(null), COURIER(RanchAddons.Socket.PACKAGING);

        private final RanchAddons.Socket addon;
        Tool(RanchAddons.Socket addon) { this.addon = addon; }
    }

    private final Tool tool;

    private RanchToolSlot(Common common, Tool tool) {
        super(common, 0, SlotKind.NORMAL, Appearance.standard(SlotSize.NORMAL, Optional.empty(), Optional.empty()));
        this.tool = tool;
    }

    static void register() {
        ScreenTags.GLOBAL.register(new QName("https://moddev.zone/industrial-overkill/screen", "tool-slot"),
            ScreenTag.element(RanchToolSlot.class, xml -> new RanchToolSlot(xml.common(),
                Tool.valueOf(xml.attribute("tool").toUpperCase(Locale.ROOT))))
                .attribute("tool", ScreenAttribute.required("xs:string")).group("slots").build());
    }

    @Override
    public List<ElementSync<?>> synchronizedValues() { return List.of(ENABLED); }

    public boolean enabled(ElementStateView state) { return state.get(this, ENABLED); }

    private boolean enabledOn(Object host) {
        return tool.addon == null || host instanceof ProcessorMachine machine && RanchClientState.installed(machine, tool.addon);
    }

    @Override
    public List<Component> tooltip(ScreenHost host, ItemStack carried) {
        return enabledOn(host) ? List.of() : List.of(Component.translatable("ranch.slot_disabled").withStyle(ChatFormatting.RED));
    }

    public ItemStack sample() {
        return switch (tool) {
            case SHEARS -> Items.SHEARS.getDefaultInstance();
            case BUCKET -> Items.BUCKET.getDefaultInstance();
            case COURIER -> new ItemStack(MachineContent.item(CritterCourierProcessor.ID));
        };
    }
}
