package net.modcrafters.minecraft.industrialoverkill.test;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.modcrafters.minecraft.industrialoverkill.gym.GymAnimalRecipe;
import net.modcrafters.minecraft.industrialoverkill.power.GeneratorEntity;
import net.modcrafters.minecraft.industrialoverkill.power.GeneratorFuel;
import net.modcrafters.minecraft.industrialoverkill.power.OmnivorousGenerator;
import net.modcrafters.minecraft.industrialoverkill.waste.FarmFluids;
import zone.moddev.mc.lib.gui.model.elements.FluidTankElement;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;

import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.require;

@GameTestHolder("industrial_overkill")
@PrefixGameTestTemplate(false)
public final class OmnivorousGeneratorTests {
    private static GeneratorEntity place(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlockAndUpdate(pos, OmnivorousGenerator.BLOCK.get().defaultBlockState());
        return (GeneratorEntity) h.getLevel().getBlockEntity(pos);
    }

    @GameTest(template = "gym_empty")
    public static void potatoStaysReservedAndBurnsThroughOverflowRedstoneAndReload(GameTestHelper h) {
        var generator = place(h);
        var menu = new MachineMenu(17, h.makeMockServerPlayerInLevel().getInventory(), generator);
        require(menu.bound.screen().find("fuel").isPresent() && menu.bound.screen().find("output4").isPresent(),
            "The survival machine opens with fuel and four output slots");
        var fuel = generator.screenItems();
        fuel.insertItem(0, new ItemStack(Items.POTATO, 2), false);
        generator.energy.setStored(32000);
        generator.serverTick();
        require(fuel.getStackInSlot(0).getCount() == 2, "A full generator leaves unstarted fuel alone");
        generator.energy.extract(3, false);
        generator.serverTick();
        require(generator.energy.stored() == 32000 && generator.getProgress() == 792 && fuel.getStackInSlot(0).getCount() == 2,
            "The first tick burns eight FE of a still-visible potato even when only three FE fit");
        require(fuel.extractItem(0, 64, true).getCount() == 1, "Only the surplus potato can be picked up");
        var arrow = menu.bound.screen().find("burn").orElseThrow();
        require(arrow.tooltip(generator, ItemStack.EMPTY).equals(List.of(Component.translatable("omnivore.remaining", 792),
            Component.translatable("omnivore.rate.tip", 8))), "The arrow shows the remaining FE and burn rate on separate rows");
        var registries = h.getLevel().registryAccess();
        generator.loadWithComponents(generator.saveWithoutMetadata(registries), registries);
        require(fuel.extractItem(0, 64, true).getCount() == 1, "Reload reinstates the reservation on the same potato");
        var switchPos = generator.getBlockPos().above();
        h.getLevel().setBlockAndUpdate(switchPos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        generator.serverTick();
        require(generator.getProgress() == 784 && generator.energy.stored() == 32000, "Full storage and redstone still spend the next tick");
        for (int i = 0; i < 98; i++) generator.serverTick();
        require(generator.getProgress() == 0 && fuel.getStackInSlot(0).getCount() == 1 && !fuel.hasPlan(0),
            "The 100th tick consumes exactly the reserved potato and frees the slot");
        generator.energy.setStored(0);
        generator.serverTick();
        require(generator.energy.stored() == 0 && !fuel.hasPlan(0), "Redstone prevents ignition of the queued potato");
        h.getLevel().removeBlock(switchPos, false);
        for (int i = 0; i < 100; i++) generator.serverTick();
        require(generator.energy.stored() == 800 && fuel.getStackInSlot(0).isEmpty(), "The next potato delivers 800 FE over 100 ticks");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void catalystAndByproductHaveSeparatePersistentPorts(GameTestHelper h) {
        var generator = place(h);
        var menu = new MachineMenu(17, h.makeMockServerPlayerInLevel().getInventory(), generator);
        require(menu.bound.screen().find("catalyst").orElseThrow() instanceof FluidTankElement input && input.tank() == 0
            && menu.bound.screen().find("byproduct").orElseThrow() instanceof FluidTankElement output && output.tank() == 1,
            "The screen exposes the catalyst and byproduct tanks");
        var port = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, generator.getBlockPos(), Direction.UP);
        var water = new FluidStack(Fluids.WATER, 1000);
        var lava = new FluidStack(Fluids.LAVA, 1000);
        var residue = new FluidStack(FarmFluids.SEWAGE.source.get(), 250);
        require(port != null && port.getTanks() == 2 && port.getTankCapacity(0) == 4000 && port.getTankCapacity(1) == 4000,
            "Both fluid ports have four buckets of capacity");
        require(port.fill(lava, FluidAction.SIMULATE) == 1000 && port.fill(residue, FluidAction.EXECUTE) == 0
            && port.fill(water, FluidAction.EXECUTE) == 1000 && port.fill(lava, FluidAction.EXECUTE) == 0,
            "Only one of water or lava goes into Catalyst, and external fills cannot spill into Byproduct");
        require(port.drain(1000, FluidAction.EXECUTE).isEmpty(), "Pipes cannot extract from the catalyst input");
        generator.screenFluids().fillResult(1, residue, FluidAction.EXECUTE);
        var registries = h.getLevel().registryAccess();
        generator.loadWithComponents(generator.saveWithoutMetadata(registries), registries);
        require(port.getFluidInTank(0).getAmount() == 1000 && port.drain(residue, FluidAction.EXECUTE).getAmount() == 250
            && port.getFluidInTank(1).isEmpty(), "Reload preserves both tanks and only Byproduct drains to a pipe");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void solidFuelRatesAndGymAdvantage(GameTestHelper h) {
        var level = h.getLevel();
        for (var coal : new Item[]{Items.COAL, Items.CHARCOAL}) {
            require(new GeneratorFuel(32000, 40).equals(GeneratorFuel.forStack(level, new ItemStack(coal))),
                "One coal or charcoal pays 40 FE/t for 800 ticks");
        }
        require(new GeneratorFuel(800, 8).equals(GeneratorFuel.forStack(level, new ItemStack(Items.POTATO))),
            "The synced potato override sets the starting balance");
        for (var rule : GymAnimalRecipe.rules(level)) {
            for (var stack : rule.food().getItems()) {
                var fuel = GeneratorFuel.forStack(level, stack);
                require(fuel == null || fuel.energy() < (long) rule.energyPerTick() * rule.foodDurationTicks(),
                    "Each shared food earns more FE in the Gym: " + stack);
            }
        }
        var generator = place(h);
        var port = level.getCapability(Capabilities.ItemHandler.BLOCK, generator.getBlockPos(), Direction.UP);
        require(port != null && !port.insertItem(0, new ItemStack(Items.LAVA_BUCKET), false).isEmpty()
            && !port.insertItem(0, new ItemStack(Items.STONE), false).isEmpty(), "Automation rejects liquid fuel and stone");
        port.insertItem(0, new ItemStack(Items.COAL), false);
        generator.serverTick();
        var output = level.getCapability(Capabilities.EnergyStorage.BLOCK, generator.getBlockPos(), Direction.WEST);
        require(output != null && output.receiveEnergy(8, false) == 0 && output.extractEnergy(40, false) == 40,
            "The generator exposes its first coal tick as extractable FE");
        var neighbour = generator.getBlockPos().east();
        var rockStew = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("industrial_overkill:rock_stew_factory"));
        level.setBlockAndUpdate(neighbour, rockStew.defaultBlockState());
        generator.serverTick();
        var consumer = level.getCapability(Capabilities.EnergyStorage.BLOCK, neighbour, Direction.WEST);
        require(consumer != null && consumer.getEnergyStored() == 40 && generator.energy.stored() == 0,
            "An adjacent Rock Stew Factory receives the next coal tick automatically");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void returnsFoodContainersOnce(GameTestHelper h) {
        var generator = place(h);
        var slots = generator.screenItems();
        slots.insertItem(0, new ItemStack(Items.MUSHROOM_STEW), false);
        for (int i = 1; i <= 4; i++) slots.setStackInSlot(i, new ItemStack(Items.BOWL, 64));
        generator.serverTick();
        require(slots.getStackInSlot(0).is(Items.MUSHROOM_STEW) && generator.energy.stored() == 0,
            "A blocked bowl output preserves the stew");
        slots.extractItem(4, 1, false);
        generator.serverTick();
        require(slots.getStackInSlot(4).getCount() == 63 && slots.extractItem(0, 1, false).isEmpty(),
            "One stew and room for its bowl are reserved until completion");
        int ticks = Math.ceilDiv(generator.getProgress(), generator.getBurnRate());
        for (int i = 0; i < ticks; i++) generator.serverTick();
        require(slots.getStackInSlot(0).isEmpty() && slots.getStackInSlot(4).getCount() == 64,
            "The stew returns exactly one bowl to the fourth slot");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void breakingGeneratorDropsCharcoalAndOnlyUnstartedWood(GameTestHelper h) {
        var generator = place(h);
        generator.screenItems().insertItem(0, new ItemStack(Items.OAK_LOG, 2), false);
        generator.serverTick();
        var pos = generator.getBlockPos();
        h.getLevel().removeBlock(pos, false);
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1));
        require(drops.stream().map(ItemEntity::getItem).filter(s -> s.is(Items.OAK_LOG)).mapToInt(ItemStack::getCount).sum() == 1
            && drops.stream().map(ItemEntity::getItem).filter(s -> s.is(Items.CHARCOAL)).mapToInt(ItemStack::getCount).sum() == 1,
            "Breaking returns the unstarted log and the lit log's charcoal exactly once");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void olderPrepaidBurnFinishesWithoutTakingQueuedFuel(GameTestHelper h) {
        var generator = place(h);
        generator.screenItems().insertItem(0, new ItemStack(Items.POTATO), false);
        var registries = h.getLevel().registryAccess();
        var old = generator.saveWithoutMetadata(registries);
        old.putInt("burn_total", 800);
        old.putInt("burn_remaining", 3);
        old.putInt("burn_rate", 8);
        generator.loadWithComponents(old, registries);
        generator.serverTick();
        require(generator.energy.stored() == 3 && generator.screenItems().getStackInSlot(0).is(Items.POTATO)
            && generator.getProgress() == 0, "A prepaid legacy burn pays its final partial tick without consuming the next item");
        h.succeed();
    }
}
