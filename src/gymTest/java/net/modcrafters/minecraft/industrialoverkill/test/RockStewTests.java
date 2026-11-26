package net.modcrafters.minecraft.industrialoverkill.test;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.modcrafters.minecraft.industrialoverkill.stew.RockStewProcessor;
import net.modcrafters.minecraft.industrialoverkill.stew.RockStewConfig;
import net.modcrafters.minecraft.industrialoverkill.stew.RockStewRecipes;
import net.modcrafters.minecraft.industrialoverkill.stew.RockStewScreen;
import zone.moddev.mc.lib.gui.model.elements.FluidTankElement;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.ScreenClick;
import zone.moddev.mc.lib.machine.feature.RecipeCycle;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

@GameTestHolder("industrial_overkill_stew")
@PrefixGameTestTemplate(false)
public final class RockStewTests {
    public static final ResourceLocation ID = RockStewRecipes.id("rock_stew_factory");
    public static final List<String> RECIPES = List.of("cobblestone", "cobbled_deepslate", "stone", "andesite", "diorite", "granite", "tuff", "deepslate", "mossy_cobblestone", "basalt", "blackstone", "calcite", "obsidian");
    private static final String TEMPLATE = "gym_empty";
    private static DefinedMachineEntity place(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos, BuiltInRegistries.BLOCK.get(ID).defaultBlockState(), 3);
        var m = (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
        m.fluids.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        m.fluids.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE);
        m.energy.setStored(100000);
        return m;
    }
    private static void tick(DefinedMachineEntity m, int count) { for (int i = 0; i < count; i++) m.serverTick(); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static RockStewProcessor panePath(DefinedMachineEntity m) {
        var p = RockStewProcessor.from(m);
        require(p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.GLASS_PANE)), "Crafting path admitted");
        m.serverTick();
        require(p.chain().size() == 5, "Generate, grind, grind, smelt, craft");
        return p;
    }
    public static void fillStaticTanks(DefinedMachineEntity m, net.minecraft.world.entity.player.Player player) {
        var previous = player.containerMenu;
        var menu = new MachineMenu(83, player.getInventory(), m);
        player.containerMenu = menu;
        try {
            for (int i = 0; i < 2; i++) {
                var tank = (FluidTankElement) menu.bound.screen().find("tank_" + i).orElseThrow();
                menu.setCarried(new ItemStack(i == 0 ? Items.WATER_BUCKET : Items.LAVA_BUCKET));
                tank.interact(new ScreenClick(player, menu, tank, 0, false, false, false, 0));
                require(menu.getCarried().is(Items.BUCKET), "Horizontal gauge returns an empty bucket");
                menu.setCarried(ItemStack.EMPTY);
            }
        } finally { menu.removed(player); player.containerMenu = previous; }
    }

    @GameTest(template = TEMPLATE)
    public static void poweredBaseSuppliesWorkingGearboxAndCapacitor(GameTestHelper h) {
        var m = place(h);
        require(m.screenInventory("output").getSlots() == RockStewProcessor.SLOTS,
            "Inherited component inventories preserve every output slot");
        var player = h.makeMockServerPlayerInLevel();
        var menu = new MachineMenu(95, player.getInventory(), m);
        try {
            require(menu.bound.screen().find("upgrades").isPresent()
                && menu.bound.screen().find("gearbox").isPresent()
                && menu.bound.screen().find("capacitor").isPresent(),
                "The powered base supplies the drawer and both sockets without Rock Stew XML declarations");
        } finally { menu.removed(player); }
        var capacitor = new ItemStack(zone.moddev.mc.lib.MMDLib.CAPACITOR_UPGRADE.get(), 8);
        m.screenInventory("mmdlib_capacitor").setStackInSlot(0, capacitor.copy());
        require(m.energy.capacity() == 200000 && m.energy.receiveEnergy(9999, true) == 2000,
            "Eight inherited capacitors double base storage and input rate");
        var p = RockStewProcessor.from(m);
        require(p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.COBBLESTONE)), "Select generation");
        tick(m, 1);
        int before = p.chain().cycle(0).progress();
        long energy = m.energy.stored();
        tick(m, 4);
        int ordinaryWork = p.chain().cycle(0).progress() - before;
        long ordinaryCost = energy - m.energy.stored();
        var gearbox = new ItemStack(zone.moddev.mc.lib.MMDLib.GEARBOX_UPGRADE.get(), 8);
        m.screenInventory("mmdlib_gearbox").setStackInSlot(0, gearbox.copy());
        require(p.displaySize() == 5, "Installed upgrades are never displayed as Rock Stew output stages");
        before = p.chain().cycle(0).progress();
        energy = m.energy.stored();
        tick(m, 4);
        require(ordinaryWork == 4 && p.chain().cycle(0).progress() - before == ordinaryWork * 2
            && m.energy.stored() == energy - ordinaryCost * 2,
            "Inherited gearboxes double Rock Stew processing speed and FE usage rate");
        require(ItemStack.matches(gearbox, m.screenInventory("mmdlib_gearbox").getStackInSlot(0))
            && ItemStack.matches(capacitor, m.screenInventory("mmdlib_capacitor").getStackInSlot(0)),
            "Shared upgrades never wear out");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void chunkLoadRestoresActiveWorkAndVisuals(GameTestHelper h) {
        var m = place(h);
        var p = RockStewProcessor.from(m);
        require(p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.COBBLESTONE)), "Select generation");
        p.stock(0); p.stock(0);
        tick(m, 15);
        var registry = h.getLevel().registryAccess();
        var saved = m.saveWithFullMetadata(registry);
        // Chunk loading creates a fresh entity and calls reset/restore outside a processor tick.
        var restored = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(m.getBlockPos(), m.getBlockState(), saved, registry);
        require(restored instanceof DefinedMachineEntity, "Chunk loader must not discard Rock Stew after a lifecycle exception");
        var copy = (DefinedMachineEntity) restored;
        copy.setLevel(h.getLevel());
        var loaded = RockStewProcessor.from(copy);
        require(loaded.problem().isEmpty() && loaded.chain().cycle(0).progress() == 14, "Paid work restores on a fresh entity");
        require(loaded.stockMode(0) == 1 && copy.energy.getEnergyStored() == m.energy.getEnergyStored(), "Stock mode and energy survive load");
        var light = zone.moddev.mc.lib.gui.model.ProcessorVariable.parse("$processors.rock_stew.progress_lights_1");
        require(light.read(copy).equals(3), "Restoration recomputes visual frame safely outside a tick");
        loaded.reset();
        require(light.read(copy).equals(0), "Standalone lifecycle reset can clear visual state");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void frontLightsTrackTheGenerator(GameTestHelper h) {
        var m = place(h);
        var p = RockStewProcessor.from(m);
        var lights = zone.moddev.mc.lib.gui.model.ProcessorVariable.parse("$processors.rock_stew.progress_lights");
        var stageLight = zone.moddev.mc.lib.gui.model.ProcessorVariable.parse("$processors.rock_stew.progress_lights_1");
        require(lights.read(m).equals(0), "Unconfigured machine has no lit lamps");
        require(p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.COBBLESTONE)), "Select generator");
        p.stock(0); p.stock(0); // Keep one batch, then stop.
        tick(m, 1);
        require(lights.read(m).equals(1), "First third lights one lamp");
        tick(m, 14);
        require(lights.read(m).equals(2), "Second third lights two lamps");
        require(stageLight.read(m).equals(3), "Fourteen paid ticks select frame three of the eight-frame stage strip");
        int paid = p.chain().cycle(0).progress();
        p.setPaused(true); tick(m, 10);
        require(p.chain().cycle(0).progress() == paid && lights.read(m).equals(2), "Pause retains progress and lamps");
        p.setPaused(false);
        m.energy.setStored(0); tick(m, 10);
        require(p.chain().cycle(0).progress() == paid && lights.read(m).equals(2), "Energy stall retains lamps");
        var registry = h.getLevel().registryAccess();
        m.loadWithComponents(m.saveWithoutMetadata(registry), registry);
        require(lights.read(m).equals(2), "Reload restores generator band");
        var client = (DefinedMachineEntity) m.getType().create(m.getBlockPos(), m.getBlockState());
        require(client != null, "Create tracking replica");
        client.loadWithComponents(m.getUpdateTag(registry), registry);
        require(lights.read(client).equals(2), "Tracking players receive lamps without opening a menu");
        require(stageLight.read(client).equals(3), "Per-stage frame survives pause, stall, reload and tracking sync");
        m.energy.setStored(100000); tick(m, 14);
        require(lights.read(m).equals(3), "Final third lights three lamps");
        require(stageLight.read(m).equals(5), "Twenty-eight paid ticks select frame five");
        tick(m, 8);
        require(stageLight.read(m).equals(7), "Last active frame is visible before completion");
        var cycle = p.chain().cycle(0);
        tick(m, cycle.plan().orElseThrow().time() - cycle.progress());
        require(m.items.getStackInSlot(0).getCount() == 1 && lights.read(m).equals(0), "Completed generator clears lamps");
        require(stageLight.read(m).equals(0), "Completed stage uses idle frame zero");
        require(p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.GLASS)), "Select downstream route");
        m.items.restoreStack(0, new ItemStack(Items.COBBLESTONE, 64));
        m.items.restoreStack(1, new ItemStack(Items.GRAVEL, 64));
        m.items.restoreStack(2, new ItemStack(Items.SAND, 64));
        m.fluids.fill(new FluidStack(Fluids.LAVA, 3000), FluidAction.EXECUTE);
        tick(m, 1);
        require(p.chain().cycle(3).plan().isPresent() && lights.read(m).equals(0), "Downstream work alone leaves generator lamps dark");
        m.items.restoreStack(0, ItemStack.EMPTY);
        p.stock(0); // Keep a generator batch even while downstream has stock.
        tick(m, 1);
        require(lights.read(m).equals(1), "Generator controls lamps on a multi-stage route");
        p.reset();
        require(lights.read(m).equals(0), "Reset clears lamps");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void frontLightsTrackAllFiveStagesIndependently(GameTestHelper h) {
        var m = place(h);
        var p = RockStewProcessor.from(m);
        var lights = java.util.stream.IntStream.rangeClosed(1, 5).mapToObj(stage ->
            zone.moddev.mc.lib.gui.model.ProcessorVariable.parse("$processors.rock_stew.progress_lights_" + stage)).toList();
        require(lights.stream().allMatch(key -> key.read(m).equals(0)), "All stage lights start idle");
        require(p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.GLASS_PANE)), "Select all five stages");
        var inputs = List.of(Items.COBBLESTONE, Items.GRAVEL, Items.SAND, Items.GLASS);
        for (int stage = 0; stage < 4; stage++) {
            m.items.restoreStack(stage, new ItemStack(inputs.get(stage), 8));
            p.stock(stage); p.stock(stage); // Keep full stacks so every stage can start.
        }
        m.fluids.fill(new FluidStack(Fluids.LAVA, 3000), FluidAction.EXECUTE);
        tick(m, 1);
        require(lights.stream().allMatch(key -> key.read(m).equals(1)), "Concurrent stages each select their first active frame");
        var registry = h.getLevel().registryAccess();
        var client = (DefinedMachineEntity) m.getType().create(m.getBlockPos(), m.getBlockState());
        require(client != null, "Create tracking replica");
        client.loadWithComponents(m.getUpdateTag(registry), registry);
        require(lights.stream().allMatch(key -> key.read(client).equals(1)), "All five frames synchronize without a menu");

        var generator = p.chain().cycle(0).plan().orElseThrow();
        m.energy.setStored(generator.energy().get("energy") / generator.time());
        tick(m, 1);
        require(p.chain().cycle(0).progress() == 1 && p.chain().result(1) == RecipeCycle.Result.STALLED,
            "The generator advances before a later stage runs short of power");
        var running = zone.moddev.mc.lib.gui.model.ProcessorVariable.parse("$processors.rock_stew.running");
        client.loadWithComponents(m.getUpdateTag(registry), registry);
        require(Boolean.TRUE.equals(running.read(client)), "Actual work keeps the casing lights on during a partial stall");
        tick(m, 1);
        require(Boolean.FALSE.equals(running.read(m)), "The casing lights turn off when no stage advances");
        m.energy.setStored(100000);

        require(p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.GLASS)), "Shorten path");
        require(lights.stream().allMatch(key -> key.read(m).equals(0)), "Path changes clear all old frames immediately");
        m.items.restoreStack(0, new ItemStack(Items.COBBLESTONE, 64));
        m.items.restoreStack(1, new ItemStack(Items.GRAVEL, 64));
        m.items.restoreStack(2, new ItemStack(Items.SAND, 64));
        tick(m, 1);
        require(lights.get(0).read(m).equals(0) && lights.get(3).read(m).equals(1)
            && lights.get(4).read(m).equals(0), "Smelter animates independently; stocked generator and unused fifth stage stay idle");
        p.reset();
        require(lights.stream().allMatch(key -> key.read(m).equals(0)), "Reset clears every stage");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void frontDisplayTracksSelectedProductWithoutAMenu(GameTestHelper h) {
        var machine = place(h);
        var processor = RockStewProcessor.from(machine);
        var display = zone.moddev.mc.lib.gui.model.ProcessorVariable.parse("$processors.rock_stew.display_product");
        require(zone.moddev.mc.lib.machine.neoforge.MachineContent.definitions().get(ID)
            .behavior().itemDisplay().variable().equals(display), "Front display is bound by machine XML");
        var registry = h.getLevel().registryAccess();
        var client = (DefinedMachineEntity) machine.getType().create(machine.getBlockPos(), machine.getBlockState());
        require(client != null, "Create detached tracking replica");
        require(display.read(machine).equals(""), "Unconfigured machine has no front display");
        require(processor.select(RockStewRecipes.DEFAULT, new ItemStack(Items.GLASS_PANE)), "Select pane production");
        require(machine.items.getStackInSlot(4).isEmpty(), "No completed final product is needed for the preview");
        client.loadWithComponents(machine.getUpdateTag(registry), registry);
        require(display.read(client).equals("minecraft:glass_pane"), "Tracking update carries the final product without an open menu");
        var saved = machine.saveWithoutMetadata(registry);
        machine.loadWithComponents(saved, registry);
        require(display.read(machine).equals("minecraft:glass_pane"), "Selected front display survives reload");
        require(processor.select(RockStewRecipes.DEFAULT, new ItemStack(Items.COBBLESTONE_STAIRS)), "Change production path");
        client.loadWithComponents(machine.getUpdateTag(registry), registry);
        require(display.read(client).equals("minecraft:cobblestone_stairs"), "Front changes immediately with selection");
        processor.reset();
        client.loadWithComponents(machine.getUpdateTag(registry), registry);
        require(display.read(client).equals(""), "Removing the selected path clears the tracked display");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void configurableTagExclusions(GameTestHelper h) {
        require(RockStewConfig.DEFAULT_EXCLUDED_TAGS.equals(List.of("c:ingots", "c:ingots/*")), "Default ingot policy");
        require(RockStewConfig.matchesTag("c:ingots", ResourceLocation.parse("c:ingots")), "Exact tag matches");
        require(!RockStewConfig.matchesTag("c:ingots", ResourceLocation.parse("c:ingots/iron")), "Exact tag is not a prefix");
        require(RockStewConfig.matchesTag("c:ingots/*", ResourceLocation.parse("c:ingots/obsidian"))
            && RockStewConfig.matchesTag("c:ingots/*", ResourceLocation.parse("c:ingots/alloys/test")), "Wildcard matches descendants");
        for (String tag : List.of("c:ingots", "c:ingots_extra/iron", "other:ingots/iron"))
            require(!RockStewConfig.matchesTag("c:ingots/*", ResourceLocation.parse(tag)), "Wildcard respects namespace and path boundaries");
        for (Object invalid : List.of("ingots", "#c:ingots", "c:", "c:/*", "c:ing*", "c:ingots/**", " c:ingots", 123))
            require(!RockStewConfig.validPattern(invalid), "Invalid pattern rejected: " + invalid);

        var m = place(h);
        require(RockStewConfig.SPEC.isLoaded(), "World server config is loaded");
        var previous = List.copyOf(RockStewConfig.EXCLUDED_ITEM_TAGS.get());
        var previousAllowed = List.copyOf(RockStewConfig.ALLOWED_ITEM_TAGS.get());
        var ingot = new ItemStack(Items.IRON_INGOT);
        var sourceId = RockStewRecipes.id("rock_stew/obsidian");
        var originalRecipes = installObsidianTestPaths(h);
        try {
            // In-memory overrides exercise policy changes without saving edits to the user's file.
            RockStewConfig.ALLOWED_ITEM_TAGS.set(List.of("industrial_overkill:rock_stew_building_blocks", "c:ingots/iron"));
            RockStewConfig.ALLOWED_ITEM_TAGS.clearCache(); // Simulate reopening the world for this world-restart setting.
            RockStewConfig.EXCLUDED_ITEM_TAGS.set(List.of("c:ingots/iron"));
            RockStewConfig.EXCLUDED_ITEM_TAGS.clearCache(); // Simulate reopening the world for this world-restart setting.
            RockStewRecipes.invalidate();
            require(RockStewConfig.excluded(ingot) && !RockStewConfig.excluded(new ItemStack(Items.COPPER_INGOT)),
                "Configured list replaces defaults rather than appending to them");
            var p = RockStewProcessor.from(m);
            require(!p.select(sourceId, ingot), "Configured exact exclusion rejects selection");
            RockStewConfig.EXCLUDED_ITEM_TAGS.set(List.of());
            RockStewConfig.EXCLUDED_ITEM_TAGS.clearCache(); // Simulate reopening the world for this world-restart setting.
            RockStewRecipes.invalidate();
            require(!RockStewConfig.excluded(ingot) && p.select(sourceId, ingot), "Empty list allows the formerly excluded path");
            require(sourceGraph(h, sourceId).indexOf(ingot) >= 0, "Recipe browser graph uses the configured list");
            m.items.restoreStack(1, ingot.copy());
            RockStewConfig.EXCLUDED_ITEM_TAGS.set(RockStewConfig.DEFAULT_EXCLUDED_TAGS);
            RockStewConfig.EXCLUDED_ITEM_TAGS.clearCache(); // Simulate reopening the world for this world-restart setting.
            RockStewRecipes.invalidate();
            tick(m, 1);
            require(p.chain().draining() && p.chain().drained(), "Excluded existing path stops on policy revalidation");
            require(m.items.getStackInSlot(1).is(ingot.getItem()), "Policy changes preserve existing stock");
        } finally {
            RockStewConfig.EXCLUDED_ITEM_TAGS.set(previous);
            RockStewConfig.EXCLUDED_ITEM_TAGS.clearCache(); // Simulate reopening the world for this world-restart setting.
            RockStewConfig.ALLOWED_ITEM_TAGS.set(previousAllowed);
            RockStewConfig.ALLOWED_ITEM_TAGS.clearCache(); // Simulate reopening the world for this world-restart setting.
            h.getLevel().getRecipeManager().replaceRecipes(originalRecipes);
            RockStewRecipes.invalidate();
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void strictBuildingBlockAllowlist(GameTestHelper h) {
        var m = place(h);
        var root = RockStewRecipes.sources(h.getLevel()).stream()
            .filter(source -> source.id().equals(RockStewRecipes.DEFAULT)).findFirst().orElseThrow();
        var graph = RockStewRecipes.graph(h.getLevel(), root);
        for (var item : List.of(Items.COBBLESTONE, Items.STONE, Items.COBBLESTONE_STAIRS,
            Items.STONE_SLAB, Items.GRAVEL, Items.SAND, Items.GLASS, Items.GLASS_PANE)) {
            require(RockStewConfig.allowed(new ItemStack(item)), "Building material is allowlisted: " + item);
            require(graph.indexOf(new ItemStack(item)) >= 0, "Building path remains reachable: " + item);
        }
        var processor = RockStewProcessor.from(m);
        for (var item : List.of(Items.STONE_BUTTON, Items.STONE_PRESSURE_PLATE,
            Items.FURNACE, Items.CRAFTING_TABLE, Items.LEVER, Items.IRON_INGOT)) {
            require(!RockStewConfig.allowed(new ItemStack(item)), "Functional or loose item is outside the allowlist: " + item);
            require(graph.indexOf(new ItemStack(item)) < 0, "Drawer/JEI omit non-building paths: " + item);
            require(!processor.select(RockStewRecipes.DEFAULT, new ItemStack(item)), "Server rejects non-building selection: " + item);
        }
        var previous = List.copyOf(RockStewConfig.ALLOWED_ITEM_TAGS.get());
        try {
            require(processor.select(RockStewRecipes.DEFAULT, new ItemStack(Items.GLASS_PANE)), "Select building path");
            m.items.restoreStack(0, new ItemStack(Items.COBBLESTONE, 8));
            RockStewConfig.ALLOWED_ITEM_TAGS.set(List.of());
            RockStewConfig.ALLOWED_ITEM_TAGS.clearCache(); // Simulate reopening the world for this world-restart setting.
            RockStewRecipes.invalidate();
            require(RockStewRecipes.sources(h.getLevel()).isEmpty(), "Empty allowlist fails closed");
            tick(m, 1);
            require(processor.chain().draining() && processor.chain().drained(), "Disallowed saved path stops");
            require(m.items.getStackInSlot(0).getCount() == 8, "Policy changes retain old inventory");
        } finally {
            RockStewConfig.ALLOWED_ITEM_TAGS.set(previous);
            RockStewConfig.ALLOWED_ITEM_TAGS.clearCache(); // Simulate reopening the world for this world-restart setting.
            RockStewRecipes.invalidate();
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void materialSpecificIngotIsExcluded(GameTestHelper h) {
        var m = place(h);
        var ingot = new ItemStack(Items.IRON_INGOT);
        var stairs = new ItemStack(Items.BLACKSTONE_STAIRS);
        require(ingot.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
            ResourceLocation.parse("c:ingots/iron"))), "Iron ingot has its material-specific tag");
        var sourceId = RockStewRecipes.id("rock_stew/obsidian");
        var previousAllowed = List.copyOf(RockStewConfig.ALLOWED_ITEM_TAGS.get());
        var originalRecipes = installObsidianTestPaths(h);
        try {
            RockStewConfig.ALLOWED_ITEM_TAGS.set(List.of("industrial_overkill:rock_stew_building_blocks", "c:ingots/iron"));
            RockStewConfig.ALLOWED_ITEM_TAGS.clearCache();
            RockStewRecipes.invalidate();
            var source = RockStewRecipes.sources(h.getLevel()).stream()
                .filter(candidate -> candidate.id().equals(sourceId)).findFirst().orElseThrow();
            var graph = RockStewRecipes.graph(h.getLevel(), source);
            require(graph.indexOf(new ItemStack(Items.OBSIDIAN)) == 0 && graph.indexOf(stairs) >= 0,
                "Obsidian generation and its building-block path remain available");
            require(graph.indexOf(ingot) < 0, "Material-specific ingot cannot appear in the panel or JEI graph");
            require(graph.indexOf(new ItemStack(Items.POLISHED_BLACKSTONE_BRICK_STAIRS)) < 0,
                "Filtering does not expand through the rejected ingot to an allowed building block");
            var p = RockStewProcessor.from(m);
            require(!p.select(sourceId, ingot), "Server refuses direct ingot selection");
            require(p.select(sourceId, stairs), "Legal building-block path can still be selected");
        } finally {
            RockStewConfig.ALLOWED_ITEM_TAGS.set(previousAllowed);
            RockStewConfig.ALLOWED_ITEM_TAGS.clearCache();
            h.getLevel().getRecipeManager().replaceRecipes(originalRecipes);
            RockStewRecipes.invalidate();
        }
        h.succeed();
    }

    private static List<RecipeHolder<?>> installObsidianTestPaths(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        var recipes = new java.util.ArrayList<>(original);
        recipes.add(testGrinding("obsidian_ingot", Items.OBSIDIAN, Items.IRON_INGOT));
        recipes.add(testGrinding("obsidian_stairs", Items.OBSIDIAN, Items.BLACKSTONE_STAIRS));
        recipes.add(testGrinding("ingot_building_block", Items.IRON_INGOT, Items.POLISHED_BLACKSTONE_BRICK_STAIRS));
        manager.replaceRecipes(recipes);
        RockStewRecipes.invalidate();
        return original;
    }

    @GameTest(template = TEMPLATE)
    public static void catalogLimitsPathsToFiveStepsAndExcludesIngots(GameTestHelper h) {
        var m = place(h);
        var manager = h.getLevel().getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        var recipes = new java.util.ArrayList<>(original);
        var root = RockStewRecipes.sources(h.getLevel()).stream()
            .filter(source -> source.id().equals(RockStewRecipes.DEFAULT)).findFirst().orElseThrow();
        var materials = new net.minecraft.world.item.Item[]{Items.COBBLESTONE, Items.WHITE_WOOL,
            Items.ORANGE_WOOL, Items.MAGENTA_WOOL, Items.LIGHT_BLUE_WOOL, Items.YELLOW_WOOL};
        for (int i = 0; i < materials.length - 1; i++)
            recipes.add(testGrinding("depth_" + i, materials[i], materials[i + 1]));
        recipes.add(testGrinding("ingot", Items.COBBLESTONE, Items.IRON_INGOT));
        recipes.add(testGrinding("after_ingot", Items.IRON_INGOT, Items.DIAMOND));
        var ingotSource = RockStewRecipes.id("test/ingot_source");
        recipes.add(new net.minecraft.world.item.crafting.RecipeHolder<>(ingotSource,
            new zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry(RockStewRecipes.SOURCES,
                new zone.moddev.mc.lib.machine.recipe.MachineRecipe(root.value().recipe().inputs(),
                    java.util.Map.of("output", List.of(new zone.moddev.mc.lib.machine.recipe.MachineProduct.ItemProduct(
                        new ItemStack(Items.GOLD_INGOT), 1))), 40))));
        try {
            manager.replaceRecipes(recipes); RockStewRecipes.invalidate();
            var graph = RockStewRecipes.graph(h.getLevel(), root);
            int fifth = graph.indexOf(new ItemStack(Items.LIGHT_BLUE_WOOL));
            require(fifth >= 0 && RockStewRecipes.route(root, graph, fifth).steps().size() == 5,
                "Generation plus four operations is admitted");
            require(graph.indexOf(new ItemStack(Items.YELLOW_WOOL)) < 0, "Sixth step is not offered");
            require(graph.indexOf(new ItemStack(Items.IRON_INGOT)) < 0
                && graph.indexOf(new ItemStack(Items.DIAMOND)) < 0, "Ingots and paths through them are pruned");
            require(RockStewRecipes.sources(h.getLevel()).stream().noneMatch(source -> source.id().equals(ingotSource)),
                "Ingot generation is not an allowed source");
            for (int node = 0; node < graph.size(); node++) {
                var row = net.modcrafters.minecraft.industrialoverkill.stew.RockStewTree.row(root.value().recipe(), graph, node);
                require(row.steps().size() <= 5 && row.steps().stream().noneMatch(step -> step.item().is(
                    net.neoforged.neoforge.common.Tags.Items.INGOTS)), "Panel and JEI rows obey the same admission rules");
            }
            var p = RockStewProcessor.from(m);
            require(!p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.YELLOW_WOOL))
                && !p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.IRON_INGOT))
                && !p.select(ingotSource, new ItemStack(Items.GOLD_INGOT)), "Server selection rejects excluded paths");
            require(p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.GLASS_PANE)) && p.route().steps().size() == 5,
                "The five-step cobblestone-to-panes path remains available");
        } finally { manager.replaceRecipes(original); RockStewRecipes.invalidate(); }
        h.succeed();
    }

    private static net.minecraft.world.item.crafting.RecipeHolder<zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry> testGrinding(
        String name, net.minecraft.world.item.Item input, net.minecraft.world.item.Item output) {
        var recipe = new zone.moddev.mc.lib.machine.recipe.MachineRecipe(
            java.util.Map.of("input", List.of(new zone.moddev.mc.lib.machine.ingredient.MachineIngredient.ItemPart(
                net.minecraft.world.item.crafting.Ingredient.of(input), 1))),
            java.util.Map.of("output", List.of(new zone.moddev.mc.lib.machine.recipe.MachineProduct.ItemProduct(new ItemStack(output), 1))), 20);
        return new net.minecraft.world.item.crafting.RecipeHolder<>(RockStewRecipes.id("test/" + name),
            new zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry(RockStewRecipes.GRINDING, recipe));
    }

    @GameTest(template = TEMPLATE)
    public static void oneRunDoesNotConsumePreviousOutputWhenItsBatchIsReady(GameTestHelper h) {
        var m = place(h); var p = RockStewProcessor.from(m);
        m.fluids.drain(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE);
        require(p.select(RockStewRecipes.id("rock_stew/cobbled_deepslate"), new ItemStack(Items.CRACKED_DEEPSLATE_BRICKS)),
            "Choose crafting line ending in an unfueled furnace");
        p.stock(1); p.stock(2);
        m.items.restoreStack(0, new ItemStack(Items.COBBLED_DEEPSLATE, 64));
        m.items.restoreStack(1, new ItemStack(Items.POLISHED_DEEPSLATE, 4));
        m.items.restoreStack(2, new ItemStack(Items.DEEPSLATE_BRICKS, 1));
        require(p.chain().pendingTake(0) == 0 && p.chain().pendingTake(1) == 0,
            "Satisfied One Run stages must not reserve previous outputs for the final stack's future demand");
        tick(m, 250);
        require(m.items.getStackInSlot(0).getCount() == 64 && m.items.getStackInSlot(1).getCount() == 4
            && m.items.getStackInSlot(2).getCount() == 1 && p.chain().cycle(1).plan().isEmpty(),
            "Available previous output does not trigger extra cycles once One Run's batch is ready");
        var saved = m.saveWithoutMetadata(h.getLevel().registryAccess());
        m.loadWithComponents(saved, h.getLevel().registryAccess());
        require(p.chain().pendingTake(0) == 0 && p.chain().pendingTake(1) == 0,
            "One Run's buffer limit is restored along with its stock setting");
        // Model the next processor completing consumption of its reserved input.
        m.items.restoreStack(2, ItemStack.EMPTY);
        tick(m, 250);
        require(m.items.getStackInSlot(0).getCount() == 60 && m.items.getStackInSlot(1).getCount() == 4
            && m.items.getStackInSlot(2).getCount() == 4 && p.chain().cycle(1).plan().isEmpty(),
            "Consumption triggers exactly the needed refill, preserving complete crafting yields");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void blockedIntermediateDoesNotFloodSource(GameTestHelper h) {
        var m = place(h); var p = RockStewProcessor.from(m);
        require(p.select(RockStewRecipes.id("rock_stew/blackstone"), new ItemStack(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS)),
            "Choose a four-stage blackstone path with old deepslate products still stored");
        m.fluids.fill(new FluidStack(Fluids.LAVA, 3000), FluidAction.EXECUTE);
        m.items.restoreStack(0, new ItemStack(Items.BLACKSTONE, 57));
        m.items.restoreStack(1, new ItemStack(Items.POLISHED_DEEPSLATE, 14));
        m.items.restoreStack(2, new ItemStack(Items.DEEPSLATE_BRICKS, 60));
        require(p.stockMode(0) == 0 && p.outputBlocked(1), "None source and incompatible intermediate reproduce the stalled line");
        tick(m, 500);
        require(m.items.getStackInSlot(0).getCount() == 57 && p.chain().cycle(0).plan().isEmpty(),
            "None must not fill the source for a downstream stage that cannot accept its output");
        m.items.restoreStack(0, ItemStack.EMPTY);
        p.stock(0);
        tick(m, 500);
        require(m.items.getStackInSlot(0).getCount() == 4 && p.chain().cycle(0).plan().isEmpty(),
            "One Run keeps only its four-item batch even when later outputs are blocked");
        p.stock(0); // Full Stack starts a refill.
        m.serverTick();
        p.stock(0); // None releases further demand but finishes the admitted cycle.
        tick(m, 500);
        require(m.items.getStackInSlot(0).getCount() == 5 && p.chain().cycle(0).plan().isEmpty(),
            "Switching to None finishes at most the active cycle, then stops");
        m.items.restoreStack(1, ItemStack.EMPTY);
        m.items.restoreStack(2, ItemStack.EMPTY);
        m.fluids.fill(new FluidStack(Fluids.LAVA, 3000), FluidAction.EXECUTE);
        tick(m, 700);
        require(!m.items.getStackInSlot(3).isEmpty(), "Clearing incompatible products restores normal downstream demand");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void oneRunStopsAtItsTargetWhenDownstreamIsSatisfied(GameTestHelper h) {
        var m = place(h); var p = RockStewProcessor.from(m);
        require(p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.COBBLESTONE)), "Choose source-only cobblestone");
        p.stock(0); p.stock(0); // Full Stack -> None -> One Run.
        tick(m, 500);
        require(p.stockMode(0) == 1 && m.items.getStackInSlot(0).getCount() == 1 && p.chain().cycle(0).plan().isEmpty(),
            "One Run produces exactly one cobblestone then idles when no downstream demand exists");
        require(p.select(RockStewRecipes.id("rock_stew/cobbled_deepslate"), new ItemStack(Items.CRACKED_DEEPSLATE_BRICKS)), "Choose reported four-stage path");
        m.items.restoreStack(0, ItemStack.EMPTY);
        m.items.restoreStack(1, new ItemStack(Items.POLISHED_DEEPSLATE, 14));
        m.items.restoreStack(2, new ItemStack(Items.DEEPSLATE_BRICKS, 60));
        m.items.restoreStack(3, new ItemStack(Items.CRACKED_DEEPSLATE_BRICKS, 64));
        p.stock(0);
        tick(m, 500);
        require(p.stockMode(0) == 1 && m.items.getStackInSlot(0).getCount() == 4 && p.chain().cycle(0).plan().isEmpty(),
            "One Run stops at the four cobbled deepslate needed by the next crafting batch");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void oneRunStocksDownstreamBatchAndSurvivesReload(GameTestHelper h) {
        var m = place(h); var p = panePath(m);
        p.stock(3);
        require(p.stockMode(3) == 1 && p.chain().target(3) == 6, "One Run keeps six glass for the next pane crafting cycle");
        p.stock(4); p.stock(4);
        require(p.stockMode(4) == 1 && p.chain().target(4) == 16, "Final One Run keeps one output batch");
        var saved = m.saveWithoutMetadata(h.getLevel().registryAccess());
        m.loadWithComponents(saved, h.getLevel().registryAccess());
        require(p.stockMode(3) == 1 && p.chain().target(3) == 6 && p.chain().target(4) == 16, "Batch settings survive reload");
        p.stock(3);
        require(p.stockMode(3) == 2 && p.chain().target(3) == 64, "One Run switches to Full Stack");
        p.stock(3);
        require(p.stockMode(3) == 0 && p.chain().target(3) == 0, "Full Stack switches to None");
        // Old saves used a numeric One target without an explicit mode.
        saved.getCompound("features").getCompound("rock_stew").getCompound("chain_data").remove("stock_modes");
        var stages = saved.getCompound("features").getCompound("rock_stew").getCompound("chain_data")
            .getCompound("state").getList("stages", Tag.TAG_COMPOUND);
        stages.getCompound(3).putInt("target", 1);
        m.loadWithComponents(saved, h.getLevel().registryAccess());
        require(p.stockMode(3) == 1 && p.chain().target(3) == 6, "Old One migrates to one downstream batch");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void stockClickRecoversFailedSavedWork(GameTestHelper h) {
        var m = place(h); var p = panePath(m);
        m.items.restoreStack(3, new ItemStack(Items.GLASS, 8));
        tick(m, 5);
        require(p.chain().cycle(4).plan().isPresent(), "Pane crafting is active before modeling invalid saved work");
        // Model a saved active plan whose claimed input is no longer present.
        m.items.restoreStack(3, new ItemStack(Items.GLASS, 2));
        var saved = m.saveWithoutMetadata(h.getLevel().registryAccess());
        long paid = m.energy.stored();
        m.loadWithComponents(saved, h.getLevel().registryAccess());
        require(p.problem().equals("stew.recovery"), "Invalid saved work reproduces the reported recovery lock");
        var player = h.makeMockServerPlayerInLevel();
        var menu = new MachineMenu(89, player.getInventory(), m);
        try {
            var stock = (RockStewScreen.Control) menu.bound.screen().find("stage_stock_0").orElseThrow();
            stock.interact(new ScreenClick(player, menu, stock, 0, false, false, false, 0));
            require(p.problem().isEmpty() && p.stockMode(0) == 1 && p.chain().target(0) == 1,
                "The actual stock-button action recovers and switches to One Run");
            require(m.items.getStackInSlot(3).getCount() == 2 && m.energy.stored() == paid
                && m.fluids.getFluidInTank(1).getAmount() == 1000, "Restart preserves contents and does not refund energy");
            require(p.state().getCompound("recovery_data").equals(saved.getCompound("features").getCompound("rock_stew").getCompound("chain_data")),
                "Original failed work is retained in a separate recovery snapshot");
            m.serverTick();
            require(p.chain().cycle(0).plan().isPresent(), "Source can run after recovery");
            var recovered = m.saveWithoutMetadata(h.getLevel().registryAccess());
            m.loadWithComponents(recovered, h.getLevel().registryAccess());
            require(p.problem().isEmpty() && p.stockMode(0) == 1, "Recovered machine reloads without locking again");
        } finally { menu.removed(player); }
        m.loadWithComponents(saved, h.getLevel().registryAccess());
        require(p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.STONE)) && p.problem().isEmpty(),
            "Choosing a valid path also cancels unrecoverable work");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void stockedDeepslateLineKeepsProcessing(GameTestHelper h) {
        var m = place(h); var p = RockStewProcessor.from(m);
        require(p.select(RockStewRecipes.id("rock_stew/cobbled_deepslate"), new ItemStack(Items.CRACKED_DEEPSLATE_BRICKS)),
            "Screenshot's deepslate crafting/smelting line is admitted");
        require(p.chain().size() == 4, "Source, polish, bricks, furnace");
        m.fluids.fill(new FluidStack(Fluids.LAVA, 2000), FluidAction.EXECUTE);
        require(p.chain().target(0) == 0, "Intermediate stock defaults remain None");
        m.items.restoreStack(1, new ItemStack(Items.POLISHED_DEEPSLATE, 14));
        m.items.restoreStack(2, new ItemStack(Items.DEEPSLATE_BRICKS, 60));
        m.serverTick();
        require(p.chain().cycle(3).plan().isPresent(), "Furnace starts from existing intermediates");
        tick(m, 100);
        require(m.items.getStackInSlot(3).is(Items.CRACKED_DEEPSLATE_BRICKS), "Existing intermediate products are processed");
        require(m.items.getStackInSlot(0).isEmpty(), "None avoids generating unneeded stock when intermediates cover demand");
        p.stock(0);
        require(p.chain().target(0) == p.chain().step(1).inputCount(), "None switches to one downstream input batch");
        tick(m, 41);
        require(m.items.getStackInSlot(0).is(Items.COBBLED_DEEPSLATE), "Source produces its selected stone when stock is requested");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void twoTanksAndConcurrentMaterialPath(GameTestHelper h) {
        var m = place(h); var p = panePath(m);
        m.fluids.fill(new FluidStack(Fluids.LAVA, 2400), FluidAction.EXECUTE);
        require(m.fluids.getTanks() == 2 && m.fluids.getTankCapacity(0) == 4000, "Exactly two four-bucket tanks");
        boolean concurrent = false;
        for (int i = 0; i < 4000; i++) {
            m.serverTick();
            int active = 0;
            for (int j = 0; j < p.chain().size(); j++) if (p.chain().cycle(j).plan().isPresent()) active++;
            concurrent |= active > 1;
        }
        require(concurrent && m.items.getStackInSlot(4).getCount() == 64, "Concurrent full crafting batches");
        require(m.energy.stored() == 100000 - 24 * 1760 - 4 * 160, "All cycles pay exactly once");
        require(m.fluids.getFluidInTank(0).getAmount() == 1000 && m.fluids.getFluidInTank(1).getAmount() == 1000, "Reusable catalysts retained");
        for (int i = 0; i < 4; i++) require(m.items.getStackInSlot(i).isEmpty(), "No excess intermediate orders");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void generationChecksThresholdBeforeConsumption(GameTestHelper h) {
        var m = place(h); var p = RockStewProcessor.from(m);
        require(p.select(RockStewRecipes.id("rock_stew/stone"), new ItemStack(Items.STONE)), "Select stone generation");
        m.fluids.drain(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE);
        tick(m, 45);
        require(p.chain().cycle(0).plan().isEmpty() && m.items.getStackInSlot(0).isEmpty(), "999 ml water cannot start generation");
        m.fluids.drain(new FluidStack(Fluids.LAVA, 1), FluidAction.EXECUTE);
        m.fluids.fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE);
        tick(m, 45);
        require(p.chain().cycle(0).plan().isEmpty(), "999 ml lava cannot start generation either");
        m.fluids.fill(new FluidStack(Fluids.LAVA, 1), FluidAction.EXECUTE);
        tick(m, 90);
        require(m.items.getStackInSlot(0).getCount() == 1 && m.fluids.getFluidInTank(1).getAmount() == 900
            && p.chain().cycle(0).plan().isEmpty(), "Exactly 1000 ml starts one stone cycle, then 900 ml prevents restart");
        m.fluids.fill(new FluidStack(Fluids.LAVA, 100), FluidAction.EXECUTE);
        tick(m, 90);
        require(m.items.getStackInSlot(0).getCount() == 2, "Refilling to the threshold allows one more cycle");
        m.items.extractItem(0, 64, false);
        m.fluids.fill(new FluidStack(Fluids.LAVA, 100), FluidAction.EXECUTE);
        require(p.select(RockStewRecipes.id("rock_stew/obsidian"), new ItemStack(Items.OBSIDIAN)), "Select obsidian generation");
        tick(m, 450);
        require(m.items.getStackInSlot(0).is(Items.OBSIDIAN) && m.items.getStackInSlot(0).getCount() == 1
            && m.fluids.getFluidInTank(1).isEmpty() && p.chain().cycle(0).plan().isEmpty(),
            "Obsidian consumes the starting bucket without requiring an extra retained bucket");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void furnaceUsesRemainingLavaWithoutSourceThreshold(GameTestHelper h) {
        var m = place(h); var p = RockStewProcessor.from(m);
        require(p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.GLASS_PANE)), "Select furnace path before any work starts");
        p.chain().target(0, 0);
        p.stock(3); p.stock(3); // Independent Full Stack glass target, since crafting is blocked.
        m.items.restoreStack(2, new ItemStack(Items.SAND, 64));
        m.items.restoreStack(4, new ItemStack(Items.DIRT)); // Block crafting so glass stays measurable.
        m.fluids.drain(new FluidStack(Fluids.LAVA, 900), FluidAction.EXECUTE);
        m.fluids.drain(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        m.serverTick();
        require(p.chain().cycle(3).plan().isPresent() && p.chain().cycle(0).plan().isEmpty(),
            "A furnace starts with only 100 ml lava and no water while generation is stopped");
        tick(m, 40);
        long paid = m.energy.stored();
        require(p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.COBBLESTONE)), "Changing recipe cancels active furnace");
        require(m.fluids.getFluidInTank(1).getAmount() == 100 && m.items.getStackInSlot(2).getCount() == 64
            && m.energy.stored() == paid, "Cancellation releases fuel and input reservations without refunding spent energy");
        require(p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.GLASS_PANE)), "Restore furnace path");
        p.chain().target(0, 0);
        p.stock(3); p.stock(3); // Independent Full Stack glass target, since crafting is blocked.
        m.serverTick(); tick(m, 40);
        var saved = m.saveWithoutMetadata(h.getLevel().registryAccess());
        var oldFuel = saved.getCompound("features").getCompound("rock_stew").getCompound("chain_data")
            .getCompound("state").getList("stages", Tag.TAG_COMPOUND).getCompound(3)
            .getCompound("cycle").getCompound("cycle").getList("fluids", Tag.TAG_COMPOUND).getCompound(0);
        oldFuel.getCompound("fluid").putInt("amount", 1100);
        oldFuel.putInt("retained", 1000);
        m.loadWithComponents(saved, h.getLevel().registryAccess());
        require(p.problem().isEmpty(), "Old retained claims migrate without losing paid work");
        tick(m, 60);
        require(m.items.getStackInSlot(3).is(Items.GLASS) && m.items.getStackInSlot(3).getCount() == 1
            && m.items.getStackInSlot(2).getCount() == 63 && m.fluids.getFluidInTank(1).isEmpty(),
            "A resumed furnace consumes the last 100 ml exactly once");
        tick(m, 110);
        require(m.items.getStackInSlot(3).getCount() == 1, "Another cycle waits for fuel refill");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void blockedOutputsKeepSelectedRecipeVisible(GameTestHelper h) {
        var m = place(h); var p = RockStewProcessor.from(m);
        m.serverTick();
        require(p.route() == null, "Fresh machine waits for a chosen path");
        var emptyPlayer = h.makeMockServerPlayerInLevel();
        var emptyMenu = new MachineMenu(86, emptyPlayer.getInventory(), m);
        var emptyDisplay = (RockStewScreen.Display) emptyMenu.bound.screen().find("stage_processor_0").orElseThrow();
        require(emptyDisplay.tooltip(m, ItemStack.EMPTY).equals(List.of(net.minecraft.network.chat.Component.translatable("stew.open_tree"))),
            "Unselected processor only prompts to choose a production path");
        emptyMenu.removed(emptyPlayer);
        m.items.restoreStack(0, new ItemStack(Items.DIRT, 64));
        require(p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.GLASS_PANE)), "Selection accepted with unrelated stock");
        m.serverTick();
        require(p.chain() != null && p.displayRoute().destination().is(Items.GLASS_PANE) && p.displaySize() == 5,
            "Selected recipe binds even with incompatible output contents");
        var player = h.makeMockServerPlayerInLevel();
        var menu = new MachineMenu(85, player.getInventory(), m); player.containerMenu = menu;
        try {
            var furnace = (RockStewScreen.Display) menu.bound.screen().find("stage_processor_3").orElseThrow();
            require(furnace.snapshot(m).orElseThrow().value() instanceof ItemStack icon && icon.is(Items.FURNACE),
                "Blocked recipe retains its processor sprite");
            require(!furnace.tooltip(m, ItemStack.EMPTY).contains(net.minecraft.network.chat.Component.translatable("stew.output_blocked"))
                && p.outputBlocked(0), "Only the occupied processor output is blocked");
            var tooltip = furnace.tooltip(m, ItemStack.EMPTY);
            require(tooltip.size() == 3 && tooltip.getFirst().getString().equals(new ItemStack(Items.GLASS).getHoverName().getString())
                && tooltip.getFirst().getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.AQUA)),
                "Selected product is first and light blue, without instructions or tick/operation details");
            require(tooltip.get(1).equals(net.minecraft.network.chat.Component.translatable("stew.lava_cost", 100L)
                .withStyle(net.minecraft.ChatFormatting.GOLD)), "Furnace tooltip includes its fuel cost");
            require(tooltip.getLast().equals(net.minecraft.network.chat.Component.translatable("stew.cost", "8", 800L)
                .withStyle(net.minecraft.ChatFormatting.RED)), "Energy tooltip shows actual FE per tick and per cycle in light red");
            require(m.items.getStackInSlot(0).is(Items.DIRT), "Preview does not overwrite old output");
            var saved = m.saveWithoutMetadata(h.getLevel().registryAccess());
            m.loadWithComponents(saved, h.getLevel().registryAccess());
            require(p.displayRoute().destination().is(Items.GLASS_PANE) && p.outputBlocked(0) && !p.outputBlocked(3), "Local blocked status survives reload");
            m.items.extractItem(0, 64, false);
            m.items.restoreStack(1, new ItemStack(Items.DIRT, 64));
            p.stock(0); // Its independent One Run target can run despite downstream blockage.
            m.serverTick();
            require(p.chain().cycle(0).plan().isPresent() && !p.outputBlocked(0) && p.outputBlocked(1),
                "Clear first output can run while another processor output is blocked");
            require(p.chain() != null && !p.switching() && !p.outputBlocked(3), "Clearing outputs activates the visible recipe");
            m.items.restoreStack(4, new ItemStack(Items.DIRT));
            require(p.outputBlocked(4) && !p.outputBlocked(3), "Incompatible stock blocks only its current processor");
            m.items.restoreStack(4, new ItemStack(Items.GLASS_PANE, 49));
            require(p.outputBlocked(4), "A complete output batch must fit");
            m.items.restoreStack(4, new ItemStack(Items.GLASS_PANE, 48));
            require(!p.outputBlocked(4), "Compatible stock with enough batch room is not blocked");
        } finally { menu.removed(player); }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void claimsSurviveLoadBeforeFirstTick(GameTestHelper h) {
        var m = place(h); var p = panePath(m);
        m.items.restoreStack(3, new ItemStack(Items.GLASS, 3));
        require(m.items.extractItem(3, 64, true).isEmpty(), "Partial batch protected before tick");
        var saved = m.saveWithoutMetadata(h.getLevel().registryAccess());
        m.loadWithComponents(saved, h.getLevel().registryAccess());
        require(m.items.extractItem(3, 64, true).isEmpty(), "Pending claims restored before first tick");
        m.items.restoreStack(3, new ItemStack(Items.GLASS, 8)); m.serverTick();
        require(m.items.plan(3).take() == 6 && m.items.extractItem(3, 64, true).getCount() == 2, "Batch locks six, surplus is free");
        saved = m.saveWithoutMetadata(h.getLevel().registryAccess());
        m.loadWithComponents(saved, h.getLevel().registryAccess());
        require(m.items.plan(3).take() == 6, "Active claims restored exactly once");
        p.setPaused(true); int progress = p.chain().cycle(4).progress(); tick(m, 5);
        require(m.items.extractItem(3, 64, true).getCount() == 2 && p.chain().cycle(4).progress() == progress, "Pause preserves work and claims");
        p.setPaused(false); tick(m, 20);
        require(m.items.getStackInSlot(4).getCount() == 16, "Crafting resumes after reload");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void switchesCancelWorkAndPreserveStock(GameTestHelper h) {
        var m = place(h); var p = panePath(m);
        m.items.restoreStack(3, new ItemStack(Items.GLASS, 8)); tick(m, 7);
        var oldChain = p.chain();
        require(oldChain.cycle(4).plan().isPresent() && m.items.plan(3).take() == 6, "Crafting has active input claims");
        long energy = m.energy.stored();
        var deepslate = RockStewRecipes.id("rock_stew/cobbled_deepslate");
        require(p.select(deepslate, new ItemStack(Items.COBBLED_DEEPSLATE)), "New recipe applies immediately");
        require(p.route().source().equals(deepslate) && !p.switching() && p.chain().cycle(0).progress() == 0,
            "New path replaces old work immediately");
        for (int i = 0; i < oldChain.size(); i++) require(oldChain.cycle(i).plan().isEmpty() && oldChain.cycle(i).progress() == 0,
            "Canceled cycles release plans and progress");
        require(m.items.plan(3).take() == 0 && m.items.extractItem(3, 64, true).getCount() == 8,
            "Cancellation releases input claims without consuming stored ingredients");
        require(m.energy.stored() == energy && m.items.getStackInSlot(4).isEmpty(), "Cancellation neither refunds spent FE nor produces unfinished output");
        require(p.displaySize() == 5 && !p.outputBlocked(0), "Leftovers stay accessible without blocking the clear first output");
        tick(m, 41);
        require(m.items.getStackInSlot(0).is(Items.COBBLED_DEEPSLATE) && m.items.getStackInSlot(3).getCount() == 8,
            "New source runs while unrelated old products remain");
        p.setPaused(true);
        var saved = m.saveWithoutMetadata(h.getLevel().registryAccess());
        m.loadWithComponents(saved, h.getLevel().registryAccess());
        require(p.route().source().equals(deepslate) && m.items.plan(3).take() == 0,
            "Reload retains the new route without resurrecting canceled claims");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void repeatedSelectionAndReloadRespectCancellation(GameTestHelper h) {
        var m = place(h); var p = panePath(m); tick(m, 7);
        int progress = p.chain().cycle(0).progress();
        p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.GLASS_PANE));
        require(p.chain().cycle(0).progress() == progress, "Selecting the same recipe leaves current work intact");
        p.select(RockStewRecipes.id("rock_stew/stone"), new ItemStack(Items.STONE));
        p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.GLASS));
        require(p.chain().cycle(0).progress() == 0 && p.route().destination().is(Items.GLASS), "Changing selection discards old progress");
        require(p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.GLASS_PANE)) && !p.switching(), "Latest recipe is applied immediately");
        var recipes = List.copyOf(h.getLevel().getRecipeManager().getRecipes());
        try {
            h.getLevel().getRecipeManager().replaceRecipes(recipes.stream().filter(r -> !r.id().equals(RockStewRecipes.DEFAULT)).toList());
            RockStewRecipes.invalidate(); tick(m, 45);
            require(p.chain().drained() && m.items.getStackInSlot(0).isEmpty(), "Removed source cannot finish canceled work or restart");
        } finally { h.getLevel().getRecipeManager().replaceRecipes(recipes); RockStewRecipes.invalidate(); }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void legacyCycleAndReagentArePreserved(GameTestHelper h) {
        var m = place(h); var p = RockStewProcessor.from(m);
        p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.COBBLESTONE)); tick(m, 12);
        var cycle = p.chain().cycle(0); var registry = h.getLevel().registryAccess();
        var old = new RecipeProcessor.State(false, cycle.progress(), Optional.empty(), 0, cycle.plan(), Optional.empty());
        var saved = m.saveWithoutMetadata(registry);
        saved.getCompound("features").put("rock_stew", RecipeProcessor.State.CODEC.encodeStart(registry.createSerializationContext(NbtOps.INSTANCE), old).getOrThrow());
        var reagent = new CompoundTag(); reagent.put("Fluid", new FluidStack(Fluids.LAVA, 1000).save(registry)); reagent.putString("TankId", "reagent");
        saved.getCompound("fluids").getList("Tanks", Tag.TAG_COMPOUND).add(reagent);
        long energy = m.energy.stored(); m.loadWithComponents(saved, registry);
        require(m.fluids.getTanks() == 2 && m.fluids.getFluidInTank(1).getAmount() == 2000, "Old reagent retained in two tanks");
        tick(m, 40 - cycle.progress());
        require(m.items.getStackInSlot(0).is(Items.COBBLESTONE) && m.energy.stored() == energy - (40 - cycle.progress()) * 8L, "Legacy work finishes without recharging paid ticks");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void recipeRowsPreserveFluidAndOperationSemantics(GameTestHelper h) {
        var m = place(h);
        var sources = RockStewRecipes.sources(h.getLevel());
        var stone = sources.stream().filter(source -> source.id().equals(RockStewRecipes.id("rock_stew/stone"))).findFirst().orElseThrow();
        var stoneGraph = RockStewRecipes.graph(h.getLevel(), stone);
        var row = net.modcrafters.minecraft.industrialoverkill.stew.RockStewTree.row(
            stone.value().recipe(), stoneGraph, stoneGraph.indexOf(new ItemStack(Items.STONE_BRICKS)));
        require(row.water().retained() == 1000 && row.water().consumed() == 0,
            "Water is required without consumption");
        require(row.lava().retained() == 1000 && row.lava().consumed() == 100 && row.lava().total() == 1000,
            "The starting threshold includes the consumed lava");
        require(row.steps().getFirst().operation().equals("generate") && row.steps().getLast().operation().equals("craft"),
            "Shades follow operations, not item identities");
        var cobble = sources.stream().filter(source -> source.id().equals(RockStewRecipes.DEFAULT)).findFirst().orElseThrow();
        var graph = RockStewRecipes.graph(h.getLevel(), cobble);
        var glass = net.modcrafters.minecraft.industrialoverkill.stew.RockStewTree.row(
            cobble.value().recipe(), graph, graph.indexOf(new ItemStack(Items.GLASS)));
        require(glass.steps().getLast().lavaConsumed() == 100 && glass.lava().consumed() == 0,
            "Furnace fuel is separate from source-generation cost");
        require(glass.steps().stream().map(step -> step.operation()).toList().equals(List.of("generate", "grind", "grind", "smelt")),
            "Full route keeps generation, both grinds and smelting");
        h.succeed();
    }

    private static zone.moddev.mc.lib.machine.recipe.RecipeGraph sourceGraph(GameTestHelper h, ResourceLocation source) {
        var root = RockStewRecipes.sources(h.getLevel()).stream()
            .filter(recipe -> recipe.id().equals(source)).findFirst().orElseThrow();
        return RockStewRecipes.graph(h.getLevel(), root);
    }

    @GameTest(template = TEMPLATE)
    public static void treeKeepsParentsAndDescendantsTogether(GameTestHelper h) {
        var m = place(h); panePath(m);
        var graph = sourceGraph(h, RockStewRecipes.DEFAULT);
        var order = net.modcrafters.minecraft.industrialoverkill.stew.RockStewTree.order(graph);
        require(order.size() == graph.size() && order.stream().distinct().count() == graph.size(), "Every destination occurs once");
        for (int node = 1; node < graph.size(); node++) {
            var path = graph.pathTo(node);
            int parent = path.size() == 1 ? 0 : graph.indexOf(path.get(path.size() - 2).output());
            require(order.indexOf(parent) < order.indexOf(node), "Parent precedes child");
            var root = graph.item(node);
            boolean leftSubtree = false;
            for (int i = order.indexOf(node) + 1; i < order.size(); i++) {
                boolean descendant = graph.pathTo(order.get(i)).stream()
                    .anyMatch(step -> ItemStack.isSameItemSameComponents(root, step.output()));
                require(!leftSubtree || !descendant, "Subtree stays contiguous");
                leftSubtree |= !descendant;
            }
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void screenShowsWholeProcessingLine(GameTestHelper h) {
        var m = place(h); var p = panePath(m); var player = h.makeMockServerPlayerInLevel();
        var menu = new MachineMenu(84, player.getInventory(), m); player.containerMenu = menu;
        try {
            require(menu.bound.screen().find("tank_2").isEmpty(), "No third fluid gauge");
            require(menu.bound.screen().find("recipe_book").isEmpty()
                && menu.bound.screen().find("previous_stage").isEmpty()
                && menu.bound.screen().find("next_stage").isEmpty(), "No redundant navigation buttons");
            var display = menu.bound.screen().find("stage_processor_0").orElseThrow();
            require(display instanceof RockStewScreen.Display && display.actions().contains(RockStewScreen.SELECT), "Processor sprite accepts recipe selection");
            var first = (zone.moddev.mc.lib.gui.model.elements.SlotElement) menu.bound.screen().find("stage_item_0").orElseThrow();
            var sixth = (zone.moddev.mc.lib.gui.model.elements.SlotElement) menu.bound.screen().find("stage_item_5").orElseThrow();
            var line = (RockStewScreen.Line) menu.bound.screen().find("processing_line").orElseThrow();
            menu.elementStates().sample(m, player);
            require(menu.elementStates().get(line, RockStewScreen.Line.COUNT) == p.chain().size(), "Line follows selected route length");
            require(line.actions().isEmpty(), "Processing line has no paging action");
            require(line.children().size() == RockStewProcessor.SLOTS, "One XML element generates all stable stage columns");
            for (int i = 0; i < RockStewProcessor.SLOTS; i++) {
                var output = (zone.moddev.mc.lib.gui.model.elements.SlotElement) menu.bound.screen().find("stage_item_" + i).orElseThrow();
                require(menu.slot(output).isActive() == (i < p.chain().size()), "All selected outputs and only selected outputs are visible");
            }
            int index = menu.slot(sixth).index;
            // Model a longer synchronized route: column six must extend the same line, never replace column one.
            menu.elementStates().set(line, RockStewScreen.Line.COUNT, 6);
            var layout = new zone.moddev.mc.lib.gui.layout.LayoutContext(
                zone.moddev.mc.lib.gui.layout.TextMeasure.NONE, menu.elementStates());
            var size = line.measure(layout);
            var positions = line.childLayout(new zone.moddev.mc.lib.gui.layout.Rect(0, 0, size[0], size[1]), layout);
            require(menu.slot(first).isActive() && menu.slot(sixth).isActive() && menu.slot(sixth).index == index,
                "Longer lines keep first and later outputs visible with stable native indices");
            for (int i = 1; i < 6; i++) require(positions.get(line.children().get(i)).x()
                > positions.get(line.children().get(i - 1)).x(), "Every column extends the straight line");
            require(size[0] >= positions.get(line.children().get(5)).right(), "Line includes its final column");
            menu.elementStates().sample(m, player);
            require(menu.slot(first).isActive() && !menu.slot(sixth).isActive(), "Five baseline columns remain without paging");
            var stock = (RockStewScreen.Control) menu.bound.screen().find("stage_stock_0").orElseThrow();
            var tooltip = stock.tooltip(m, ItemStack.EMPTY);
            require(tooltip.size() == 2 && tooltip.getFirst().equals(net.minecraft.network.chat.Component.translatable("stew.stock.label.none"))
                && tooltip.get(1).getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.GRAY)),
                "None has only its title and grey explanation");
            p.stock(0);
            tooltip = stock.tooltip(m, ItemStack.EMPTY);
            require(tooltip.size() == 2 && tooltip.getFirst().equals(net.minecraft.network.chat.Component.translatable("stew.stock.label.one")),
                "One Run has a concise two-line tooltip");
            p.stock(0);
            require(stock.tooltip(m, ItemStack.EMPTY).equals(List.of(net.minecraft.network.chat.Component.translatable("stew.stock.label.stack"))),
                "Full Stack is a single-line tooltip");
            p.select(RockStewRecipes.DEFAULT, new ItemStack(Items.COBBLESTONE));
            menu.elementStates().sample(m, player);
            require(menu.elementStates().get(line, RockStewScreen.Line.COUNT) == 5, "Short recipes keep five columns visible");
            for (int i = 1; i < 5; i++) {
                var unused = (RockStewScreen.Control) menu.bound.screen().find("stage_stock_" + i).orElseThrow();
                var glyph = (RockStewScreen.Display) menu.bound.screen().find("stage_processor_" + i).orElseThrow();
                require(menu.visible(unused) && !unused.enabled(menu.elementStates()) && menu.visible(glyph),
                    "Unused processors remain visible with disabled stock controls");
                require(glyph.snapshot(m).orElseThrow().value() instanceof ItemStack item && item.isEmpty(),
                    "Unused processor presents a disabled placeholder");
            }
        } finally { menu.removed(player); }
        h.succeed();
    }
}
