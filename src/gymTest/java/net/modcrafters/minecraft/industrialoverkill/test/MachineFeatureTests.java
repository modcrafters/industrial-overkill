package net.modcrafters.minecraft.industrialoverkill.test;

import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.require;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import net.modcrafters.minecraft.industrialoverkill.waste.FarmSupplies;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.drawer.BuiltinDrawers;
import zone.moddev.mc.lib.gui.drawer.DrawerRegistry;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

@GameTestHolder("industrial_overkill_agri")
@PrefixGameTestTemplate(false)
public final class MachineFeatureTests {
    @GameTest(template = "gym_empty")
    public static void inheritedPoweredFeatures(GameTestHelper h) {
        var filter = place(h, "sewage_filter", new BlockPos(2, 2, 2));
        var screen = Screens.get(filter.definition.id()).orElseThrow().screen();
        var drawers = DrawerRegistry.GLOBAL.resolve(
            new DrawerRegistry.Context(screen, TextMeasure.NONE, filter));
        require(drawers.stream().anyMatch(drawer -> drawer.type().equals(BuiltinDrawers.REDSTONE)
                && drawer.placement().order() == 20),
            "Sewage Filter inherits Industrial Overkill's Redstone drawer placement");
        require(drawers.stream().anyMatch(drawer -> drawer.type().equals(BuiltinDrawers.UPGRADES))
                && filter.definition.behavior().gearboxSlot() == null
                && filter.definition.behavior().capacitorSlot() != null,
            "The gearbox override leaves the capacitor and Upgrades drawer available");

        var control = filter.screenRedstone();
        for (var face : RelativeFace.values())
            require(control.mode(face.ordinal()) == RedstoneControl.Mode.READ,
                "The inherited redstone default applies to every face");
        h.getLevel().setBlockAndUpdate(filter.getBlockPos().above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        control.readInputs();
        require(control.paused(), "Power on the top face pauses processing");
        control.mode(RelativeFace.TOP.ordinal(), RedstoneControl.Mode.IGNORE);
        var registries = h.getLevel().registryAccess();
        control.load(control.save(registries), registries);
        control.readInputs();
        require(!control.paused(), "The saved per-face setting takes precedence over the inherited default");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void portableBaseRemovesStandardDrawers(GameTestHelper h) {
        var courier = place(h, "critter_courier", new BlockPos(2, 2, 2));
        var screen = Screens.get(courier.definition.id()).orElseThrow().screen();
        var drawers = DrawerRegistry.GLOBAL.resolve(
            new DrawerRegistry.Context(screen, TextMeasure.NONE, courier));
        require(drawers.isEmpty() && courier.screenRedstone() == null,
            "Critter Courier inherits a drawer-free screen from the portable base");
        h.succeed();
    }

    private static DefinedMachineEntity place(GameTestHelper h, String id, BlockPos relative) {
        var pos = h.absolutePos(relative);
        h.getLevel().setBlockAndUpdate(pos,
            BuiltInRegistries.BLOCK.get(FarmSupplies.id(id)).defaultBlockState());
        return (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
    }
}
