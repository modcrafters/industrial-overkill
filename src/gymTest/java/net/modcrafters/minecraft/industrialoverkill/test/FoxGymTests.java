package net.modcrafters.minecraft.industrialoverkill.test;

import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.place;
import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.require;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierData;

@GameTestHolder(IndustrialOverkill.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FoxGymTests {
    @GameTest(template = "gym_empty")
    public static void capturedFoxesEatBothBerriesAndPreserveRestingState(GameTestHelper h) {
        var gym = place(h, Direction.NORTH);
        for (var berry : List.of(Items.SWEET_BERRIES, Items.GLOW_BERRIES)) {
            boolean sleeping = berry == Items.SWEET_BERRIES;
            var cage = CritterCourierTests.trap(h, gym.getBlockPos().east(3));
            var fox = CritterCourierTests.mob(h, cage, EntityType.FOX);
            var trusted = UUID.randomUUID();
            var original = CritterCourierData.save(fox);
            var trust = new ListTag();
            trust.add(NbtUtils.createUUID(trusted));
            original.put("Trusted", trust);
            original.putBoolean("Sleeping", sleeping);
            original.putBoolean("Sitting", !sleeping);
            original.putString("Type", sleeping ? "snow" : "red");
            fox.load(original);
            var uuid = fox.getUUID();
            require(cage.capture(fox), "Resting fox captured");
            require(gym.food.insertItem(3, cage.take(), false).isEmpty(), "Fox trap installed");
            gym.food.insertItem(0, new ItemStack(Items.WHEAT, 2), false);
            if (!gym.training.enabled())
                gym.control("toggle");
            gym.serverTick();
            require(gym.getGeneration() == 0 && gym.food.getStackInSlot(0).getCount() == 2,
                "Fox refuses wheat");
            require(gym.food.insertItem(1, new ItemStack(berry, 2), false).isEmpty(),
                "Berry accepted into food inventory");
            gym.serverTick();
            require(
                gym.getGeneration() == 4 && gym.displayFood().is(berry)
                    && gym.food.getStackInSlot(1).getCount() == 1,
                "Either berry feeds a working fox");
            var captive = (Fox) gym.captive.animal();
            require(captive.isSleeping() == sleeping && captive.isSitting() != sleeping,
                "Training never mutates the server resting flags");
            require(!captive.getPersistentData().contains("industrial_overkill:fitness"),
                "Running does not accumulate fitness");
            gym.control("toggle");
            var saved = gym.saveWithFullMetadata(h.getLevel().registryAccess());
            gym.loadWithComponents(saved, h.getLevel().registryAccess());
            var carried = gym.food.extractItem(3, 1, false);
            var data = CritterCourierData.read(carried);
            require(
                data.getBoolean("Sleeping") == sleeping && data.getBoolean("Sitting") != sleeping
                    && data.getList("Trusted", 11).equals(trust),
                "Reload/ejection preserve resting state and trusted players");
            h.getLevel().removeBlock(cage.getBlockPos(), false);
            cage = CritterCourierTests.trap(h, gym.getBlockPos().east(3));
            cage.setMob(data);
            require(cage.release(h.makeMockPlayer(GameType.CREATIVE)),
                "Trained fox releases safely");
            var released = (Fox) h.getLevel().getEntity(uuid);
            require(
                released != null && released.isSleeping() == sleeping
                    && released.isSitting() != sleeping
                    && released.getVariant() == (sleeping ? Fox.Type.SNOW : Fox.Type.RED),
                "Release preserves identity, variant and resting pose");
            released.discard();
            h.getLevel().removeBlock(cage.getBlockPos(), false);
            gym.food.extractItem(0, 64, false);
            gym.food.extractItem(1, 64, false);
        }
        h.succeed();
    }
    @GameTest(template = "gym_empty")
    public static void adultLooseFoxesQualifyButBabiesDoNot(GameTestHelper h) {
        var gym = place(h, Direction.WEST);
        var fox = (Fox) AnimalGymTests.animal(h, gym, EntityType.FOX);
        fox.setNoAi(true);
        gym.food.insertItem(0, new ItemStack(Items.GLOW_BERRIES, 3), false);
        fox.setAge(-1000);
        gym.serverTick();
        require(gym.getGeneration() == 0 && gym.food.getStackInSlot(0).getCount() == 3,
            "Baby fox is excluded");
        fox.setAge(0);
        gym.serverTick();
        require(gym.getGeneration() == 4 && gym.training.animal() == fox,
            "Adult loose fox trains with berries");
        h.succeed();
    }
}
