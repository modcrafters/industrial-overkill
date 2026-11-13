package net.modcrafters.minecraft.industrialoverkill.test;

import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.place;
import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.require;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.gym.GymAnimalRecipe;
import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierProcessor;

@GameTestHolder(IndustrialOverkill.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PandaGymTests {
    @GameTest(template = "gym_empty")
    public static void sittingPandaTrainsWithBambooAndKeepsGenesAndPose(GameTestHelper h) {
        var gym = place(h, Direction.NORTH);
        var cage = CritterCourierTests.trap(h, gym.getBlockPos().east(3));
        var panda = CritterCourierTests.mob(h, cage, EntityType.PANDA);
        panda.setMainGene(Panda.Gene.BROWN);
        panda.setHiddenGene(Panda.Gene.BROWN);
        panda.sit(true);
        var uuid = panda.getUUID();
        require(cage.capture(panda), "Sitting panda captured");
        var carried = cage.take();
        require(((Panda) CritterCourierProcessor.ENTITIES.createDetached(CritterCourierProcessor.OCCUPANT.read(carried), h.getLevel()).orElse(null)).isSitting(),
            "Trap preserves pose omitted by vanilla saves");
        gym.food.insertItem(0, new ItemStack(Items.WHEAT, 2), false);
        require(gym.food.insertItem(3, carried, false).isEmpty(), "Panda trap installed");
        gym.serverTick();
        require(gym.getGeneration() == 0 && gym.food.getStackInSlot(0).getCount() == 2,
            "Panda refuses wheat");
        require(!GymAnimalRecipe.foodFor(panda, new ItemStack(Items.CAKE)),
            "Cake is not gym training food");
        require(gym.food.insertItem(1, new ItemStack(Items.BAMBOO, 3), false).isEmpty(),
            "Bamboo accepted by inventory");
        gym.serverTick();
        require(
            gym.getGeneration() == 20 && gym.displayFood().is(Items.BAMBOO)
                && gym.food.getStackInSlot(1).getCount() == 2,
            "Bamboo feeds the panda and pays one ration");
        require(
            ((Panda) gym.captive.animal()).isSitting()
                && !gym.captive.animal().getPersistentData()
                    .contains("industrial_overkill:fitness"),
            "Running preserves server sitting flag without accumulating fitness");
        gym.control("toggle");
        var saved = gym.saveWithFullMetadata(h.getLevel().registryAccess());
        gym.loadWithComponents(saved, h.getLevel().registryAccess());
        require(((Panda) gym.captive.animal()).isSitting(), "Pause/reload retains sitting pose");
        var data = CritterCourierProcessor.OCCUPANT.read(gym.food.extractItem(3, 1, false));
        h.getLevel().removeBlock(cage.getBlockPos(), false);
        cage = CritterCourierTests.trap(h, gym.getBlockPos().east(3));
        cage.occupant(data);
        require(cage.release(h.makeMockPlayer(GameType.CREATIVE)),
            "Full-size panda releases beside trap");
        var released = (Panda) h.getLevel().getEntity(uuid);
        require(
            released != null && released.isSitting() && released.getMainGene() == Panda.Gene.BROWN
                && released.getHiddenGene() == Panda.Gene.BROWN,
            "Release retains identity, both genes and resting pose");
        h.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void adultLoosePandaFitsAndTrainsButBabyDoesNot(GameTestHelper h) {
        var gym = place(h, Direction.WEST);
        var panda = (Panda) AnimalGymTests.animal(h, gym, EntityType.PANDA);
        panda.setNoAi(true);
        gym.food.insertItem(0, new ItemStack(Items.BAMBOO, 3), false);
        panda.setAge(-1000);
        gym.serverTick();
        require(gym.getGeneration() == 0 && gym.food.getStackInSlot(0).getCount() == 3,
            "Baby panda excluded");
        panda.setAge(0);
        gym.serverTick();
        require(gym.getGeneration() == 20 && gym.training.animal() == panda, "Adult panda trains");
        require(panda.getBbWidth() <= net.modcrafters.minecraft.industrialoverkill.gym.GymAnimalFit.MAX_WIDTH
            + .001, "Large panda fits belt");
        gym.control("toggle");
        require(Math.abs(panda.getScale() - 1) < .001, "Pause restores original panda scale");
        h.succeed();
    }
}
