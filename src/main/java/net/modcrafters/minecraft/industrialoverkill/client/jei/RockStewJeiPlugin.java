package net.modcrafters.minecraft.industrialoverkill.client.jei;

import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.modcrafters.minecraft.industrialoverkill.stew.RockStewRecipes;
import net.modcrafters.minecraft.industrialoverkill.stew.RockStewScreen;
import net.modcrafters.minecraft.industrialoverkill.stew.RockStewTree;
import zone.moddev.mc.lib.client.jei.MachineRecipeCategory;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.ScreenRuntime;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.RecipeTransferResult;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import java.util.Optional;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

/** JEI-only classes are reached through this plugin, never by the optional-free client. */
@JeiPlugin
public final class RockStewJeiPlugin implements IModPlugin {
    private List<RockStewTreeCategory.Entry> entries = List.of();
    @Override public ResourceLocation getPluginUid() { return RockStewRecipes.id("rock_stew_jei"); }

    @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        net.modcrafters.minecraft.industrialoverkill.client.RockStewClient.recipeViewer = () -> {
            runtime.getRecipesGui().showTypes(List.of(RockStewTreeCategory.TYPE));
            return true;
        };
    }

    @Override public void onRuntimeUnavailable() {
        net.modcrafters.minecraft.industrialoverkill.client.RockStewClient.recipeViewer = () -> false;
    }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new RockStewTreeCategory(registration.getJeiHelpers().getGuiHelper()));
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        var pages = new ArrayList<RockStewTreeCategory.Entry>();
        if (level != null) for (var source : RockStewRecipes.sources(level)) {
            var graph = RockStewRecipes.graph(level, source);
            var order = RockStewTree.order(graph);
            for (int node : order) pages.add(new RockStewTreeCategory.Entry(source.id(), node,
                RockStewTree.row(source.value().recipe(), graph, node)));
        }
        entries = List.copyOf(pages);
        registration.addRecipes(RockStewTreeCategory.TYPE, entries);
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(MachineContent.item(RockStewRecipes.id("rock_stew_factory"))),
            RockStewTreeCategory.TYPE, MachineRecipeCategory.recipeType(RockStewRecipes.SOURCES),
            MachineRecipeCategory.recipeType(RockStewRecipes.GRINDING));
    }
    @Override public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        var helper = registration.getTransferHelper();
        registration.addRecipeTransferHandler(new IRecipeTransferHandler<MachineMenu, RockStewTreeCategory.Entry>() {
            @Override public Class<MachineMenu> getContainerClass() { return MachineMenu.class; }
            @Override public Optional<MenuType<MachineMenu>> getMenuType() { return Optional.of(ScreenRuntime.MENUS.get()); }
            @Override public RecipeType<RockStewTreeCategory.Entry> getRecipeType() { return RockStewTreeCategory.TYPE; }
            @Override @SuppressWarnings("removal")
            public IRecipeTransferError transferRecipe(MachineMenu menu, RockStewTreeCategory.Entry entry,
                IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer) {
                // Applying a route is configuration: it never moves items or consumes recipe fluids.
                return select(menu, new RockStewScreen.Selection(entry.source(), entry.row().destination()), !doTransfer)
                    ? null : helper.createInternalError();
            }
            @Override public IRecipeTransferError transferRecipe(
                IRecipeTransferContext<RockStewTreeCategory.Entry, MachineMenu> context, boolean doTransfer) {
                var error = transferRecipe(context.getContainer(), context.getRecipe(), context.getRecipeSlots(),
                    context.getPlayer(), context.isMaxTransfer(), doTransfer);
                if (doTransfer) context.completeRecipeTransfer(error == null ? RecipeTransferResult.SUCCESS : RecipeTransferResult.REJECTED);
                return error;
            }
        }, RockStewTreeCategory.TYPE);
    }

    private static boolean select(MachineMenu menu, RockStewScreen.Selection selection, boolean simulate) {
        if (!menu.bound.screen().id().equals(RockStewRecipes.id("rock_stew_factory"))
            || Minecraft.getInstance().player == null || Minecraft.getInstance().player.containerMenu != menu) return false;
        // Any processor in the selected line can submit a new route.
        var target = java.util.stream.IntStream.range(0, net.modcrafters.minecraft.industrialoverkill.stew.RockStewProcessor.SLOTS)
            .mapToObj(index -> menu.bound.screen().find("stage_processor_" + index).orElseThrow())
            .filter(menu::visible).filter(element -> !menu.elementPending(element)).findFirst();
        if (target.isEmpty()) return false;
        if (simulate) return true;
        var packet = menu.prepareElementAction(target.get(), RockStewScreen.SELECT, selection);
        if (packet.isEmpty()) return false;
        PacketDistributor.sendToServer(packet.get());
        return true;
    }
}
