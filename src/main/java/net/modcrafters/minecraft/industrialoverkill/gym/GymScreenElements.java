package net.modcrafters.minecraft.industrialoverkill.gym;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.xml.namespace.QName;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;


import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.gui.load.ScreenAttribute;
import zone.moddev.mc.lib.gui.load.ScreenTag;
import zone.moddev.mc.lib.gui.load.ScreenTags;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.ElementSnapshot;
import zone.moddev.mc.lib.gui.model.ProcessorButtonState;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.model.SlotSize;
import zone.moddev.mc.lib.gui.model.elements.ItemSlotElement;
import zone.moddev.mc.lib.gui.model.elements.ProcessorButtonElement;
import zone.moddev.mc.lib.gui.runtime.ScreenClick;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.sync.ElementStateView;

/** Gym-specific policies using MMDLib's standard slot and control presentation. */
public final class GymScreenElements {
    public static final net.minecraft.resources.ResourceLocation RECIPE_DRAWER = AnimalGym.id("gym_recipes");
    public static final String RECIPE_DRAWER_ID = "gym_recipes";
    private static final String NAMESPACE = "https://moddev.zone/industrial-overkill/screen";
    private GymScreenElements() {
    }
    public static void register() {
        zone.moddev.mc.lib.gui.drawer.DrawerRegistry.GLOBAL.register(RECIPE_DRAWER, context ->
            context.screen().id().equals(AnimalGym.id("animal_gym"))
                ? List.of(new zone.moddev.mc.lib.gui.drawer.DrawerDefinition(RECIPE_DRAWER, RECIPE_DRAWER_ID,
                    zone.moddev.mc.lib.gui.drawer.DrawerPlacement.top(zone.moddev.mc.lib.gui.model.Edge.LEFT, 50)))
                : List.of());
        ScreenTags.GLOBAL.register(new QName(NAMESPACE, "food-slot"),
            ScreenTag
                .element(FoodSlot.class,
                    xml -> new FoodSlot(xml.common(), Integer.parseInt(xml.attribute("index"))))
                .attribute("index", ScreenAttribute.required("xs:nonNegativeInteger"))
                .group("slots").build());
        ScreenTags.GLOBAL.register(new QName(NAMESPACE, "run-button"),
            ScreenTag.element(RunButton.class, xml -> new RunButton(xml.common())).group("buttons")
                .build());

    }

    public static final class FoodSlot extends ItemSlotElement {
        public FoodSlot(Common common, int index) {
            super(common, index, SlotKind.NORMAL,
                Appearance.standard(SlotSize.NORMAL, Optional.empty(), Optional.empty()));
        }
        @Override
        public List<Component> tooltip(ScreenHost host, ItemStack carried) {
            if (!(host instanceof AnimalGymEntity gym))
                return List.of();
            var animal = gym.training.foodAnimal();
            if (animal == null)
                return List.of(Component.translatable(gym.hasCaptive()
                    ? "gym.food.none" : "gym.food.no_animal"));
            var lines = new ArrayList<Component>();
            var stack = gym.food.getStackInSlot(index());
            if (!stack.isEmpty()) {
                var recipe = GymAnimalRecipe.find(animal, stack);
                if (recipe == null) {
                    lines.add(Component.translatable("gym.food.unsuitable"));
                } else {
                    int intensity = gym.getIntensity();
                    var format = NumberFormat.getNumberInstance(Locale.ROOT);
                    format.setMaximumFractionDigits(2);
                    long total = (long) recipe.energyPerTick() * recipe.foodDurationTicks();
                    lines.addAll(List.of(
                        Component.translatable("gym.food.intensity", intensity),
                        Component.translatable("gym.food.power",
                            format.format(recipe.energyPerTick() * intensity)),
                        Component.translatable("gym.food.duration",
                            format.format(recipe.foodDurationTicks() / (20.0 * intensity))),
                        Component.translatable("gym.food.total_energy", format.format(total))));
                }
            }
            var foods = GymAnimalRecipe.foodsFor(animal);
            if (foods.isEmpty()) {
                lines.add(Component.translatable("gym.food.none"));
            } else {
                lines.add(Component.translatable("gym.food.accepts", animal.getName()));
                for (var food : foods)
                    lines.add(Component.translatable("gym.food.accepted_item", food.getHoverName()));
            }
            return List.copyOf(lines);
        }
    }

    /** Inherit MMDLib's glyphs, flat styling, tooltip and redstone conflict badge. */
    public static final class RunButton extends ProcessorButtonElement {
        public RunButton(Common common) {
            super(common, Optional.empty());
        }
        @Override
        public void validateHost(ScreenHost host) {
            if (!(host instanceof AnimalGymEntity))
                throw new IllegalArgumentException("Gym run button requires an Animal Gym");
        }
        @Override
        public void validateBindings(Map<String, Set<String>> bindings) {
        }
        @Override
        public boolean interact(ScreenClick click) {
            if (click.button() == 0 && click.option() == 0
                && click.menu().blockEntity instanceof AnimalGymEntity gym)
                gym.control("toggle");
            return true;
        }
        @Override
        public Optional<ElementSnapshot> snapshot(ScreenHost host) {
            var gym = (AnimalGymEntity) host;
            boolean paused = !gym.training.enabled();
            var redstone = gym.screenRedstone();
            int conflict = 0;
            for (int face = 0; face < 6; face++)
                if (redstone.mode(face) == RedstoneControl.Mode.READ
                    && redstone.paused() != paused) {
                    conflict = redstone.paused() ? 1 : 2;
                    break;
                }
            return Optional.of(new ElementSnapshot(
                ProcessorButtonState.of(paused, gym.getGeneration() > 0).wireValue(), conflict));
        }
    }

}
