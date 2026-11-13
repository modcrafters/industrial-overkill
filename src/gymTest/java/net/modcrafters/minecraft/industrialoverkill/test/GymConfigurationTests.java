package net.modcrafters.minecraft.industrialoverkill.test;

import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.lamps;
import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.place;
import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.require;

import java.util.UUID;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierData;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.gui.runtime.AutomationNetwork;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.RedstoneMenu;
import zone.moddev.mc.lib.gui.runtime.RedstoneNetwork;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;
import zone.moddev.mc.lib.machine.multiblock.MultiblockInstance;

@GameTestHolder(IndustrialOverkill.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GymConfigurationTests {
    @GameTest(template = "gym_empty")
    public static void activeEnergyExportRespectsSideSettingsDuringRedstonePause(GameTestHelper h) {
        var gym = place(h, Direction.NORTH);
        var pos = gym.getBlockPos().north();
        h.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        var target = h.getLevel().getBlockEntity(pos);
        gym.energy.setStored(1000);
        gym.energySides.set(RelativeFace.FRONT, IoMode.DISABLED);
        gym.serverTick();
        require(target.getPersistentData().getInt("gym_test_received") == 0
            && gym.energy.stored() == 1000, "Disabled blocks active FE push");
        gym.energySides.set(RelativeFace.FRONT, IoMode.OUTPUT);
        gym.serverTick();
        require(target.getPersistentData().getInt("gym_test_received") == 128
            && gym.energy.stored() == 872, "Output permits active FE push");
        int back = RelativeFace.BACK.ordinal();
        gym.screenRedstone().mode(back, RedstoneControl.Mode.READ);
        gym.screenRedstone().range(back, 0, 0);
        gym.serverTick();
        require(
            gym.screenRedstone().paused()
                && target.getPersistentData().getInt("gym_test_received") == 256,
            "Processing-only pause allows configured FE draining");
        gym.energySides.set(RelativeFace.FRONT, IoMode.DISABLED);
        gym.serverTick();
        require(target.getPersistentData().getInt("gym_test_received") == 256,
            "Paused push still obeys disabled side");
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void sidePoliciesRotatePersistAndProtectCaptive(GameTestHelper h) {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var gym = place(h, facing);
            var level = h.getLevel();
            var pos = gym.getBlockPos();
            var right = RelativeFace.RIGHT.toWorld(facing);
            require(level.getCapability(Capabilities.ItemHandler.BLOCK, pos, right) == null,
                "Belt side has no item port");
            require(level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, right) == null,
                "Belt side has no energy port");
            require(level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP) == null
                && level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, Direction.UP) == null,
                "Food display top has no item or energy port");
            require(!gym.screenRedstone().faceAvailable(RelativeFace.TOP.ordinal()),
                "Top redstone is unavailable");
            require(level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) == null,
                "No unsided bypass");
            var food = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, facing);
            var power = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, facing);
            require(food != null && power != null && food.getSlots() == 4,
                "Available side keeps fixed slot indexes");
            require(food.insertItem(0, new ItemStack(Items.WHEAT), false).isEmpty(),
                "Default food insertion");
            require(food.extractItem(0, 1, false).isEmpty(), "Food automation is input only");
            gym.foodSides.set(RelativeFace.FRONT, IoMode.BOTH);
            require(food.extractItem(0, 1, false).isEmpty(), "Legacy both mode cannot bypass input policy");
            var legacy = gym.saveWithFullMetadata(level.registryAccess());
            gym.loadWithComponents(legacy, level.registryAccess());
            require(gym.foodSides.get(RelativeFace.FRONT) == IoMode.INPUT,
                "Saved both mode migrates to input");
            require(gym.food.extractItem(0, 1, true).is(Items.WHEAT),
                "Manual food removal remains available");
            gym.energy.setStored(1000);
            require(power.extractEnergy(20, true) == 20 && power.receiveEnergy(20, false) == 0,
                "FE output only");
            var pet = EntityType.WOLF.create(level);
            pet.setTame(true, true);
            pet.setOwnerUUID(UUID.randomUUID());
            var trap = CritterCourierData.stack(CritterCourierData.save(pet));
            gym.food.insertItem(3, trap, false);
            require(food.extractItem(3, 1, false).isEmpty()
                && !food.insertItem(3, trap, true).isEmpty(), "Trap is manual only");
            gym.foodSides.set(RelativeFace.FRONT, IoMode.DISABLED);
            gym.energySides.set(RelativeFace.FRONT, IoMode.DISABLED);
            require(
                food.extractItem(0, 1, false).isEmpty()
                    && !food.insertItem(0, new ItemStack(Items.WHEAT), true).isEmpty(),
                "Retained item handler sees mode change");
            require(power.extractEnergy(20, false) == 0, "Retained FE handler sees mode change");
            var saved = gym.saveWithFullMetadata(level.registryAccess());
            gym.foodSides.set(RelativeFace.FRONT, IoMode.BOTH);
            gym.loadWithComponents(saved, level.registryAccess());
            require(
                gym.foodSides.get(RelativeFace.FRONT) == IoMode.DISABLED
                    && gym.energySides.get(RelativeFace.FRONT) == IoMode.DISABLED,
                "Side settings survive reload");
            gym.foodSides.set(RelativeFace.TOP, IoMode.BOTH);
            gym.screenRedstone().mode(RelativeFace.TOP.ordinal(), RedstoneControl.Mode.READ);
            require(gym.foodSides.get(RelativeFace.TOP) == IoMode.DISABLED
                && gym.screenRedstone()
                    .mode(RelativeFace.TOP.ordinal()) == RedstoneControl.Mode.IGNORE,
                "Top restriction survives reload and rejects edits");
            gym.foodSides.set(RelativeFace.RIGHT, IoMode.BOTH);
            require(
                !gym.foodSides.faceAvailable(RelativeFace.RIGHT)
                    && gym.foodSides.get(RelativeFace.RIGHT) == IoMode.DISABLED,
                "Policy survives save/load and direct edits");
            require(CritterCourierData.read(gym.food.getStackInSlot(3)).equals(CritterCourierData.read(trap)),
                "Captive data preserved");
            gym.multiblock().dismantle(MultiblockInstance.Cause.CREATIVE);
        }
        h.succeed();
    }

    @GameTest(template = "gym_empty")
    public static void redstonePausesWorkWithoutLosingPetAndRejectsBlockedEdits(GameTestHelper h) {
        var gym = place(h, Direction.NORTH);
        var level = h.getLevel();
        var pet = EntityType.CAT.create(level);
        pet.setTame(true, true);
        var owner = UUID.randomUUID();
        pet.setOwnerUUID(owner);
        pet.setOrderedToSit(true);
        pet.setInSittingPose(true);
        gym.food.insertItem(3, CritterCourierData.stack(CritterCourierData.save(pet)), false);
        gym.food.insertItem(0, new ItemStack(Items.SALMON, 8), false);
        gym.serverTick();
        require(gym.getGeneration() > 0, "Captive training starts");
        var rs = gym.screenRedstone();
        int front = RelativeFace.FRONT.ordinal(), back = RelativeFace.BACK.ordinal(),
            right = RelativeFace.RIGHT.ordinal();
        rs.mode(back, RedstoneControl.Mode.EMIT);
        require(rs.source(back, 1), "Working output exists");
        gym.serverTick();
        require(rs.signal(Direction.NORTH) == 15, "Working output emits");
        rs.mode(front, RedstoneControl.Mode.READ);
        level.setBlock(gym.getBlockPos().north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        var captiveBefore = CritterCourierData.read(gym.food.getStackInSlot(3));
        var foodBefore = gym.food.getStackInSlot(0).getCount();
        gym.serverTick();
        require(gym.getGeneration() == 0 && gym.training.status().equals("redstone"),
            "Redstone pauses training distinctly");
        lamps(gym, 0);
        for (int i = 0; i < 10; i++)
            gym.serverTick();
        require(
            gym.food.getStackInSlot(0).getCount() == foodBefore
                && CritterCourierData.read(gym.food.getStackInSlot(3)).equals(captiveBefore),
            "Pause consumes no food and preserves captive");
        require(rs.signal(Direction.NORTH) == 0 && gym.displayFood().isEmpty(),
            "Working output and bait stop");
        level.setBlock(gym.getBlockPos().north(), Blocks.AIR.defaultBlockState(), 3);
        gym.serverTick();
        require(gym.getGeneration() > 0, "Clearing redstone resumes");
        lamps(gym, 14);
        gym.control("toggle");
        gym.serverTick();
        require(gym.getGeneration() == 0 && !gym.training.enabled(),
            "Manual pause remains independent");
        var restoredPet = CritterCourierData.load(CritterCourierData.read(gym.food.getStackInSlot(3)), level);
        require(
            restoredPet instanceof net.minecraft.world.entity.TamableAnimal tame
                && owner.equals(tame.getOwnerUUID()) && tame.isOrderedToSit(),
            "Ownership and sit command retained");
        var player = h.makeMockServerPlayerInLevel();
        player.setPos(gym.getBlockPos().getX(), gym.getBlockPos().getY(), gym.getBlockPos().getZ());
        var menu = new MachineMenu(81, player.getInventory(), gym);
        player.containerMenu = menu;
        var sides = menu.components().get(BuiltinMenuComponents.AUTOMATION);
        sides.action(player, new AutomationNetwork.Action(81, menu.sessionId(), 1, "select", 0, -1,
            0, 0, "", 0, ""));
        sides.action(player, new AutomationNetwork.Action(81, menu.sessionId(), 2, "face", 0, -1, 0,
            0, "DISABLED", right, ""));
        require(gym.foodSides.get(RelativeFace.RIGHT) == IoMode.DISABLED,
            "Forged blocked side edit refused");
        var redstone = menu.components().get(BuiltinMenuComponents.REDSTONE);
        redstone.action(player, new RedstoneNetwork.Action(81, menu.sessionId(), 1, rs.revision(),
            RedstoneMenu.TOGGLE, 0, 0, 0));
        redstone.action(player, new RedstoneNetwork.Action(81, menu.sessionId(), 2, rs.revision(),
            RedstoneMenu.SELECT, right, 0, 0));
        redstone.action(player, new RedstoneNetwork.Action(81, menu.sessionId(), 3, rs.revision(),
            RedstoneMenu.MODE, right, 0, 1));
        require(redstone.selected() != right && rs.mode(right) == RedstoneControl.Mode.IGNORE,
            "Forged blocked redstone selection/edit refused");
        menu.removed(player);
        h.succeed();
    }
}
