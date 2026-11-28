package net.modcrafters.minecraft.industrialoverkill.power;

import java.util.List;
import java.util.Optional;
import javax.xml.namespace.QName;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import zone.moddev.mc.lib.gui.load.ScreenTag;
import zone.moddev.mc.lib.gui.load.ScreenTags;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.ProgressDirection;
import zone.moddev.mc.lib.gui.model.elements.ProgressElement;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;

public final class GeneratorBurnArrow extends ProgressElement {
    private GeneratorBurnArrow(Common common) {
        super(common, Optional.empty(), ProgressDirection.RIGHT);
    }

    static void register() {
        ScreenTags.GLOBAL.register(new QName("https://moddev.zone/industrial-overkill/screen", "burn-arrow"),
            ScreenTag.element(GeneratorBurnArrow.class, xml -> new GeneratorBurnArrow(xml.common())).build());
    }

    @Override public List<Component> tooltip(ScreenHost host, ItemStack carried) {
        if (!(host instanceof GeneratorEntity generator)) return List.of();
        return List.of(Component.translatable("omnivore.remaining", generator.getProgress()),
            Component.translatable("omnivore.rate.tip", generator.getBurnRate()));
    }
}
