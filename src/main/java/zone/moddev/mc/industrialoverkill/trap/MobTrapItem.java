package zone.moddev.mc.industrialoverkill.trap;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;

import zone.moddev.mc.industrialoverkill.gym.AnimalGymEntity;
import zone.moddev.mc.lib.machine.neoforge.MultiblockMembers;

public final class MobTrapItem extends BlockItem {
    public MobTrapItem() {
        super(MobTrap.BLOCK.get(), new Properties().stacksTo(1));
    }
    @Override
    public InteractionResult useOn(UseOnContext context) {
        // The server owns the complete manifest; clients only receive a compact view.
        // Predict the interaction without trying to resolve server-only membership.
        if (context.getLevel().isClientSide && MobTrapData.occupied(context.getItemInHand())
            && context.getLevel().getBlockState(context.getClickedPos())
                .getBlock() instanceof zone.moddev.mc.industrialoverkill.gym.GymBlock)
            return InteractionResult.SUCCESS;
        var structure = MultiblockMembers.resolve(context.getLevel(), context.getClickedPos());
        if (structure != null && structure.owner() instanceof AnimalGymEntity gym
            && MobTrapData.occupied(context.getItemInHand())) {
            if (context.getLevel().isClientSide)
                return InteractionResult.SUCCESS;
            if (!structure.ready() || !gym.food.getStackInSlot(3).isEmpty())
                return InteractionResult.FAIL;
            gym.training.reset();
            if (!gym.food.insertItem(3, context.getItemInHand().copyWithCount(1), false).isEmpty())
                return InteractionResult.FAIL;
            context.getItemInHand().shrink(1); // Occupants transfer even in creative; never
                                               // duplicate a mob.
            gym.synchronize();
            return InteractionResult.CONSUME;
        }
        return super.useOn(context);
    }
    @Override
    public InteractionResult place(BlockPlaceContext context) {
        var data = MobTrapData.read(context.getItemInHand());
        var result = super.place(context);
        if (result.consumesAction() && !context.getLevel().isClientSide
            && context.getLevel()
                .getBlockEntity(context.getClickedPos()) instanceof MobTrapEntity trap) {
            trap.setMob(data);
            if (!data.isEmpty() && context.getPlayer() != null && context.getPlayer().isCreative())
                context.getItemInHand().shrink(1);
        }
        return result;
    }
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
        TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        var data = MobTrapData.read(stack);
        tooltip.add(data.isEmpty()
            ? Component.translatable("trap.armed")
            : Component.translatable("trap.contains", MobTrapData.name(data)));
    }
}
