package net.modcrafters.minecraft.industrialoverkill.agri;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/** Plant through the normal item-use path so placement rules and cancellation apply. */
final class AgriPlanting {
    static final TagKey<Item> SEEDS = TagKey.create(Registries.ITEM,
        ResourceLocation.parse("industrial_overkill:agri_brain_seeds"));
    static final TagKey<Item> DIRECT = TagKey.create(Registries.ITEM,
        ResourceLocation.parse("industrial_overkill:agri_brain_direct_planting"));
    private static final TagKey<Block> NEEDS_CLEAR_SIDES = TagKey.create(Registries.BLOCK,
        ResourceLocation.parse("industrial_overkill:agri_brain_needs_clear_sides"));

    private AgriPlanting() {}

    static boolean accepts(ItemStack stack) { return !stack.isEmpty() && stack.is(SEEDS); }

    static boolean hasPlantingClearance(ServerLevel level, BlockPos crop, ItemStack seed) {
        boolean needsClearSides = Block.byItem(seed.getItem()).defaultBlockState().is(NEEDS_CLEAR_SIDES);
        for (var side : Direction.Plane.HORIZONTAL) {
            var adjacent = crop.relative(side);
            if (!level.hasChunkAt(adjacent)) return false;
            var neighbor = level.getBlockState(adjacent);
            if (neighbor.is(NEEDS_CLEAR_SIDES)) return false;
            if (needsClearSides && !neighbor.isAir() && !(neighbor.getBlock() instanceof LiquidBlock)) return false;
        }
        return true;
    }

    static boolean plant(DefinedMachineEntity machine, BlockPos soil, int slot) {
        if (slot < 0 || !(machine.getLevel() instanceof ServerLevel level) || machine.isRemoved()
            || !level.hasChunkAt(soil) || level.isOutsideBuildHeight(soil.above())
            || !level.getWorldBorder().isWithinBounds(soil.above())
            || level.captureBlockSnapshots || level.restoringBlockSnapshots
            || !level.isEmptyBlock(soil.above())) return false;
        var inventory = machine.items.inventory("seeds");
        var stored = inventory.getStackInSlot(slot);
        if (!accepts(stored) || !hasPlantingClearance(level, soil.above(), stored)) return false;
        if (stored.is(DIRECT)) {
            // Vanilla placement checks can read neighboring blocks (water for cane, clearance for cactus).
            if (level.getBlockState(soil).canBeReplaced()
                || !level.hasChunksAt(soil.offset(-1, 0, -1), soil.offset(1, 1, 1))) return false;
        } else if (!level.getBlockState(soil).is(Blocks.FARMLAND)) return false;
        var player = FakePlayerFactory.get(level, AgriTilling.PROFILE);
        if (!player.mayBuild() || !level.mayInteract(player, soil)
            || !level.mayInteract(player, soil.above())) return false;
        var oldPosition = player.position();
        var oldHand = player.getMainHandItem();
        try {
            player.setPos(Vec3.atCenterOf(machine.getBlockPos()));
            var seed = stored.copyWithCount(1);
            player.setItemInHand(InteractionHand.MAIN_HAND, seed);
            var hit = new BlockHitResult(Vec3.atCenterOf(soil).add(0, .5, 0), Direction.UP, soil, false);
            var result = seed.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            if (!result.consumesAction() || level.isEmptyBlock(soil.above())) return false;
            var remaining = stored.copy();
            remaining.shrink(1);
            inventory.setStackInSlot(slot, remaining);
            return true;
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
            player.setPos(oldPosition);
        }
    }
}
