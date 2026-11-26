package net.modcrafters.minecraft.industrialoverkill.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.waste.FarmRecipeBooks;
import net.modcrafters.minecraft.industrialoverkill.waste.FertilizerMakerProcessor;
import zone.moddev.mc.lib.gui.client.DrawerFactories;
import zone.moddev.mc.lib.gui.client.recipe.RecipeCatalog;
import zone.moddev.mc.lib.gui.client.recipe.RecipeDrawer;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;

@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FarmRecipeCatalog implements RecipeCatalog {
    private static long recipeRevision;
    private final ResourceLocation machine;
    private ClientLevel level;
    private long revision = -1;
    private List<Group> groups = List.of();

    private FarmRecipeCatalog(ResourceLocation machine) {
        this.machine = machine;
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            NeoForge.EVENT_BUS.addListener((RecipesUpdatedEvent updated) -> recipeRevision++);
            NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent updated) -> recipeRevision++);
            DrawerFactories.GLOBAL.register(FarmRecipeBooks.DRAWER, (screen, definition) ->
                new RecipeDrawer(screen, definition, new FarmRecipeCatalog(screen.getMenu().bound.screen().id())));
        });
    }

    @Override
    public List<Group> groups() {
        var current = Minecraft.getInstance().level;
        if (level == current && revision == recipeRevision) return groups;
        level = current;
        revision = recipeRevision;
        if (level == null) return groups = List.of();

        var choices = new ArrayList<Choice>();
        if (machine.equals(FarmRecipeBooks.SEWAGE_FILTER)) {
            MachineContent.recipeType(FarmRecipeBooks.SEWAGE_FILTER).recipes(level.getRecipeManager()).stream()
                .sorted(Comparator.comparing(RecipeHolder::id))
                .forEach(entry -> add(choices, entry.id(), entry.value().recipe()));
        } else {
            FertilizerMakerProcessor.recipePreviews().forEach(entry -> add(choices, entry.id(), entry.recipe()));
        }
        var icon = new ItemStack(MachineContent.item(machine));
        var title = Component.translatable("block." + machine.getNamespace() + "." + machine.getPath());
        return groups = List.of(new Group(machine.toString(), title, icon, choices));
    }

    private static void add(List<Choice> choices, ResourceLocation id, MachineRecipe recipe) {
        var view = new FarmRecipeView(recipe);
        if (view.available()) choices.add(new Choice(id.toString(), view));
    }

    @Override
    public boolean select(Group group, Choice choice) {
        // The tank contents and the maker's existing mode control select recipes.
        return false;
    }
}
