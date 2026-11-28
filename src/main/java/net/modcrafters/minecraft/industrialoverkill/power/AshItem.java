package net.modcrafters.minecraft.industrialoverkill.power;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.gameevent.GameEvent;

public final class AshItem extends BoneMealItem {
    private static final int SUCCESS_PERCENT = 30;

    public AshItem(Properties properties) {
        super(properties);
    }

    public static boolean takesEffect(RandomSource random) {
        return random.nextInt(100) < SUCCESS_PERCENT;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("item.industrial_overkill.ash.fertilizer", SUCCESS_PERCENT));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        return fertilize(context.getItemInHand(), level, context.getClickedPos(), context.getClickedFace(), context.getPlayer())
            ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }

    static void registerDispenserBehavior() {
        DispenserBlock.registerBehavior(OmnivorousGenerator.ASH.get(), new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack stack) {
                var target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
                setSuccess(fertilize(stack, source.level(), target, null, null));
                return stack;
            }
        });
    }

    private static boolean fertilize(ItemStack stack, Level level, BlockPos pos, @Nullable Direction face, @Nullable Player player) {
        var state = level.getBlockState(pos);
        // A dispenser targets the water itself. Hand use targets a sturdy face next to it.
        var waterPos = face == null ? pos : pos.relative(face);
        boolean canReachWater = face == null || state.isFaceSturdy(level, pos, face);
        if (!level.isClientSide && !takesEffect(player == null ? level.random : player.getRandom())) {
            boolean plant = state.getBlock() instanceof BonemealableBlock block && block.isValidBonemealTarget(level, pos, state);
            boolean water = canReachWater && level.getBlockState(waterPos).is(Blocks.WATER) && level.getFluidState(waterPos).isSource();
            if (!plant && !water) return false;
            stack.shrink(1);
            return true;
        }
        if (!applyBonemeal(stack, level, pos, player)) {
            if (!canReachWater || !growWaterPlant(stack, level, waterPos, face)) return false;
            pos = waterPos;
        }
        if (!level.isClientSide) {
            if (player != null) player.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
            level.levelEvent(1505, pos, 15);
        }
        return true;
    }
}
