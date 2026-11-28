package net.modcrafters.minecraft.industrialoverkill.agri;

import java.util.Optional;
import javax.xml.namespace.QName;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import zone.moddev.mc.lib.gui.load.ScreenAttribute;
import zone.moddev.mc.lib.gui.load.ScreenTag;
import zone.moddev.mc.lib.gui.load.ScreenTags;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.model.SlotSize;
import zone.moddev.mc.lib.gui.model.elements.ItemSlotElement;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;

/** Slot hints and server values used by the Agri-Brain screen. */
public final class AgriBrainScreen {
    private AgriBrainScreen() {}

    public static void register() {
        ScreenTags.GLOBAL.register(new QName("https://moddev.zone/industrial-overkill/screen", "hinted-slot"),
            ScreenTag.element(HintedSlot.class, xml -> new HintedSlot(xml.common(), xml.integer("index"),
                BuiltInRegistries.ITEM.getOptional(xml.id("item").orElseThrow())
                    .orElseThrow(() -> xml.error("Unknown hint item " + xml.attribute("item")))))
                .attribute("index", ScreenAttribute.required("xs:nonNegativeInteger"))
                .attribute("item", ScreenAttribute.required("xs:string")).group("slots").build());
    }

    public static final class HintedSlot extends ItemSlotElement {
        private final Item sample;

        private HintedSlot(Common common, int index, Item sample) {
            super(common, index, SlotKind.NORMAL, new Appearance(SlotSize.NORMAL, Optional.empty(), Optional.empty(),
                ResourceLocation.parse("mmdlib:slot"), .25f));
            this.sample = sample;
        }

        @Override
        public ItemStack hint(ScreenHost host, String inventory) {
            return sample.getDefaultInstance();
        }
    }

    /** Server value bound to the shared inventory selection highlight. */
    public static int selectedSeedSlot(BlockEntity machine) {
        var processor = AgriBrainProcessor.from(machine);
        return processor.plantingMode() == 1 ? processor.nextSeedSlot() : -1;
    }
}
