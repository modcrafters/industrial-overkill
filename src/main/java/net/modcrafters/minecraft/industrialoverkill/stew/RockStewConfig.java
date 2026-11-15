package net.modcrafters.minecraft.industrialoverkill.stew;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned recipe policy, synchronized by NeoForge when a player joins. */
public final class RockStewConfig {
    public static final List<String> DEFAULT_EXCLUDED_TAGS = List.of("c:ingots", "c:ingots/*");
    public static final List<String> DEFAULT_ALLOWED_TAGS = List.of("industrial_overkill:rock_stew_building_blocks");
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_ITEM_TAGS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_ITEM_TAGS;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("rockStew");
        ALLOWED_ITEM_TAGS = builder.comment(
            "Only items matching these tags may be Rock Stew sources, intermediates or final products.",
            "The default building-block allowlist can be extended or replaced through a datapack.",
            "Supports exact tags and trailing /* patterns. An empty list allows no products.",
            "Excluded tags always take precedence. Reopen the world or restart the server after editing.")
            .worldRestart().defineListAllowEmpty("allowedItemTags", DEFAULT_ALLOWED_TAGS,
                () -> "industrial_overkill:rock_stew_building_blocks", RockStewConfig::validPattern);
        EXCLUDED_ITEM_TAGS = builder.comment(
            "Item tags excluded from Rock Stew sources, intermediate products and final outputs.",
            "Use namespace:path for an exact tag, or namespace:path/* for tags below that path.",
            "A wildcard does not include the parent tag. An empty list disables tag exclusions.",
            "Reopen the world or restart the server after changing this setting.")
            .worldRestart().defineListAllowEmpty("excludedItemTags", DEFAULT_EXCLUDED_TAGS,
                () -> "c:ingots", RockStewConfig::validPattern);
        builder.pop();
        SPEC = builder.build();
    }

    private RockStewConfig() {}

    public static void register(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC, "industrial_overkill-server.toml");
        bus.addListener((ModConfigEvent.Loading event) -> {
            if (event.getConfig().getSpec() != SPEC) return;
            // Freeze world-restart values for this connection/world, before recipe discovery.
            EXCLUDED_ITEM_TAGS.get();
            ALLOWED_ITEM_TAGS.get();
            RockStewRecipes.invalidate();
        });
        bus.addListener((ModConfigEvent.Unloading event) -> {
            if (event.getConfig().getSpec() == SPEC) RockStewRecipes.invalidate();
        });
    }

    public static boolean validPattern(Object value) {
        if (!(value instanceof String pattern) || pattern.indexOf(':') < 1) return false;
        String base = pattern.endsWith("/*") ? pattern.substring(0, pattern.length() - 2) : pattern;
        return !base.endsWith(":") && ResourceLocation.tryParse(base) != null;
    }

    public static boolean matchesTag(String pattern, ResourceLocation tag) {
        if (!validPattern(pattern)) return false;
        String id = tag.toString();
        return pattern.endsWith("/*") ? id.startsWith(pattern.substring(0, pattern.length() - 1)) : id.equals(pattern);
    }

    public static boolean excluded(ItemStack stack) {
        var patterns = SPEC.isLoaded() ? EXCLUDED_ITEM_TAGS.get() : DEFAULT_EXCLUDED_TAGS;
        return !patterns.isEmpty() && stack.getTags().anyMatch(tag ->
            patterns.stream().anyMatch(pattern -> matchesTag(pattern, tag.location())));
    }

    public static boolean allowed(ItemStack stack) {
        if (stack.isEmpty() || excluded(stack)) return false;
        var patterns = SPEC.isLoaded() ? ALLOWED_ITEM_TAGS.get() : DEFAULT_ALLOWED_TAGS;
        return stack.getTags().anyMatch(tag -> patterns.stream()
            .anyMatch(pattern -> matchesTag(pattern, tag.location())));
    }
}
