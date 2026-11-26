package net.modcrafters.minecraft.industrialoverkill.test;

import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.require;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.modcrafters.minecraft.industrialoverkill.waste.FarmFluids;
import net.modcrafters.minecraft.industrialoverkill.waste.FarmRecipeBooks;
import net.modcrafters.minecraft.industrialoverkill.waste.FarmSupplies;
import net.modcrafters.minecraft.industrialoverkill.waste.FertilizerMakerProcessor;
import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.drawer.DrawerRegistry;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.containers.InventoryElement;
import zone.moddev.mc.lib.gui.model.elements.EnergyGaugeElement;
import zone.moddev.mc.lib.gui.model.elements.FluidTankElement;
import zone.moddev.mc.lib.gui.model.elements.ProgressElement;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.ScreenClick;
import zone.moddev.mc.lib.gui.sync.ElementStates;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.neoforge.MachineVisualState;
import zone.moddev.mc.lib.machine.presentation.VisualCondition;

@GameTestHolder("industrial_overkill_agri")
@PrefixGameTestTemplate(false)
public final class FarmWasteTests {
    @GameTest(template = "gym_empty")
    public static void sewerCollectsTheExpectedAmount(GameTestHelper h) {
        var sewer = place(h, "honest_sewer", new BlockPos(2, 2, 2));
        var replica = (DefinedMachineEntity) sewer.getType().create(sewer.getBlockPos(), sewer.getBlockState());
        requireRanchingLights(sewer, replica, false);
        h.spawn(EntityType.COW, new BlockPos(3, 2, 2));
        ticks(sewer, 599);
        require(sewer.fluids.getFluidInTank(0).isEmpty(), "Collection waits a full thirty seconds");
        sewer.serverTick();
        var sewage = sewer.fluids.getFluidInTank(0);
        require(sewage.getFluid() == FarmFluids.SEWAGE.source.get() && sewage.getAmount() == 25,
            "One animal contributes exactly 25 mB without power");

        sewer.energy.setStored(200);
        requireRanchingLights(sewer, replica, true);
        sewer.serverTick();
        require(sewer.energy.stored() == 198, "The powered sewer draws 2 FE on its first tick");
        ticks(sewer, 98);
        require(sewer.energy.stored() == 2 && sewer.fluids.getFluidInTank(0).getAmount() == 25,
            "The powered sewer pays every tick and waits a full five seconds");
        sewer.serverTick();
        require(sewer.energy.stored() == 0 && sewer.fluids.getFluidInTank(0).getAmount() == 50,
            "A fully powered cycle adds 25 mB per animal after 100 ticks");
        requireRanchingLights(sewer, replica, true);
        sewer.serverTick();
        requireRanchingLights(sewer, replica, false);
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void sewerResumesPartialPowerAfterReload(GameTestHelper h) {
        var sewer = place(h, "honest_sewer", new BlockPos(2, 2, 2));
        h.spawn(EntityType.COW, new BlockPos(3, 2, 2));
        sewer.energy.setStored(81);
        ticks(sewer, 40);
        require(sewer.energy.stored() == 1 && sewer.fluids.getFluidInTank(0).isEmpty(),
            "Two FE fund each boosted tick and the final single FE is saved");

        var registries = h.getLevel().registryAccess();
        sewer.loadWithComponents(sewer.saveWithoutMetadata(registries), registries);
        ticks(sewer, 359);
        require(sewer.fluids.getFluidInTank(0).isEmpty(), "The mixed-power batch is still one tick short");
        sewer.serverTick();
        require(sewer.energy.stored() == 1 && sewer.fluids.getFluidInTank(0).getAmount() == 25,
            "40 powered ticks and 360 passive ticks complete one batch across a reload");

        int capacity = sewer.fluids.getTankCapacity(0);
        sewer.fluids.restoreFluid(0, new FluidStack(FarmFluids.SEWAGE.source.get(), capacity));
        sewer.energy.setStored(200);
        ticks(sewer, 600);
        require(sewer.energy.stored() == 200, "A full tank never draws power");
        sewer.fluids.restoreFluid(0, FluidStack.EMPTY);
        sewer.serverTick();
        require(sewer.energy.stored() == 200 && sewer.fluids.getFluidInTank(0).getAmount() == 25,
            "A blocked cycle delivers its accrued yield once the tank is drained");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void sewageFilterSelectsRecipesInTheSharedTanks(GameTestHelper h) {
        var filter = place(h, "sewage_filter", new BlockPos(4, 2, 2));
        var replica = (DefinedMachineEntity) filter.getType().create(filter.getBlockPos(), filter.getBlockState());
        requireRanchingLights(filter, replica, false);
        var screen = Screens.get(filter.definition.id()).orElseThrow().screen();
        var arrow = (ProgressElement) screen.find("generated_progress").orElseThrow();
        requireRecipeBook(filter, arrow);
        var registries = h.getLevel().registryAccess();
        var viewer = h.makeMockPlayer(GameType.SURVIVAL);
        var menu = new MachineMenu(1, viewer.getInventory(), filter);
        var inputElement = (FluidTankElement) screen.find("tank_0").orElseThrow();
        var outputElement = (FluidTankElement) screen.find("tank_1").orElseThrow();
        var layout = screen.elements().stream().filter(element -> element instanceof FluidTankElement
            || element instanceof ProgressElement
            || element instanceof InventoryElement inventory && "solids".equals(inventory.inventory())
            || element instanceof EnergyGaugeElement).toList();
        require(layout.size() == 5 && layout.get(0) == inputElement && layout.get(1) == arrow
                && layout.get(2) instanceof InventoryElement && layout.get(3) == outputElement
                && layout.get(4) instanceof EnergyGaugeElement, "Unexpected generated row: " + layout);
        var solidsGrid = (InventoryElement) layout.get(2);
        require(solidsGrid.columns() == 3 && solidsGrid.children().size() == 9,
            "The solids inventory is a 3x3 grid");
        for (int i = 1; i < layout.size(); i++)
            require(menu.layout().bounds(layout.get(i - 1)).right() <= menu.layout().bounds(layout.get(i)).x(),
                "The generated controls appear left to right without overlapping");
        var elements = List.<Element>of(arrow, inputElement, outputElement);
        var serverState = new ElementStates(elements, registries, () -> {});
        var clientState = new ElementStates(elements, registries, () -> {});
        clientState.apply(serverState.sample(filter, viewer));
        require(!arrow.outputBlocked(clientState) && !inputElement.drainOpen(clientState)
                && !outputElement.drainOpen(clientState),
            "An empty filter starts with both valves closed and no output warning");
        require(filter.fluids.getTanks() == 2 && filter.screenInventory("solids").getSlots() == 9,
            "The filter has one input tank, one output tank and nine solid output slots");
        var input = filter.fluids.tank(0);
        var output = filter.fluids.tank(1);
        require(filter.fluids.fill(new FluidStack(Fluids.WATER, 250), FluidAction.EXECUTE) == 0,
            "The input tank admits only fluids used by a filtering recipe");
        require(filter.fluids.fill(new FluidStack(FarmFluids.SEWAGE.source.get(), 250), FluidAction.EXECUTE) == 250,
            "The shared input accepts raw sewage");
        filter.serverTick(); // Reserve the recipe before advancing its work ticks.
        require(filter.fluids.plan(0).take() == 250 && input.drain(250, FluidAction.SIMULATE).isEmpty(),
            "The running strain recipe reserves its input against draining");
        require(filter.fluids.fill(new FluidStack(FarmFluids.SEWAGE.source.get(), 50), FluidAction.EXECUTE) == 50,
            "Unreserved sewage can still be added during processing");
        var clickInputDrain = new ScreenClick(viewer, menu, inputElement, 0, false, false, false,
            FluidTankElement.DRAIN_OPTION);
        require(inputElement.interact(clickInputDrain) && input.getFluidAmount() == 300,
            "A click opens the valve without discarding any fluid immediately");
        clientState.apply(serverState.sample(filter, viewer));
        require(inputElement.drainOpen(clientState) && !outputElement.drainOpen(clientState),
            "The valve state synchronizes independently per tank");
        filter.serverTick();
        require(input.getFluidAmount() == 295, "An open valve drains five mB in one tick");
        requireRanchingLights(filter, replica, true);
        ticks(filter, 10);
        require(input.getFluidAmount() == 250 && inputElement.interact(clickInputDrain),
            "The valve leaves the 250 mB reservation intact and can be closed");
        clientState.apply(serverState.sample(filter, viewer));
        require(!inputElement.drainOpen(clientState), "Closing the valve synchronizes its state");
        var liquid = filter.fluids.plan(1).add();
        require(liquid.amount() == 200 && liquid.fluid().value() == FarmFluids.FILTERED_SEWAGE.source.get()
                && output.fill(new FluidStack(Fluids.WATER, 200), FluidAction.SIMULATE) == 0,
            "The running strain recipe reserves 200 mB of filtered sewage output");
        var solids = filter.items.inventory("solids");
        for (int slot = 0; slot < solids.getSlots(); slot++)
            require(solids.plan(slot).isNone(), "Chance salvage reserves no item slots");
        ticks(filter, 188); // The valve checks already advanced eleven work ticks.
        require(input.getFluidAmount() == 250 && output.isEmpty() && filter.getProgress() == 199
                && filter.getTime() == 200,
            "The strain recipe displays progress before consuming inputs");
        filter.serverTick();
        require(filter.fluids.plan(0).isNone() && filter.fluids.plan(1).isNone(),
            "Finishing the strain recipe clears both tank reservations");
        var filtered = output.getFluid().copy();
        require(input.isEmpty() && filtered.getFluid() == FarmFluids.FILTERED_SEWAGE.source.get()
                && filtered.getAmount() == 200 && filter.energy.getEnergyStored() == 99000,
            "Strain converts 250 mB of sewage to 200 mB of filtered sewage for 1,000 FE");

        require(filter.fluids.fill(filtered, FluidAction.EXECUTE) == 200, "The shared input accepts filtered sewage");
        ticks(filter, 200);
        require(input.getFluidAmount() == 200 && output.getFluidAmount() == 200
                && filter.energy.getEnergyStored() == 99000 && count(filter, "solids", FarmSupplies.FILTER_CAKE.get()) == 0,
            "An incompatible output stalls reclaim without consuming filtered sewage or energy");
        requireRanchingLights(filter, replica, false);
        clientState.apply(serverState.sample(filter, viewer));
        require(arrow.outputBlocked(clientState), "The arrow receives the blocked output warning");

        var clickOutputDrain = new ScreenClick(viewer, menu, outputElement, 0, false, false, false,
            FluidTankElement.DRAIN_OPTION);
        require(outputElement.interact(clickOutputDrain), "Clicking opens the output valve");
        ticks(filter, 39);
        require(output.getFluidAmount() == 5, "The output valve drains at the same rate");
        filter.serverTick();
        clientState.apply(serverState.sample(filter, viewer));
        require(output.isEmpty() && !outputElement.drainOpen(clientState) && !arrow.outputBlocked(clientState),
            "The valve closes and the warning clears when the tank empties");
        outputElement.interact(clickOutputDrain);
        clientState.apply(serverState.sample(filter, viewer));
        require(!outputElement.drainOpen(clientState), "Clicking the empty tank's valve has no effect");
        filter.serverTick();
        ticks(filter, 200);
        require(input.isEmpty() && output.getFluid().getFluid() == Fluids.WATER && output.getFluidAmount() == 150
                && filter.energy.getEnergyStored() == 98000 && count(filter, "solids", FarmSupplies.FILTER_CAKE.get()) == 1,
            "Reclaim starts automatically once the output is clear and produces both outputs once");
        h.succeed();
    }

    private static void requireRanchingLights(DefinedMachineEntity machine, DefinedMachineEntity replica, boolean lit) {
        var registry = machine.getLevel().registryAccess();
        replica.loadWithComponents(machine.getUpdateTag(registry), registry);
        var snapshot = replica.getModelData().get(MachineVisualState.VARIABLES);
        var lights = machine.definition.modelParts().stream()
            .filter(part -> FarmSupplies.id("block/machine/ranching_powered_sides").equals(part.model()))
            .findFirst().orElseThrow(() -> new AssertionError("Missing ranching casing lights"));
        require(VisualCondition.compile(lights.when(), snapshot).test(snapshot) == lit,
            "Tracking clients see the casing lights " + (lit ? "lit" : "off"));
    }

    @GameTest(template = "gym_empty")
    public static void makerProcessesSharedInputAndCollectsWater(GameTestHelper h) {
        var machine = place(h, "fertilizer_maker", new BlockPos(2, 2, 2));
        var screen = Screens.get(machine.definition.id()).orElseThrow().screen();
        requireRecipeBook(machine, screen.find("maker_progress").orElseThrow());
        var previews = FertilizerMakerProcessor.recipePreviews();
        require(previews.size() == 7 && previews.stream().map(FertilizerMakerProcessor.RecipePreview::id).distinct().count() == 7,
            "The maker publishes three liquid recipes with both input alternatives and a pellet recipe");
        var rawCake = previews.stream().filter(preview -> preview.id().equals(FarmSupplies.id("fertilizer_maker/sewage_cake")))
            .findFirst().orElseThrow().recipe();
        require(rawCake.inputs().containsKey("cake") && !rawCake.inputs().containsKey("seeds")
            && rawCake.outputs().get("water").getFirst() instanceof zone.moddev.mc.lib.machine.recipe.MachineProduct.FluidProduct water
            && water.fluid().amount() == 50 && water.fluid().fluid().value() == Fluids.WATER,
            "The cake alternative shows the actual water byproduct without requiring seeds");
        var viewer = h.makeMockPlayer(GameType.SURVIVAL);
        var menu = new MachineMenu(1, viewer.getInventory(), machine);
        var row = List.of("maker_input", "maker_seeds", "maker_progress", "maker_output", "maker_water", "energy");
        for (int i = 1; i < row.size(); i++) {
            var left = screen.find(row.get(i - 1)).orElseThrow();
            var right = screen.find(row.get(i)).orElseThrow();
            require(menu.layout().bounds(left).right() <= menu.layout().bounds(right).x(),
                "Fertilizer Maker controls appear in order without overlapping");
        }
        var seeds = (InventoryElement) screen.find("maker_seeds").orElseThrow();
        require(machine.screenInventory("seeds").getSlots() == 4 && seeds.columns() == 2 && seeds.children().size() == 4,
            "The seed input is a 2 by 2 grid backed by four slots");
        var lowerLeft = menu.layout().bounds(seeds.children().get(2));
        var lowerRight = menu.layout().bounds(seeds.children().get(3));
        var cake = menu.layout().bounds(screen.find("maker_cake").orElseThrow());
        var mode = menu.layout().bounds(screen.find("maker_mode").orElseThrow());
        require(cake.x() == lowerLeft.x() && mode.x() == lowerRight.x()
                && cake.y() == mode.y() && cake.y() > lowerLeft.bottom() && mode.y() > lowerRight.bottom(),
            "The cake and mode controls sit just below their respective seed columns");
        require(machine.fluids.getTanks() == 2
            && screen.elements().stream().filter(FluidTankElement.class::isInstance).count() == 2,
            "The maker shows one liquid input and one water output");

        require(machine.fluids.fill(new FluidStack(Fluids.LAVA, 100), FluidAction.EXECUTE) == 0,
            "The shared input rejects unsupported fluids");
        require(machine.fluids.fill(new FluidStack(FarmFluids.SEWAGE.source.get(), 100), FluidAction.EXECUTE) == 100,
            "The shared input accepts raw sewage");
        require(machine.fluids.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE) == 0,
            "External filling cannot mix liquids or fill the water output");
        machine.screenInventory("seeds").setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 2));
        machine.screenInventory("seeds").setStackInSlot(3, new ItemStack(Items.MELON_SEEDS, 2));
        ticks(machine, 199);
        require(FertilizerMakerProcessor.progress(machine) == 199 && FertilizerMakerProcessor.progressMax(machine) == 200,
            "The arrow gets the elapsed time and duration of the current batch");
        machine.serverTick();
        require(count(machine, "output", FarmSupplies.FERTILIZER.get()) == 4 && machine.fluids.getFluidInTank(0).isEmpty()
            && machine.fluids.getFluidInTank(1).getFluid() == Fluids.WATER && machine.fluids.getFluidInTank(1).getAmount() == 50,
            "Raw sewage and mixed seeds yield four doses and 50 mB water");

