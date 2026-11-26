package net.modcrafters.minecraft.industrialoverkill.agri;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/** Uses the ordinary hoe path, including tool modification and placement cancellation. */
final class AgriTilling {
    static final GameProfile PROFILE = new GameProfile(UUID.nameUUIDFromBytes(
        "industrial_overkill:agri_brain".getBytes(StandardCharsets.UTF_8)), "[Agri-Brain]");

    private AgriTilling() {}

    static boolean till(ProcessorMachine machine, BlockPos soil, boolean applyWear) {
        if (!(machine.level() instanceof ServerLevel level) || machine.removed()
            || !level.hasChunkAt(soil) || level.isOutsideBuildHeight(soil)
            || !level.getWorldBorder().isWithinBounds(soil)
            || level.captureBlockSnapshots || level.restoringBlockSnapshots) return false;
        var block = level.getBlockState(soil);
        if (!(block.is(Blocks.DIRT) || block.is(Blocks.GRASS_BLOCK))
            || !level.isEmptyBlock(soil.above())) return false;

        var inventory = ((DefinedMachineEntity) machine).items.inventory("hoe");
        var installed = inventory.getStackInSlot(0);
        if (installed.isEmpty() || !installed.is(net.minecraft.tags.ItemTags.HOES)
            || !installed.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.HOE_TILL)) return false;

        var player = FakePlayerFactory.get(level, PROFILE);
        if (!player.mayBuild() || !level.mayInteract(player, soil)) return false;
        var oldPosition = player.position();
        var oldHand = player.getMainHandItem();
        var hoe = installed.copy();
        var unbreakable = hoe.get(DataComponents.UNBREAKABLE);
        // Protect even a nearly broken hoe on the free half of each pair.
        // Paid uses follow the item's ordinary durability and enchantment logic.
        if (!applyWear) hoe.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
        try {
            player.setPos(Vec3.atCenterOf(machine.position()));
            player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
            var hit = new BlockHitResult(Vec3.atCenterOf(soil).add(0, .5, 0), Direction.UP, soil, false);
            // ItemStack.useOn includes NeoForge's snapshot rollback when placement is denied.
            hoe.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            if (!level.getBlockState(soil).is(Blocks.FARMLAND)) return false;
            if (!hoe.isEmpty()) {
                if (unbreakable == null) hoe.remove(DataComponents.UNBREAKABLE);
                else hoe.set(DataComponents.UNBREAKABLE, unbreakable);
            }
            inventory.setStackInSlot(0, hoe.isEmpty() ? ItemStack.EMPTY : hoe);
            return true;
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
            player.setPos(oldPosition);
        }
    }
}
