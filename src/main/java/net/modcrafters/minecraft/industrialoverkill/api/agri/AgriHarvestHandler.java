package net.modcrafters.minecraft.industrialoverkill.api.agri;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;

/** Register this capability on the crop's base block in NeoForge's RegisterCapabilitiesEvent. */
@FunctionalInterface
public interface AgriHarvestHandler {
    BlockCapability<AgriHarvestHandler, Void> CAPABILITY = BlockCapability.createVoid(
        ResourceLocation.parse("industrial_overkill:agri_harvest"), AgriHarvestHandler.class);

    /**
     * Inspect a plot without mutations, random rolls or chunk loads. Return null to leave it alone.
     * The drone asks again at harvest time. The target must be the base or a block directly above it.
     */
    @Nullable Harvest inspect(ServerLevel level, BlockPos base, BlockState state);

    /** One block change and its yield. The machine checks protection and capacity before committing. */
    record Harvest(BlockPos target, BlockState replacement, Drops drops) {
        public Harvest {
            target = target.immutable();
            Objects.requireNonNull(replacement);
            Objects.requireNonNull(drops);
        }

        /** Remove one block using its ordinary empty-hand loot table. */
        public static Harvest cut(BlockPos target) {
            return new Harvest(target, Blocks.AIR.defaultBlockState(), (level, pos, state, player) ->
                Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, ItemStack.EMPTY));
        }

        public boolean clearsBase(BlockPos base) {
            return target.equals(base) && replacement.isAir();
        }
    }

    @FunctionalInterface
    interface Drops {
        /** Return the yield for this attempt. Never spawn items or change the world in this callback. */
        List<ItemStack> roll(ServerLevel level, BlockPos target, BlockState harvested, Player player);
    }
}
