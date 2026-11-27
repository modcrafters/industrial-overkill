package net.modcrafters.minecraft.industrialoverkill.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.power.GeneratorBurnArrow;
import net.modcrafters.minecraft.industrialoverkill.power.GeneratorCatalyst;
import net.modcrafters.minecraft.industrialoverkill.power.GeneratorFuel;
import net.modcrafters.minecraft.industrialoverkill.power.OmnivorousGenerator;
import zone.moddev.mc.lib.gui.client.DefaultElementBehavior;
import zone.moddev.mc.lib.gui.client.DrawerFactories;
import zone.moddev.mc.lib.gui.client.DrawerOpener;
import zone.moddev.mc.lib.gui.client.ElementBehaviors;
import zone.moddev.mc.lib.gui.client.recipe.RecipeCatalog;
import zone.moddev.mc.lib.gui.client.recipe.RecipeDrawer;

@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class GeneratorRecipeCatalog implements RecipeCatalog {
    public record Entry(ResourceLocation id, boolean food, FarmRecipeView view) {}

    private static final AtomicLong REVISION = new AtomicLong();
    private ClientLevel level;
    private long seenRevision = -1;
    private List<Group> groups = List.of();

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            NeoForge.EVENT_BUS.addListener((RecipesUpdatedEvent updated) -> REVISION.incrementAndGet());
            NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent updated) -> REVISION.incrementAndGet());
            NeoForge.EVENT_BUS.addListener((DataMapsUpdatedEvent updated) -> {
                if (updated.getRegistryKey().equals(Registries.ITEM)) REVISION.incrementAndGet();
            });
            DrawerFactories.GLOBAL.register(OmnivorousGenerator.RECIPES, (screen, definition) ->
                new RecipeDrawer(screen, definition, new GeneratorRecipeCatalog()));
            ElementBehaviors.GLOBAL.register(GeneratorBurnArrow.class, (arrow, access) -> new DefaultElementBehavior(access) {
                @Override protected Click click(double x, double y, int button) {
                    if (button == 0 && (access.showRecipes() || access.service(DrawerOpener.class)
                        .map(drawers -> drawers.open(OmnivorousGenerator.RECIPES.getPath())).orElse(false))) return Click.CONSUME;
                    return super.click(x, y, button);
                }
            });
        });
    }

    public static List<Entry> recipes(ClientLevel level) {
        if (level == null) return List.of();
        var entries = new ArrayList<Entry>();
        var catalysts = GeneratorCatalyst.rules(level).values();
        for (var id : BuiltInRegistries.ITEM.keySet().stream().sorted().toList()) {
            var stack = BuiltInRegistries.ITEM.get(id).getDefaultInstance();
            var fuel = GeneratorFuel.forStack(level, stack);
            if (fuel == null) continue;
            var food = stack.getFoodProperties(null);
            boolean edible = food != null && food.nutrition() > 0;
            var recipeId = OmnivorousGenerator.ID.withPath("burn/" + id.getNamespace() + "/" + id.getPath());
            entries.add(entry(recipeId, edible, stack, fuel, null));
            for (var rule : catalysts) {
                var catalyst = rule.value();
                var variant = recipeId.withPath("catalyzed/" + rule.id().getNamespace() + "/" + rule.id().getPath()
                    + "/" + id.getNamespace() + "/" + id.getPath());
                entries.add(entry(variant, edible, stack, GeneratorFuel.forStack(level, stack, catalyst), catalyst));
            }
        }
        return List.copyOf(entries);
    }

    private static Entry entry(ResourceLocation id, boolean food, ItemStack stack, GeneratorFuel fuel, GeneratorCatalyst catalyst) {
        var stats = Component.translatable("omnivore.serving", fuel.energy(), fuel.rate());
        return new Entry(id, food, new FarmRecipeView(fuel.recipe(stack, catalyst), stats));
    }

    @Override public List<Group> groups() {
        var current = Minecraft.getInstance().level;
        long revision = REVISION.get();
        if (current == level && revision == seenRevision) return groups;
        level = current;
        seenRevision = revision;
        var foods = new ArrayList<Choice>();
        var fuels = new ArrayList<Choice>();
        for (var entry : recipes(current)) {
            var choices = entry.food() ? foods : fuels;
            choices.add(new Choice(entry.id().toString(), entry.view()));
        }
        var available = new ArrayList<Group>();
        if (!foods.isEmpty()) available.add(new Group("food", Component.translatable("omnivore.recipes.food"),
            new ItemStack(Items.POTATO), foods));
        if (!fuels.isEmpty()) available.add(new Group("fuel", Component.translatable("omnivore.recipes.fuel"),
            new ItemStack(Items.COAL), fuels));
        return groups = List.copyOf(available);
    }

    @Override public boolean select(Group group, Choice choice) { return false; }
}
