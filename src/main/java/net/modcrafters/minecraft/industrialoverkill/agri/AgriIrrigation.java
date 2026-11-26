package net.modcrafters.minecraft.industrialoverkill.agri;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmBlock;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/** Waters dry farmland when the drone visits it. */
final class AgriIrrigation {
    private AgriIrrigation() {}

    static void waterVisitedSoil(DefinedMachineEntity machine, BlockPos soil) {
        if (!(machine.getLevel() instanceof ServerLevel level) || machine.isRemoved()
            || !level.hasChunkAt(soil) || level.isOutsideBuildHeight(soil) || !level.getWorldBorder().isWithinBounds(soil)
            || level.captureBlockSnapshots || level.restoringBlockSnapshots) return;
        var state = level.getBlockState(soil);
        if (!(state.getBlock() instanceof FarmBlock)) return;
        int missing = FarmBlock.MAX_MOISTURE - state.getValue(FarmBlock.MOISTURE);
        if (missing == 0) return;
        int cost = Math.round(100f * missing / FarmBlock.MAX_MOISTURE);

        var resources = machine.resources();
        try (var tx = resources.begin()) {
            if (resources.fluids("water").extract(0, cost, tx) != cost) return;
            if (!level.setBlock(soil, state.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), Block.UPDATE_CLIENTS)) return;
            tx.commit();
        }
        float fraction = cost / 100f;
        double spread = .045 + .15 * fraction;
        double x = soil.getX() + .5, y = soil.getY() + 1.0, z = soil.getZ() + .5;
        level.sendParticles(ParticleTypes.SPLASH, x, y, z, 1 + missing, spread, .01, spread, .015 + .035 * fraction);
        var sound = cost < 50 ? IndustrialOverkill.AGRI_WATER_SPRINKLE : IndustrialOverkill.AGRI_WATER_SPLASH;
        float pitch = 1.24f - .32f * fraction + (level.random.nextFloat() - .5f) * .03f;
        level.playSound(null, x, y, z, sound.get(), SoundSource.BLOCKS, .16f + .40f * fraction, pitch);
    }
}
