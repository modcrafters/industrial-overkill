package net.modcrafters.minecraft.industrialoverkill.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.modcrafters.minecraft.industrialoverkill.gym.GymAnimalRecipe;
import net.modcrafters.minecraft.industrialoverkill.power.GeneratorEntity;
import net.modcrafters.minecraft.industrialoverkill.power.GeneratorFuel;
import net.modcrafters.minecraft.industrialoverkill.power.OmnivorousGenerator;
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
    public static void potatoKeepsItsUnspentEnergyThroughPausesAndReload(GameTestHelper h) {
        var generator = place(h);
        var menu = new MachineMenu(17, h.makeMockServerPlayerInLevel().getInventory(), generator);
        require(menu.bound.screen().find("fuel").isPresent() && menu.bound.screen().find("containers").isPresent(),
            "The survival machine opens with fuel and container slots");
        var fuel = generator.screenItems();
        fuel.insertItem(0, new ItemStack(Items.POTATO, 2), false);
        generator.energy.setStored(32000);
        generator.serverTick();
        require(fuel.getStackInSlot(0).getCount() == 2, "A full generator leaves unstarted fuel alone");
        generator.energy.extract(3, false);
        generator.serverTick();
        require(generator.getProgress() == 797 && fuel.getStackInSlot(0).getCount() == 1,
            "Only the three FE accepted by the buffer are charged to the first potato");
        var registries = h.getLevel().registryAccess();
        generator.loadWithComponents(generator.saveWithoutMetadata(registries), registries);
        generator.energy.extract(32000, false);
        var switchPos = generator.getBlockPos().above();
        h.getLevel().setBlockAndUpdate(switchPos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        generator.serverTick();
        require(generator.getProgress() == 797 && generator.energy.stored() == 0, "Redstone holds the saved burn");
        h.getLevel().removeBlock(switchPos, false);
        for (int i = 0; i < 100; i++) generator.serverTick();
        require(generator.energy.stored() == 797 && generator.getProgress() == 0 && fuel.getStackInSlot(0).getCount() == 1,
            "The first serving pays exactly 800 FE across the reload");
        for (int i = 0; i < 100; i++) generator.serverTick();
        require(generator.energy.stored() == 1597 && fuel.getStackInSlot(0).isEmpty(), "The next potato burns for 100 ticks");
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
        slots.setStackInSlot(1, new ItemStack(Items.BOWL, 64));
        generator.serverTick();
        require(slots.getStackInSlot(0).is(Items.MUSHROOM_STEW) && generator.energy.stored() == 0,
            "A blocked bowl output preserves the stew");
        slots.extractItem(1, 64, false);
        generator.serverTick();
        generator.serverTick();
        require(slots.getStackInSlot(0).isEmpty() && slots.getStackInSlot(1).is(Items.BOWL)
            && slots.getStackInSlot(1).getCount() == 1 && generator.energy.stored() == 16,
            "One stew supplies multiple ticks and returns one bowl");
        h.succeed();
    }
}