        machine.screenInventory("cake").setStackInSlot(0, new ItemStack(FarmSupplies.FILTER_CAKE.get(), 3));
        require(machine.fluids.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE) == 100,
            "The shared input takes water once the sewage is used");
        ticks(machine, 799);
        require(count(machine, "output", FarmSupplies.FERTILIZER.get()) == 4, "The water recipe takes 800 work ticks");
        machine.serverTick();
        require(count(machine, "output", FarmSupplies.FERTILIZER.get()) == 6 && machine.fluids.getFluidInTank(0).isEmpty()
            && machine.fluids.getFluidInTank(1).getAmount() == 50,
            "The water recipe accepts cake and adds no byproduct");

        require(machine.fluids.fill(new FluidStack(FarmFluids.FILTERED_SEWAGE.source.get(), 100), FluidAction.EXECUTE) == 100,
            "The shared input takes filtered sewage");
        int capacity = machine.fluids.getTankCapacity(1);
        machine.fluids.restoreFluid(1, new FluidStack(Fluids.WATER, capacity));
        long energy = machine.energy.stored();
        machine.serverTick();
        require(machine.energy.stored() == energy && machine.fluids.getFluidInTank(0).getAmount() == 100
            && count(machine, "output", FarmSupplies.FILTERED_FERTILIZER.get()) == 0,
            "A full water output blocks the batch before it consumes FE or inputs");
        machine.fluids.tank(1).drain(capacity - 50, FluidAction.EXECUTE);
        ticks(machine, 200);
        require(count(machine, "output", FarmSupplies.FILTERED_FERTILIZER.get()) == 2
            && machine.fluids.getFluidInTank(0).isEmpty() && machine.fluids.getFluidInTank(1).getAmount() == 100,
            "Filtered sewage selects its own recipe and yields another 50 mB water");

        machine.screenInventory("seeds").setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 8));
        select(h, machine, "maker", "maker_pellets");
        ticks(machine, 400);
        require(count(machine, "output", FarmSupplies.SLOW_RELEASE_PELLET.get()) == 2
            && count(machine, "seeds", Items.WHEAT_SEEDS) == 0 && count(machine, "cake", FarmSupplies.FILTER_CAKE.get()) == 0
            && machine.fluids.getFluidInTank(1).getAmount() == 100,
            "Pellets consume one cake and eight seeds and add no byproduct");
        h.succeed();
    }

    private static DefinedMachineEntity place(GameTestHelper h, String id, BlockPos relative) {
        var pos = h.absolutePos(relative);
        h.getLevel().setBlock(pos, BuiltInRegistries.BLOCK.get(FarmSupplies.id(id)).defaultBlockState(), 3);
        var machine = (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
        if (!id.equals("honest_sewer")) machine.energy.setStored(100000);
        return machine;
    }

    private static void requireRecipeBook(DefinedMachineEntity machine, Element arrow) {
        var screen = Screens.get(machine.definition.id()).orElseThrow().screen();
        var drawers = DrawerRegistry.GLOBAL.resolve(new DrawerRegistry.Context(screen, TextMeasure.NONE, machine));
        require(drawers.stream().anyMatch(drawer -> drawer.type().equals(FarmRecipeBooks.DRAWER)),
            "The machine has a recipe-book drawer even without JEI");
        require(arrow.common().jeiRecipes().contains(machine.definition.id()),
            "The progress arrow opens the machine's JEI category");
    }

    private static void select(GameTestHelper h, DefinedMachineEntity machine, String processor, String action) {
        require(machine.screenFeatures().findProcessor(processor).orElseThrow().invoke(FarmSupplies.id(action),
            h.makeMockPlayer(GameType.SURVIVAL)).accepted(), "Machine accepts the selected mode");
    }

    private static void ticks(DefinedMachineEntity machine, int count) {
        for (int tick = 0; tick < count; tick++) machine.serverTick();
    }

    private static int count(DefinedMachineEntity machine, String group, Item item) {
        return machine.inventoryContents(group).stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
}
