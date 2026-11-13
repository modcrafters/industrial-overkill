package net.modcrafters.minecraft.industrialoverkill.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

@GameTestHolder(IndustrialOverkill.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RockStewTests {
    public static final ResourceLocation ID = ResourceLocation.parse("industrial_overkill:rock_stew_factory");

    public static final java.util.List<String> RECIPES = java.util.List.of(
        "cobblestone", "cobbled_deepslate", "stone", "andesite", "diorite", "granite", "tuff",
        "deepslate", "mossy_cobblestone", "basalt", "blackstone", "calcite", "obsidian");

    private static DefinedMachineEntity place(GameTestHelper h) {
        var block = BuiltInRegistries.BLOCK.get(ID);
        require(block != net.minecraft.world.level.block.Blocks.AIR, "Machine registered from XML");
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        return (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
    }

    private static void prime(DefinedMachineEntity machine) {
        require(machine.fluids.tank(0).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 1000,
            "Water tank accepts one bucket");
        require(machine.fluids.tank(1).fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE) == 1000,
            "Lava tank accepts one bucket");
        machine.energy.setStored(32000);
    }

    public static void fillStaticTanks(DefinedMachineEntity machine, net.minecraft.world.entity.player.Player player) {
        var previous = player.containerMenu;
        var menu = new zone.moddev.mc.lib.gui.runtime.MachineMenu(83, player.getInventory(), machine);
        player.containerMenu = menu;
        try {
            for (int index = 0; index < 2; index++) {
                var tank = (zone.moddev.mc.lib.gui.model.elements.FluidTankElement)
                    menu.bound.screen().find("tank_" + index).orElseThrow();
                var title = tank.tooltip(machine, ItemStack.EMPTY).getFirst();
                require(title.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents,
                    "Manual tank names remain translatable");
                var text = (net.minecraft.network.chat.contents.TranslatableContents) title.getContents();
                require(text.getKey().equals(ID + ".group." + (index == 0 ? "water" : "lava"))
                    && (index == 0 ? "Water Catalyst" : "Lava Catalyst").equals(text.getFallback()),
                    "Manual tank tooltip uses the XML group name");
                menu.setCarried(new ItemStack(index == 0 ? Items.LAVA_BUCKET : Items.WATER_BUCKET));
                tank.interact(new zone.moddev.mc.lib.gui.runtime.ScreenClick(player, menu, tank, 0, false, false, false, 0));
                require(machine.fluids.getFluidInTank(index).isEmpty(), "Static tank rejects the wrong fluid");
                menu.setCarried(new ItemStack(index == 0 ? Items.WATER_BUCKET : Items.LAVA_BUCKET));
                tank.interact(new zone.moddev.mc.lib.gui.runtime.ScreenClick(player, menu, tank, 0, false, false, false, 0));
                require(menu.getCarried().is(Items.BUCKET), "Manual gauge filling returns the empty bucket");
                menu.setCarried(ItemStack.EMPTY);
            }
            catalysts(machine);
            require(machine.fluids.getFluidInTank(2).isEmpty(), "Manual static filling leaves reagent tank empty");
        } finally {
            menu.removed(player);
            player.containerMenu = previous;
        }
    }

    @GameTest(template = "gym_empty")
    public static void staticTanksFillManuallyWithoutAutomationGroups(GameTestHelper h) {
        var machine = place(h);
        fillStaticTanks(machine, h.makeMockServerPlayerInLevel());
        var saved = machine.saveWithoutMetadata(h.getLevel().registryAccess());
        machine.loadWithComponents(saved, h.getLevel().registryAccess());
        catalysts(machine);
        require(machine.resources.automation().groups().stream()
            .noneMatch(g -> g.id().equals("water") || g.id().equals("lava")), "Manual tanks stay absent from side configuration after reload");
        h.succeed();
    }

    private static void tick(DefinedMachineEntity machine, int count) {
        for (int i = 0; i < count; i++) machine.serverTick();
    }

    private static int output(DefinedMachineEntity machine) {
        int count = 0;
        for (int i = 0; i < machine.items.getSlots(); i++)
            if (machine.items.getStackInSlot(i).is(Items.COBBLESTONE))
                count += machine.items.getStackInSlot(i).getCount();
        return count;
    }

    private static void catalysts(DefinedMachineEntity machine) {
        require(machine.fluids.getFluidInTank(0).getFluid() == Fluids.WATER
                && machine.fluids.getFluidInTank(0).getAmount() == 1000
                && machine.fluids.getFluidInTank(1).getFluid() == Fluids.LAVA
                && machine.fluids.getFluidInTank(1).getAmount() == 1000,
            "Water and lava remain exactly one bucket each");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static boolean indicator(DefinedMachineEntity machine) {
        return machine.getUpdateTag(machine.getLevel().registryAccess())
            .getCompound("mmdlib_machine").getCompound("visuals")
            .getBoolean("processors.rock_stew.running");
    }

    private static void select(DefinedMachineEntity machine, String recipe) {
        var item = recipe == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecraft:" + recipe)));
        require(machine.setScreenGhost(null, "input", 0, item), "Ghost input accepted");
    }

    private static int count(DefinedMachineEntity machine, net.minecraft.world.item.Item item) {
        int result = 0;
        for (int i = 0; i < machine.items.getSlots(); i++)
            if (machine.items.getStackInSlot(i).is(item)) result += machine.items.getStackInSlot(i).getCount();
        return result;
    }

    @GameTest(template = "gym_empty")
    public static void allStoneRecipesLoadWithGhostSamples(GameTestHelper h) {
        var type = ResourceLocation.parse("industrial_overkill:rock_stew");
        require(zone.moddev.mc.lib.machine.definition.MachineDefinition.ghostInputsFor(
            zone.moddev.mc.lib.machine.neoforge.MachineContent.definitions().values(), type)
            .equals(java.util.Set.of("input")), "JEI identifies the ghost input from storage");
        for (String name : RECIPES) {
            var id = ResourceLocation.fromNamespaceAndPath("industrial_overkill", "rock_stew/" + name);
            var holder = h.getLevel().getRecipeManager().byKey(id).orElseThrow(
                () -> new AssertionError("Shipped Stone Stew recipe failed to load: " + id));
            var entry = (zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry) holder.value();
            var sample = (zone.moddev.mc.lib.machine.ingredient.MachineIngredient.ItemPart)
                entry.recipe().inputs().get("input").getFirst();
            require(sample.items().test(new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("minecraft", name)))), "Ghost sample matches " + name);
            require(sample.consumed(), "JEI ghost label comes from storage, without a recipe flag");
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("minecraft", name));
            float hardness = block.defaultBlockState().getDestroySpeed(h.getLevel(), h.absolutePos(BlockPos.ZERO));
            require(entry.recipe().inputs().get("energy").getFirst().amount() == Math.round(hardness * 160),
                "Energy follows actual block hardness: " + name);
            require(entry.recipe().time() == Math.max(40, Math.round(hardness * 8)), "Harder blocks scale processing time: " + name);
            var reagent = entry.recipe().inputs().get("reagent");
            if (name.equals("cobblestone") || name.equals("cobbled_deepslate")) {
                require(reagent == null, "Ordinary cobbled blocks need no extra fluid");
            } else {
                var fluid = (zone.moddev.mc.lib.machine.ingredient.MachineIngredient.FluidPart) reagent.getFirst();
                var expected = name.equals("mossy_cobblestone") ? Fluids.WATER : Fluids.LAVA;
                int amount = switch (name) {
                    case "mossy_cobblestone", "basalt" -> 250;
                    case "deepslate", "blackstone" -> 200;
                    case "obsidian" -> 1000;
                    default -> 100;
                };
                require(fluid.consumed() && fluid.millibuckets() == amount
                    && fluid.fluids().stream().anyMatch(holderFluid -> holderFluid.value() == expected),
                    "Correct consumed reagent for " + name);
            }
            for (String fluid : java.util.List.of("water", "lava")) {
                var requirement = (zone.moddev.mc.lib.machine.ingredient.MachineIngredient.FluidPart)
                    entry.recipe().inputs().get(fluid).getFirst();
                require(!requirement.consumed() && requirement.millibuckets() == 1000,
                    "Every recipe requires both full static tanks without consuming them");
            }
        }
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void reloadRejectsGhostOutputsAndWrongInputKinds(GameTestHelper h) {
        var type = zone.moddev.mc.lib.machine.neoforge.MachineContent.recipeType(
            ResourceLocation.parse("industrial_overkill:rock_stew"));
        var item = new zone.moddev.mc.lib.machine.ingredient.MachineIngredient.ItemPart(
            net.minecraft.world.item.crafting.Ingredient.of(Items.STONE), 1);
        var output = new zone.moddev.mc.lib.machine.recipe.MachineProduct.ItemProduct(new ItemStack(Items.STONE), 1);
        var wrongInput = new zone.moddev.mc.lib.machine.recipe.MachineRecipe(
            java.util.Map.of("input", java.util.List.of(new zone.moddev.mc.lib.machine.ingredient.MachineIngredient.EnergyPart(1))),
            java.util.Map.of(), 1);
        var wrongOutput = new zone.moddev.mc.lib.machine.recipe.MachineRecipe(
            java.util.Map.of("input", java.util.List.of(item)), java.util.Map.of("input", java.util.List.of(output)), 1);
        for (var recipe : java.util.List.of(wrongInput, wrongOutput)) {
            try {
                zone.moddev.mc.lib.machine.neoforge.GeneratedMachineRecipes.generate(
                    java.util.List.of(new net.minecraft.world.item.crafting.RecipeHolder<>(
                        ResourceLocation.parse("industrial_overkill:test_invalid_ghost"),
                        new zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry(type, recipe))),
                    h.getLevel().registryAccess(),
                    new zone.moddev.mc.lib.machine.recipe.generation.ReloadTags(java.util.Map.of(), java.util.Map.of()));
                throw new AssertionError("Invalid ghost recipe accepted");
            } catch (IllegalArgumentException expected) {
                require(expected.getMessage().contains("group input"), "Failure identifies the ghost group");
            }
        }
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void ghostInputIsAnOrdinaryRecipeIngredientButNeverARealItem(GameTestHelper h) {
        var machine = place(h);
        prime(machine);
        machine.fluids.tank(2).fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE);
        var player = h.makeMockServerPlayerInLevel();
        player.setPos(machine.getBlockPos().getX(), machine.getBlockPos().getY(), machine.getBlockPos().getZ());
        var menu = new zone.moddev.mc.lib.gui.runtime.MachineMenu(82, player.getInventory(), machine);
        player.containerMenu = menu;
        try {
            var ghost = menu.elements.stream()
                .filter(zone.moddev.mc.lib.gui.model.elements.SlotElement.class::isInstance)
                .map(zone.moddev.mc.lib.gui.model.elements.SlotElement.class::cast)
                .filter(zone.moddev.mc.lib.gui.model.elements.SlotElement::ghost).findFirst().orElseThrow();
            var sample = new ItemStack(Items.STONE, 64);
            require(zone.moddev.mc.lib.gui.model.elements.GhostSlotElement.SELECT_GHOST.execute(
                new zone.moddev.mc.lib.gui.ElementAction.Context(player, menu, ghost), sample), "Normal ghost-slot action accepts sample");
            require(sample.getCount() == 64 && machine.screenGhost("input", 0).getCount() == 1, "Sample is copied and normalized, not consumed");
            var recipe = (zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry) h.getLevel().getRecipeManager()
                .byKey(ResourceLocation.parse("industrial_overkill:rock_stew/stone")).orElseThrow().value();
            require(!recipe.recipe().inputs().get("lava").getFirst().consumed()
                && !recipe.recipe().inputs().get("water").getFirst().consumed()
                && recipe.recipe().inputs().get("reagent").getFirst().consumed(),
                "Stone consumes only its reagent input");
            require(recipe.recipe().outputs().keySet().equals(java.util.Set.of("output")),
                "Reusable fluids need no duplicate outputs");
            require(zone.moddev.mc.lib.machine.recipe.RecipeMatcher.check(recipe.recipe(), machine.resources.recipes()).ok(),
                "Ordinary item ingredient matches ghost input");
            require(zone.moddev.mc.lib.machine.recipe.RecipeMatcher.consume(recipe.recipe(), machine.resources.recipes()),
                "Immediate recipe consumption accepts ghost requirement");
            require(machine.screenGhost("input", 0).is(Items.STONE), "Direct recipe consumption leaves sample intact");
            require(machine.fluids.getFluidInTank(2).getAmount() == 900,
                "Immediate consumption spends only reagent lava");
            catalysts(machine);
            var ghostGroup = (zone.moddev.mc.lib.machine.inventory.ItemGroup) machine.resources.recipes().group("input").orElseThrow();
            require(!ghostGroup.fits(java.util.List.of(new ItemStack(Items.STONE))), "Recipes cannot produce into ghost storage");
            try (var tx = machine.resources.recipes().begin()) {
                require(ghostGroup.extract(0, 1, tx) == 0, "Ghost sample cannot be extracted as a real item");
                tx.commit();
            }
            require(machine.items.getSlots() == 4 && machine.resources.automation().groups().stream().noneMatch(g -> g.id().equals("input")),
                "Ghost slots have no physical item slot or automation group");
            var items = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, machine.getBlockPos(), Direction.NORTH);
            require(items != null && items.getSlots() == 4, "Automation exposes only four real output slots");
            require(player.getInventory().isEmpty() && machine.items.getStackInSlot(0).isEmpty(), "Ghost editing creates no real items");
        } finally { menu.removed(player); }
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void defaultsAndLegacySelectorMigrateToGhostSamples(GameTestHelper h) {
        var machine = place(h);
        require(machine.screenGhost("input", 0).is(Items.COBBLESTONE), "New machine defaults to a cobblestone sample");
        var saved = machine.saveWithoutMetadata(h.getLevel().registryAccess());
        saved.remove("mmdlib_ghost_items");
        var old = saved.getCompound("features").getCompound("rock_stew");
        var selection = new net.minecraft.nbt.CompoundTag();
        selection.putString("recipe", "industrial_overkill:rock_stew/stone");
        old.put("selection", selection);
        machine.loadWithComponents(saved, h.getLevel().registryAccess());
        require(machine.screenGhost("input", 0).is(Items.STONE), "Old selected recipe migrates to its sample");
        old.put("selection", new net.minecraft.nbt.CompoundTag());
        machine.loadWithComponents(saved, h.getLevel().registryAccess());
        require(machine.screenGhost("input", 0).isEmpty(), "Old explicitly cleared selection stays empty");
        old.remove("selection");
        machine.loadWithComponents(saved, h.getLevel().registryAccess());
        require(machine.screenGhost("input", 0).is(Items.COBBLESTONE), "Older saves without a selection use the declared default");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void cobbledDeepslateNeedsNoReagentAndUsesHardnessCost(GameTestHelper h) {
        var machine = place(h);
        prime(machine);
        select(machine, "cobbled_deepslate");
        tick(machine, 41);
        require(count(machine, Items.COBBLED_DEEPSLATE) == 1 && machine.energy.stored() == 31440,
            "Cobbled deepslate produces one block for 560 FE");
        require(machine.fluids.getFluidInTank(2).isEmpty(), "No reagent needed for cobbled deepslate");
        catalysts(machine);
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void mossyCobblestoneConsumesWaterAndRejectsLava(GameTestHelper h) {
        var machine = place(h);
        prime(machine);
        select(machine, "mossy_cobblestone");
        machine.fluids.tank(2).fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE);
        tick(machine, 80);
        require(count(machine, Items.MOSSY_COBBLESTONE) == 0 && machine.energy.stored() == 32000,
            "Mossy cobblestone needs reagent water despite full static tanks");
        machine.fluids.tank(2).drain(1000, FluidAction.EXECUTE);
        machine.fluids.tank(2).fill(new FluidStack(Fluids.WATER, 250), FluidAction.EXECUTE);
        tick(machine, 41);
        require(count(machine, Items.MOSSY_COBBLESTONE) == 1 && machine.energy.stored() == 31680,
            "Mossy cobblestone costs 320 FE");
        require(machine.fluids.getFluidInTank(2).isEmpty(), "Consumes exactly 250 mB water");
        catalysts(machine);
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void selectedStoneConsumesOnlyLavaAndExactEnergy(GameTestHelper h) {
        var machine = place(h);
        prime(machine);
        machine.fluids.tank(2).fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE);
        select(machine, "stone");
        tick(machine, 410);
        require(count(machine, Items.STONE) == 10 && output(machine) == 0, "Selected stone only");
        require(machine.energy.stored() == 29600, "240 FE per stone");
        require(machine.fluids.getFluidInTank(2).isEmpty(), "100 mB reagent lava per stone");
        catalysts(machine);
        tick(machine, 100);
        require(count(machine, Items.STONE) == 10 && machine.energy.stored() == 29600, "Missing reagent lava stops work without fallback");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void basaltRequiresBothStaticFluidsAndConsumesOnlyReagent(GameTestHelper h) {
        var machine = place(h);
        machine.fluids.tank(1).fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE);
        machine.fluids.tank(2).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        machine.energy.setStored(32000);
        tick(machine, 80);
        require(count(machine, Items.BASALT) == 0 && machine.energy.stored() == 32000, "Default cobblestone does not fall back to basalt");
        select(machine, "basalt");
        tick(machine, 80);
        require(count(machine, Items.BASALT) == 0 && machine.energy.stored() == 32000,
            "Basalt also requires the full static water tank");
        machine.fluids.tank(0).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        tick(machine, 80);
        require(count(machine, Items.BASALT) == 0 && machine.energy.stored() == 32000,
            "Static lava cannot replace missing reagent lava");
        machine.fluids.tank(2).drain(1000, FluidAction.EXECUTE);
        machine.fluids.tank(2).fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE);
        tick(machine, 164);
        require(count(machine, Items.BASALT) == 4 && machine.energy.stored() == 31200, "200 FE per basalt");
        require(machine.fluids.getFluidInTank(2).isEmpty(), "250 mB reagent lava per basalt");
        catalysts(machine);
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void queuedRecipeSurvivesPauseReloadAndClear(GameTestHelper h) {
        var machine = place(h);
        prime(machine);
        machine.fluids.tank(2).fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE);
        tick(machine, 11);
        var processor = machine.features.processor("rock_stew").orElseThrow();
        processor.pause();
        select(machine, "stone");
        var saved = machine.saveWithoutMetadata(h.getLevel().registryAccess());
        machine.loadWithComponents(saved, h.getLevel().registryAccess());
        require(machine.screenGhost("input", 0).is(Items.STONE), "Queued selection persisted");
        require(processor.activeRecipe().orElseThrow().getPath().equals("rock_stew/cobblestone"), "Paused recipe remains original");
        processor.resume();
        tick(machine, 30);
        require(output(machine) == 1 && count(machine, Items.STONE) == 0 && machine.energy.stored() == 31680,
            "Paid cobblestone finishes at original cost");
        tick(machine, 11);
        select(machine, null);
        saved = machine.saveWithoutMetadata(h.getLevel().registryAccess());
        machine.loadWithComponents(saved, h.getLevel().registryAccess());
        require(machine.screenGhost("input", 0).isEmpty(), "Explicitly cleared selection survives reload");
        tick(machine, 80);
        require(count(machine, Items.STONE) == 1 && output(machine) == 1 && machine.energy.stored() == 31440,
            "Already started stone finishes then cleared selector stops new work");
        require(machine.fluids.getFluidInTank(2).getAmount() == 900,
            "Queued cycle retains its fluid costs");
        catalysts(machine);
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void invalidSelectionAndRecipeRemovalNeverChooseAnotherOutput(GameTestHelper h) {
        var machine = place(h);
        prime(machine);
        machine.fluids.tank(2).fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE);
        var processor = machine.features.processor("rock_stew").orElseThrow();
        require(!processor.selectionEnabled(), "Processor uses ordinary input matching");
        machine.setScreenGhost(null, "input", 0, new ItemStack(Items.DIAMOND));
        tick(machine, 80);
        require(machine.energy.stored() == 32000 && output(machine) == 0, "Unmatched ghost input runs nothing");
        select(machine, "stone");
        tick(machine, 11);
        var manager = h.getLevel().getRecipeManager();
        var original = java.util.List.copyOf(manager.getRecipes());
        try {
            var indexed = processor.recipeChoices(h.getLevel());
            require(indexed == processor.recipeChoices(h.getLevel()), "Recipe catalogue cached");
            manager.replaceRecipes(original.stream().filter(recipe -> !recipe.id().getPath().equals("rock_stew/stone")).toList());
            require(processor.recipeChoices(h.getLevel()).size() == indexed.size() - 1, "Reload invalidates recipe catalogue");
            tick(machine, 120);
            require(count(machine, Items.STONE) == 1 && output(machine) == 0 && machine.energy.stored() == 31760,
                "Reserved cycle finishes, removed selection starts no new work or fallback");
        } finally {
            manager.replaceRecipes(original);
        }
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void selectedRecipeWithFullOutputsSpendsNothing(GameTestHelper h) {
        var machine = place(h);
        prime(machine);
        machine.fluids.tank(2).fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE);
        select(machine, "stone");
        for (int i = 0; i < 4; i++) machine.items.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
        tick(machine, 100);
        require(machine.energy.stored() == 32000 && count(machine, Items.STONE) == 0, "No energy spent for blocked selection");
        catalysts(machine);
        machine.items.extractItem(0, 64, false);
        tick(machine, 41);
        require(count(machine, Items.STONE) == 1 && machine.energy.stored() == 31760, "Selected recipe starts after space opens");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void indicatorTracksWorkCompletionStallsAndPauses(GameTestHelper h) {
        var machine = place(h);
        require(!indicator(machine), "Indicator starts inactive");
        prime(machine);
        machine.serverTick();
        require(indicator(machine), "Tracking snapshot shows running without opening a screen");
        var processor = machine.features.processor("rock_stew").orElseThrow();
        int[] activityNotifications = {0};
        var unsubscribe = processor.activityChanges().subscribe(() -> activityNotifications[0]++);
        for (int tick = 0; tick < 100 && processor.completedCycles().signum() == 0; tick++) machine.serverTick();
        require(processor.completedCycles().signum() > 0, "A recipe completed within its expected duration");
        require(activityNotifications[0] == 0, "Continuous progress and energy use do not invalidate the running indicator");
        unsubscribe.run();
        require(indicator(machine), "Completion tick keeps the indicator lit");
        machine.energy.setStored(0);
        tick(machine, 2);
        require(!indicator(machine), "Power starvation turns the indicator off");
        machine.energy.setStored(32000);
        machine.serverTick();
        require(indicator(machine), "Restored power turns the indicator on");
        processor.pause();
        machine.serverTick();
        require(!indicator(machine), "Manual pause turns the indicator off");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void makesCobblestoneAndReusesFullTanks(GameTestHelper h) {
        var machine = place(h);
        prime(machine);
        tick(machine, 41 * 10);
        require(output(machine) == 10, "Ten complete cycles produce ten cobblestone");
        require(machine.energy.stored() == 28800, "Exactly 320 FE per cobblestone");
        catalysts(machine);
        require(machine.screenGhost("input", 0).is(Items.COBBLESTONE), "Repeated cycles never consume the ghost input");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void needsBothCatalystsAndPower(GameTestHelper h) {
        var machine = place(h);
        machine.energy.setStored(32000);
        tick(machine, 50);
        require(output(machine) == 0 && machine.energy.stored() == 32000, "No work without fluids");
        machine.fluids.tank(0).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        tick(machine, 50);
        require(output(machine) == 0 && machine.energy.stored() == 32000, "Water alone cannot work");
        machine.fluids.tank(1).fill(new FluidStack(Fluids.LAVA, 999), FluidAction.EXECUTE);
        tick(machine, 50);
        require(output(machine) == 0 && machine.energy.stored() == 32000, "Requires a full lava bucket");
        machine.fluids.tank(1).fill(new FluidStack(Fluids.LAVA, 1), FluidAction.EXECUTE);
        machine.energy.setStored(0);
        tick(machine, 50);
        require(output(machine) == 0, "No free cobblestone without power");
        catalysts(machine);
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void fullOutputsAndPausedMachineSpendNothing(GameTestHelper h) {
        var machine = place(h);
        prime(machine);
        for (int i = 0; i < 4; i++) machine.items.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
        tick(machine, 100);
        require(output(machine) == 256 && machine.energy.stored() == 32000, "Full output spends nothing");
        machine.items.extractItem(3, 64, false);
        var processor = machine.screenFeatures().processors().getFirst();
        processor.pause();
        tick(machine, 100);
        require(output(machine) == 192 && machine.energy.stored() == 32000, "Manual pause spends nothing");
        processor.resume();
        tick(machine, 41);
        require(output(machine) == 193 && machine.energy.stored() == 31680, "Extraction lets work resume");
        catalysts(machine);
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void stalledCycleAndReloadKeepPaidProgress(GameTestHelper h) {
        var machine = place(h);
        prime(machine);
        machine.energy.setStored(80);
        tick(machine, 31);
        require(output(machine) == 0 && machine.getProgress() == 10, "Power loss preserves paid progress");
        catalysts(machine);
        var saved = machine.saveWithoutMetadata(h.getLevel().registryAccess());
        machine.loadWithComponents(saved, h.getLevel().registryAccess());
        require(machine.getProgress() == 10 && machine.energy.stored() == 0, "Save/load preserves stalled state");
        machine.energy.setStored(240);
        tick(machine, 30);
        require(output(machine) == 1 && machine.energy.stored() == 0, "Only unpaid remainder charged after reload");
        catalysts(machine);
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void automationFiltersAndSideSettingsAreEnforced(GameTestHelper h) {
        var machine = place(h);
        var level = h.getLevel();
        var pos = machine.getBlockPos();
        var items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.NORTH);
        var fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.NORTH);
        var power = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, Direction.NORTH);
        require(items != null && fluids != null && power != null, "All three capabilities available");
        require(!items.insertItem(0, new ItemStack(Items.COBBLESTONE), false).isEmpty(), "Outputs reject insertion");
        require(machine.resources.automation().groups().stream()
            .noneMatch(g -> g.id().equals("water") || g.id().equals("lava")), "Static tanks have no automation groups");
        require(fluids.fill(new FluidStack(Fluids.WATER, 2000), FluidAction.EXECUTE) == 1000,
            "Automation fills only the reagent tank");
        require(machine.fluids.getFluidInTank(0).isEmpty() && machine.fluids.getFluidInTank(1).isEmpty()
            && machine.fluids.getFluidInTank(2).getAmount() == 1000, "Static tanks remain empty after automation fill");
        require(fluids.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE) == 0, "Reagent cannot mix fluids");
        require(fluids.drain(1000, FluidAction.EXECUTE).isEmpty(), "Automation cannot drain input fluids");
        require(power.receiveEnergy(2000, false) == 1000 && power.extractEnergy(128, false) == 0,
            "Inherited 1000 FE input rate and no power extraction");
        var energyGroup = machine.resources.automation().groups().stream()
            .filter(g -> g.id().equals("energy")).findFirst().orElseThrow();
        energyGroup.sides().set(RelativeFace.FRONT, IoMode.DISABLED);
        require(power.receiveEnergy(128, false) == 0, "Cached power capability obeys changed face setting");
        prime(machine);
        tick(machine, 41);
        require(items.extractItem(0, 1, false).is(Items.COBBLESTONE), "Automation extracts completed output");
        catalysts(machine);
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void redstonePauseAndReservationsProtectCatalysts(GameTestHelper h) {
        var machine = place(h);
        prime(machine);
        tick(machine, 11);
        require(machine.fluids.tank(0).drain(1000, FluidAction.EXECUTE).isEmpty()
            && machine.fluids.tank(1).drain(1000, FluidAction.EXECUTE).isEmpty(), "Running cycle reserves catalysts even against manual draining");
        var redstone = machine.screenRedstone();
        redstone.mode(RelativeFace.BACK.ordinal(), RedstoneControl.Mode.READ);
        redstone.range(RelativeFace.BACK.ordinal(), 0, 0);
        long heldPower = machine.energy.stored();
        tick(machine, 50);
        require(redstone.paused() && machine.energy.stored() == heldPower && output(machine) == 0,
            "Configured redstone holds paid work");
        catalysts(machine);
        h.succeed();
    }
}
