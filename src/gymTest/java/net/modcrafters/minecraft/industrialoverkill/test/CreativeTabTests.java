package net.modcrafters.minecraft.industrialoverkill.test;

import static net.modcrafters.minecraft.industrialoverkill.test.AnimalGymTests.require;

import java.util.HashSet;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("industrial_overkill_tabs")
@PrefixGameTestTemplate(false)
public final class CreativeTabTests {
    @GameTest(template = "gym_empty")
    public static void xmlTabsKeepMachinesAndSharedUpgradesWithTheirOwners(GameTestHelper h) {
        require(!BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(ResourceLocation.parse("mmdlib:data_machines")),
            "There is no shared data-defined machines tab");
        for (int rebuild = 0; rebuild < 2; rebuild++) {
            var machines = contents(h, "industrial_overkill:machines");
            require(machines.equals(List.of(item("industrial_overkill:agri_brain"),
                item("industrial_overkill:animal_gym"), item("industrial_overkill:baa_rber_shop"),
                item("industrial_overkill:critter_courier"),
                item("industrial_overkill:fertilizer"), item("industrial_overkill:fertilizer_maker"),
                item("industrial_overkill:filter_cake"), item("industrial_overkill:filtered_fertilizer"),
                item("industrial_overkill:filtered_sewage_bucket"), item("industrial_overkill:honest_sewer"),
                item("industrial_overkill:livestock_to_go"), item("industrial_overkill:pair_pressure"),
                item("industrial_overkill:ranch_controller"), item("industrial_overkill:rock_stew_factory"),
                item("industrial_overkill:sewage_bucket"),
                item("industrial_overkill:sewage_filter"), item("industrial_overkill:slow_release_pellet"),
                item("industrial_overkill:snackcelerator"))),
                "XML discovers native and generated items in ID order, excluding internal items and legacy upgrade aliases");
            var library = contents(h, "mmdlib:main");
            for (String upgrade : List.of("range_upgrade", "gearbox_upgrade", "capacitor_upgrade")) {
                require(library.contains(item("mmdlib:" + upgrade)), "MMDLib tab contains " + upgrade);
                require(!machines.contains(item("mmdlib:" + upgrade)), "IO tab excludes " + upgrade);
            }
            require(new HashSet<>(library).size() == library.size(), "Library items appear once");
            require(!library.contains(item("mmdlib:machine_recovery_capsule")), "Internal recovery capsule stays hidden");
            require(zone.moddev.mc.lib.MMDLib.creativeTabs().get(ResourceLocation.parse(
                "industrial_overkill:machines")).contents().isEmpty(), "No manual item list is needed");
            require(emptyOrAbsent(h, "industrial_overkill:mmdlib_content"),
                "Unused fallback does not appear as a second creative tab");
            require(emptyOrAbsent(h, "mmdlib:materials"), "Library materials have no separate tab");
        }
        h.succeed();
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static boolean emptyOrAbsent(GameTestHelper h, String id) {
        return !BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(ResourceLocation.parse(id)) || contents(h, id).isEmpty();
    }

    private static List<Item> contents(GameTestHelper h, String id) {
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(ResourceLocation.parse(id));
        require(tab != null, "Registered XML or fallback tab: " + id);
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
            h.getLevel().enabledFeatures(), false, h.getLevel().registryAccess()));
        return tab.getDisplayItems().stream().map(ItemStack::getItem).toList();
    }
}
