package zone.moddev.mc.industrialoverkill.gym;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

import zone.moddev.mc.lib.api.capabilities.Transfer;
import zone.moddev.mc.lib.machine.neoforge.MultiblockMembers;

/** Keeps undecodable/displaced food verbatim until it can be restored to a gym. */
public final class RecoveredFoodItem extends Item {
    public RecoveredFoodItem() {
        super(new Properties().stacksTo(1));
    }
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide)
            return InteractionResult.SUCCESS;
        var instance = MultiblockMembers.resolve(context.getLevel(), context.getClickedPos());
        if (instance == null || !(instance.owner() instanceof AnimalGymEntity gym)
            || !instance.ready())
            return InteractionResult.PASS;
        var stack = context.getItemInHand();
        var payload = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var resources = payload.getList("resources", Tag.TAG_COMPOUND);
        boolean changed = false;
        for (int i = resources.size() - 1; i >= 0; i--) {
            var entry = resources.getCompound(i);
            if (!entry.getString("type").equals("mmdlib:items"))
                continue;
            var data = entry.getCompound("data");
            var food = ItemStack.parseOptional(context.getLevel().registryAccess(),
                data.getCompound("item"));
            if (food.isEmpty())
                continue;
            var remainder = Transfer.insert(gym.food, food, false);
            if (remainder.getCount() == food.getCount())
                continue;
            changed = true;
            if (remainder.isEmpty())
                resources.remove(i);
            else
                data.put("item", remainder.save(context.getLevel().registryAccess()));
        }
        if (changed) {
            if (resources.isEmpty())
                payload.remove("resources");
            if (payload.isEmpty())
                stack.shrink(1);
            else
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(payload));
        }
        return changed ? InteractionResult.CONSUME : InteractionResult.PASS;
    }
}
