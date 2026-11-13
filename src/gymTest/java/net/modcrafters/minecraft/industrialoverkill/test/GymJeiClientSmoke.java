package net.modcrafters.minecraft.industrialoverkill.test;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import net.modcrafters.minecraft.industrialoverkill.client.jei.GymRecipeCategory;
import net.modcrafters.minecraft.industrialoverkill.gym.AnimalGym;
import net.modcrafters.minecraft.industrialoverkill.gym.GymAnimalRecipe;
import zone.moddev.mc.lib.MMDLib;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;

/** An actual JEI runtime check, loaded only by the optional mod in the test source set. */
@JeiPlugin
public final class GymJeiClientSmoke implements IModPlugin {
    private IJeiRuntime runtime;
    private boolean started, finished;
    private int ticks, stage, runtimes;
    private final java.util.Map<EntityType<?>, Float> previousPhases = new java.util.HashMap<>();
    private long sampledAt;
    private double previousRate;

    private final long deadline = System.currentTimeMillis() + 180000;
    public GymJeiClientSmoke() {
        if (Boolean.getBoolean("industrialoverkill.jeiSmoke"))
            NeoForge.EVENT_BUS.addListener(this::tick);
    }
    @Override
    public ResourceLocation getPluginUid() {
        return AnimalGym.id("jei_test");
    }
    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        this.runtime = runtime;
        runtimes++;
    }
    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    private void tick(ClientTickEvent.Post event) {
        if (finished)
            return;
        var mc = Minecraft.getInstance();
        try {
            if (System.currentTimeMillis() > deadline)
                throw new AssertionError("JEI timed out at " + stage);
            if (!started && mc.screen instanceof TitleScreen) {
                started = true;
                mc.createWorldOpenFlows().createFreshLevel("jei-" + System.currentTimeMillis(),
                    new LevelSettings("Gym JEI", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                        true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(1, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                    mc.screen);
            }
            if (runtime == null || mc.level == null || mc.player == null || ++ticks < 40)
                return;
            ticks = 0;
            var manager = runtime.getRecipeManager();
            var entries = manager.createRecipeLookup(GymRecipeCategory.TYPE).get().toList();
            var focuses = runtime.getJeiHelpers().getFocusFactory();
            switch (stage) {
                case 0 -> {
                    check(entries.size() == 18, "One entry per animal including tag members");
                    check(
                        entries.stream().map(entry -> entry.animal().getType()).distinct()
                            .count() == 18,
                        "Food tiers never duplicate animal entries");
                    check(manager.createRecipeCatalystLookup(GymRecipeCategory.TYPE).getItemStack()
                        .anyMatch(stack -> stack.is(AnimalGym.ITEM.get())),
                        "Gym catalyst registered");
                    var food = focuses.createFocus(RecipeIngredientRole.INPUT,
                        VanillaTypes.ITEM_STACK, new ItemStack(Items.BAMBOO));
                    var found = manager.createRecipeLookup(GymRecipeCategory.TYPE)
                        .limitFocus(List.of(food)).get().toList();
                    check(
                        found.size() == 1
                            && found.getFirst().animal().getType() == EntityType.PANDA,
                        "Bamboo usage finds panda");
                    checkFood(entries, EntityType.WOLF, Items.BEEF, 6, 600);
                    checkFood(entries, EntityType.WOLF, Items.COOKED_BEEF, 6, 900);
                    checkFood(entries, EntityType.WOLF, Items.ROTTEN_FLESH, 6, 240);
                    checkFood(entries, EntityType.HORSE, Items.WHEAT, 24, 200);
                    checkFood(entries, EntityType.HORSE, Items.HAY_BLOCK, 24, 1800);
                    checkFood(entries, EntityType.HORSE, Items.GOLDEN_CARROT, 30, 400);
                    checkFood(entries, EntityType.RABBIT, Items.GOLDEN_CARROT, 3, 1800);
                    for (var entry : entries) {
                        check(
                            manager.createRecipeLayoutDrawable(
                                manager.getRecipeCategory(GymRecipeCategory.TYPE), entry,
                                focuses.getEmptyFocusGroup()).isPresent(),
                            "Every food layout builds");
                    }
                    runtime.getRecipesGui().showRecipes(
                        manager.getRecipeCategory(GymRecipeCategory.TYPE),
                        entries.stream()
                            .filter(entry -> entry.animal().getType() == EntityType.PANDA
                                || entry.animal().getType() == EntityType.WOLF
                                || entry.animal().getType() == EntityType.HORSE)
                            .toList(),
                        List.of());
                }
                case 1 -> {
                    check(visibleLayouts().size() == 3, "Three full cards fit at GUI scale 3");
                    checkAnimation(1);
                    shot("gym-jei-intensity-1.png");
                    clickIntensity(2, 0);
                }
                case 2 -> {
                    checkRates(2);
                    checkAnimation(2);
                    shot("gym-jei-intensity-2.png");
                    clickIntensity(3, 2);
                }
                case 3 -> {
                    checkRates(3);
                    checkAnimation(3);
                    shot("gym-jei-intensity-3.png");
                    clickIntensity(1, 1);
                    checkRates(1);
                    show(entries, EntityType.RABBIT);
                }
                case 4 -> {
                    var rabbit = (net.minecraft.world.entity.animal.Rabbit) ((GymRecipeCategory.Entry) visibleLayouts()
                        .getFirst().getRecipe()).animal();
                    check(rabbit.jumpDuration > 0 && rabbit.walkAnimation.position() > 0,
                        "Rabbit previews use their native hopping gait");
                    shot("gym-jei-rabbit-running.png");
                    show(entries, EntityType.WOLF);
                }
                case 5 -> {
                    checkGroupedCard(3);
                    shot("gym-jei-wolf-food-tiers.png");
                    show(entries, EntityType.HORSE);
                }
                case 6 -> {
                    checkGroupedCard(3);
                    shot("gym-jei-horse-food-tiers.png");
                    mc.setScreen(null);
                    // Exercise the same recipe synchronization that follows /reload.
                    mc.getSingleplayerServer().execute(() -> {
                        try {
                            var server = mc.getSingleplayerServer();
                            var recipes = new java.util.ArrayList<RecipeHolder<?>>();
                            for (var holder : server.getRecipeManager().getRecipes()) {
                                if (holder.value() instanceof GymAnimalRecipe rule) {
                                    boolean panda = rule.entity().matches(EntityType.PANDA);
                                    recipes.add(new RecipeHolder<>(holder.id(),
                                        new GymAnimalRecipe(rule.entity(), rule.food(),
                                            panda, panda ? 37 : rule.energyPerTick(),
                                            panda ? 450 : rule.foodDurationTicks())));
                                } else
                                    recipes.add(holder);
                            }
                            var pandaRule = recipes.stream().map(RecipeHolder::value)
                                .filter(GymAnimalRecipe.class::isInstance)
                                .map(GymAnimalRecipe.class::cast)
                                .filter(rule -> rule.enabled()).findFirst().orElseThrow();
                            recipes.add(new RecipeHolder<>(AnimalGym.id("zzz_shadowed_panda"),
                                new GymAnimalRecipe(pandaRule.entity(), pandaRule.food(), true, 99,
                                    200)));
                            server.getRecipeManager().replaceRecipes(recipes);
                            server.getPlayerList()
                                .broadcastAll(new ClientboundUpdateRecipesPacket(recipes));
                        } catch (Throwable failure) {
                            mc.execute(() -> fail(failure));
                        }
                    });
                }
                case 7 -> {
                    if (runtimes < 2)
                        return;
                    check(entries.size() == 1,
                        "Reload removes disabled and fully shadowed recipes");
                    check(
                        entries.getFirst().tiers().size() == 1
                            && entries.getFirst().tiers().getFirst().energyPerTick() == 37
                            && entries.getFirst().tiers().getFirst().foodDurationTicks() == 450,
                        "Reload updates displayed rates");
                    show(entries, EntityType.PANDA);
                }
                case 8 -> {
                    shot("gym-jei-reloaded.png");
                    finished = true;
                    MMDLib.LOGGER.info(
                        "GYM_JEI_CLIENT_PASS: one card per animal, grouped food rates, three cards per page, animated intensity controls, and reload priority filtering");
                    mc.stop();
                }
            }
            stage++;
        } catch (Throwable failure) {
            fail(failure);
        }
    }
    // JEI exposes no public API for the visible card rectangles. Keep introspection in this
    // smoke test only; production input uses the supported recipe-extras API.
    private List<mezz.jei.api.gui.IRecipeLayoutDrawable<?>> visibleLayouts() throws Exception {
        var screen = Minecraft.getInstance().screen;
        var lookup = screen.getClass().getMethod("getRecipeLayoutUnderMouse", double.class,
            double.class);
        var layouts = new java.util.LinkedHashSet<mezz.jei.api.gui.IRecipeLayoutDrawable<?>>();
        for (int y = 0; y < screen.height; y += 3) {
            var result = (java.util.Optional<?>) lookup.invoke(screen, screen.width / 2.0,
                (double) y);
            if (result.isPresent()) {
                var method = result.get().getClass().getMethod("getRecipeLayout");
                method.setAccessible(true);
                layouts
                    .add((mezz.jei.api.gui.IRecipeLayoutDrawable<?>) method.invoke(result.get()));
            }
        }
        return List.copyOf(layouts);
    }
    private void clickIntensity(int intensity, int card) throws Exception {
        var screen = Minecraft.getInstance().screen;
        var category = (GymRecipeCategory) runtime.getRecipeManager()
            .getRecipeCategory(GymRecipeCategory.TYPE);
        int previous = category.intensity();
        var rect = visibleLayouts().get(card).getRect();
        int x = rect.getX() + 179, y = rect.getY() + 5;
        check(intensity == previous % 3 + 1, "Single button cycles 1 to 2 to 3 and back to 1");
        check(screen.mouseClicked(x, y, 0), "Intensity press handled by JEI");
        check(category.intensity() == previous, "Simulated mouse down does not change intensity");
        check(screen.mouseReleased(x, y, 0), "Intensity release handled by JEI");
        check(category.intensity() == intensity, "Actual click selects intensity");
    }
    private void checkRates(int intensity) throws Exception {
        var category = (GymRecipeCategory) runtime.getRecipeManager()
            .getRecipeCategory(GymRecipeCategory.TYPE);
        check(category.intensity() == intensity, "Shared intensity is selected");
        for (var layout : visibleLayouts()) {
            var entry = (GymRecipeCategory.Entry) layout.getRecipe();
            for (var tier : entry.tiers())
                check(category.power(tier) == tier.energyPerTick() * intensity
                    && Math.abs(category.durationSeconds(tier)
                        - tier.foodDurationTicks() / (20.0 * intensity)) < .0001,
                    "Every visible food group updates both power and food duration");
        }
    }
    private void checkAnimation(int intensity) throws Exception {
        long now = System.nanoTime();
        double seconds = (now - sampledAt) / 1_000_000_000.0;
        double rate = 0;
        int count = 0;
        for (var layout : visibleLayouts()) {
            var animal = ((GymRecipeCategory.Entry) layout.getRecipe()).animal();
            check(Math.abs(animal.walkAnimation.speed() - (.35f + .15f * intensity)) < .001,
                "Rendered stride follows the selected intensity");
            var previous = previousPhases.put(animal.getType(), animal.walkAnimation.position());
            if (previous != null) {
                float advance = animal.walkAnimation.position() - previous;
                check(advance > 1, "Rendered legs advance between frames");
                rate += advance / seconds;
                count++;
            }
            check(Minecraft.getInstance().level.getEntity(animal.getId()) != animal,
                "Preview never becomes a world entity");
        }
        if (count > 0) {
            rate /= count;
            if (previousRate > 0)
                check(rate > previousRate * 1.08,
                    "Measured gait becomes faster at higher intensity");
            previousRate = rate;
        }
        sampledAt = now;
    }
    private void checkFood(List<GymRecipeCategory.Entry> entries, EntityType<?> type,
        net.minecraft.world.item.Item item, int power, int duration) {
        var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(
            RecipeIngredientRole.INPUT,
            VanillaTypes.ITEM_STACK, new ItemStack(item));
        var matching = runtime.getRecipeManager().createRecipeLookup(GymRecipeCategory.TYPE)
            .limitFocus(List.of(focus)).get().filter(entry -> entry.animal().getType() == type)
            .toList();
        check(matching.size() == 1, "Food lookup selects exactly one animal card: " + item);
        var entry = matching.getFirst();
        var tiers = entry.tiers().stream()
            .filter(tier -> tier.foods().stream().anyMatch(stack -> stack.is(item))).toList();
        check(tiers.size() == 1, "Food appears in exactly one effective group");
        var tier = tiers.getFirst();
        check(
            tier.energyPerTick() == power
                && tier.foodDurationTicks() == duration,
            "JEI rates match the selected food: " + item);
    }
    private void checkGroupedCard(int tiers) throws Exception {
        var layouts = visibleLayouts();
        check(layouts.size() == 1, "All food groups share one animal card");
        var entry = (GymRecipeCategory.Entry) layouts.getFirst().getRecipe();
        check(entry.tiers().size() == tiers, "Every distinct food effect has its own row");
        check(14 + tiers * 24 <= layouts.getFirst().getRect().getHeight(),
            "Food rows fit within the card");
    }
    private void show(List<GymRecipeCategory.Entry> entries, EntityType<?> type) {
        runtime.getRecipesGui().showRecipes(
            runtime.getRecipeManager().getRecipeCategory(GymRecipeCategory.TYPE),
            entries.stream().filter(entry -> entry.animal().getType() == type).toList(), List.of());
    }
    private static void shot(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {
        });
    }
    private static void check(boolean valid, String message) {
        if (!valid)
            throw new AssertionError(message);
    }
    private void fail(Throwable failure) {
        finished = true;
        MMDLib.LOGGER.error("GYM_JEI_CLIENT_FAIL", failure);
        Minecraft.getInstance().stop();
    }
}
