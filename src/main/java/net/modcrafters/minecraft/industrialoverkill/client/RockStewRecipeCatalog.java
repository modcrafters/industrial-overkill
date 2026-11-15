package net.modcrafters.minecraft.industrialoverkill.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.modcrafters.minecraft.industrialoverkill.stew.RockStewRecipes;
import net.modcrafters.minecraft.industrialoverkill.stew.RockStewScreen;
import net.modcrafters.minecraft.industrialoverkill.stew.RockStewTree;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.client.recipe.RecipeCatalog;

/** Synced recipe data supplies previews; the existing SELECT action validates every requested route. */
public final class RockStewRecipeCatalog implements RecipeCatalog {
    private static long clientRevision;
    public static void invalidate() { clientRevision++; }
    private final MachineScreen screen;
    private Level level;
    private long epoch = Long.MIN_VALUE;
    private long revision = Long.MIN_VALUE;
    private List<Group> groups = List.of();

    public RockStewRecipeCatalog(MachineScreen screen) { this.screen = screen; }

    @Override public String initialGroup() {
        var processor = screen.getMenu().bound.screen().find("stage_processor_0").orElseThrow();
        var value = screen.getMenu().value(processor, false);
        if (value instanceof net.minecraft.world.item.ItemStack item)
            return groups().stream().filter(g -> net.minecraft.world.item.ItemStack.isSameItemSameComponents(g.icon(), item))
                .map(Group::key).findFirst().orElse("");
        return "";
    }

    @Override public List<Group> groups() {
        var current = Minecraft.getInstance().level;
        if (current == level && epoch == RockStewRecipes.epoch() && revision == clientRevision) return groups;
        level = current;
        epoch = RockStewRecipes.epoch();
        revision = clientRevision;
        var result = new ArrayList<Group>();
        if (level != null) for (var source : RockStewRecipes.sources(level)) {
            var graph = RockStewRecipes.graph(level, source);
            var choices = new ArrayList<Choice>();
            for (int node : RockStewTree.order(graph)) {
                var row = RockStewTree.row(source.value().recipe(), graph, node);
                choices.add(new Choice(Integer.toString(node), RockStewTreeRenderer.presentation(row)));
            }
            var icon = source.value().getResultItem(level.registryAccess());
            result.add(new Group(source.id().toString(), icon.getHoverName(), icon, choices));
        }
        return groups = List.copyOf(result);
    }

    @Override public boolean select(Group group, Choice choice) {
        // No trust in the preview: the server rebuilds the route and applies exclusions and depth limits.
        var menu = screen.getMenu();
        var target = menu.bound.screen().find("stage_processor_0").orElseThrow();
        var destination = choice.presentation().items().getLast().stack();
        return screen.submitElementAction(target, RockStewScreen.SELECT,
            new RockStewScreen.Selection(ResourceLocation.parse(group.key()), destination));
    }
}
