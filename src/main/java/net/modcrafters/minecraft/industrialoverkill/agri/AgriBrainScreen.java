package net.modcrafters.minecraft.industrialoverkill.agri;

import net.minecraft.world.level.block.entity.BlockEntity;

/** Server values used by the shared XML screen controls. */
public final class AgriBrainScreen {
    private AgriBrainScreen() {}

    /** Server value bound to the shared inventory selection highlight. */
    public static int selectedSeedSlot(BlockEntity machine) {
        var processor = AgriBrainProcessor.from(machine);
        return processor.plantingMode() == 1 ? processor.nextSeedSlot() : -1;
    }
}
