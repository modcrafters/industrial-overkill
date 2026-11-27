package net.modcrafters.minecraft.industrialoverkill.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.gym.GymAnimalRecipe;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient.FluidPart;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient.ItemPart;
import zone.moddev.mc.lib.machine.recipe.MachineProduct;
import zone.moddev.mc.lib.machine.recipe.MachineProduct.ItemProduct;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;

/** The total energy paid for by one item and the most it can deliver in one tick. */
public record GeneratorFuel(int energy, int rate) {
    private static final Codec<GeneratorFuel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.intRange(1, Integer.MAX_VALUE).fieldOf("energy").forGetter(GeneratorFuel::energy),
        Codec.intRange(1, Integer.MAX_VALUE).fieldOf("rate").forGetter(GeneratorFuel::rate)
    ).apply(instance, GeneratorFuel::new));
    public static final DataMapType<Item, GeneratorFuel> CALORIES = DataMapType.builder(
        ResourceLocation.fromNamespaceAndPath(IndustrialOverkill.MOD_ID, "calories"), Registries.ITEM, CODEC)
        .synced(CODEC, true).build();
    private static final TagKey<Item> DENIED = TagKey.create(Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath(IndustrialOverkill.MOD_ID, "generator_denied"));

    public static GeneratorFuel forStack(Level level, ItemStack stack) {
        return forStack(level, stack, null);
    }

    public static GeneratorFuel forStack(Level level, ItemStack stack, GeneratorCatalyst catalyst) {
        if (level == null || stack.isEmpty() || stack.is(DENIED) || stack.has(DataComponents.BLOCK_ENTITY_DATA)
            || stack.has(DataComponents.ENTITY_DATA) || stack.has(DataComponents.BUNDLE_CONTENTS)
            || stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock) return null;
        var container = stack.get(DataComponents.CONTAINER);
        if (container != null && container.nonEmptyItems().iterator().hasNext()) return null;

        var fuel = stack.getItemHolder().getData(CALORIES);
        if (fuel == null) {
            var food = stack.getFoodProperties(null);
            if (food != null && food.nutrition() > 0) {
                fuel = new GeneratorFuel((int) Math.min(Integer.MAX_VALUE, 400L * food.nutrition()), 8);
            } else {
                int ticks = stack.getBurnTime(RecipeType.SMELTING);
                if (ticks <= 0) return null;
                fuel = new GeneratorFuel((int) Math.min(Integer.MAX_VALUE, 20L * ticks), 40);
            }
        }
        if (FluidUtil.getFluidContained(stack).isPresent()) return null;
        long gymYield = Long.MAX_VALUE;
        for (var rule : GymAnimalRecipe.rules(level)) {
            if (rule.food().test(stack)) gymYield = Math.min(gymYield, (long) rule.energyPerTick() * rule.foodDurationTicks());
        }
        int energy = (int) Math.min(fuel.energy, gymYield / 2);
        if (energy == 0) return null;
        int rate = Math.min(energy, fuel.rate);
        if (catalyst != null) {
            energy = Math.clamp(Math.round(energy * catalyst.energyMultiplier()), 1, (int) Math.min(Integer.MAX_VALUE, gymYield - 1));
            rate = Math.clamp(Math.round(rate * catalyst.burnRateMultiplier()), 1, energy);
        }
        return new GeneratorFuel(energy, rate);
    }

    public MachineRecipe recipe(ItemStack stack, GeneratorCatalyst catalyst) {
        var serving = stack.copyWithCount(1);
        var inputs = new LinkedHashMap<String, List<MachineIngredient>>();
        inputs.put("fuel", List.of(new ItemPart(Ingredient.of(serving), 1)));
        var outputs = new LinkedHashMap<String, List<MachineProduct>>();
        var products = new ArrayList<MachineProduct>();
        var container = remainder(serving);
        if (!container.isEmpty()) products.add(new ItemProduct(container, 1));
        if (serving.is(ItemTags.LOGS_THAT_BURN)) products.add(new ItemProduct(new ItemStack(Items.CHARCOAL), 1));
        float ashChance = catalyst == null ? GeneratorCatalyst.BASE_ASH_CHANCE : catalyst.ashChance();
        if (ashChance > 0) products.add(new ItemProduct(new ItemStack(OmnivorousGenerator.ASH.get()), ashChance));
        if (catalyst != null) {
            inputs.put("catalyst", List.of(new FluidPart(HolderSet.direct(catalyst.input().fluid()), catalyst.input().amount())));
            if (catalyst.outputItemChance() > 0)
                catalyst.outputItem().ifPresent(item -> products.add(new ItemProduct(item, catalyst.outputItemChance())));
        }
        outputs.put("containers", products);
        if (catalyst != null && catalyst.outputFluidChance() > 0)
            catalyst.outputFluid().ifPresent(fluid -> outputs.put("byproduct", List.of(new MachineProduct.FluidProduct(fluid, catalyst.outputFluidChance()))));
        return new MachineRecipe(inputs, outputs, Math.ceilDiv(energy, rate));
    }

    private static ItemStack remainder(ItemStack serving) {
        var food = serving.getFoodProperties(null);
        if (food != null && food.usingConvertsTo().isPresent()) return food.usingConvertsTo().get().copy();
        return serving.getCraftingRemainingItem();
    }
}
