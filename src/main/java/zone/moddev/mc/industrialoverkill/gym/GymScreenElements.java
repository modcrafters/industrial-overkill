package zone.moddev.mc.industrialoverkill.gym;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.xml.namespace.QName;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.neoforged.fml.ModList;

import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.gui.load.ScreenAttribute;
import zone.moddev.mc.lib.gui.load.ScreenTag;
import zone.moddev.mc.lib.gui.load.ScreenTags;
import zone.moddev.mc.lib.gui.model.Align;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.ElementSnapshot;
import zone.moddev.mc.lib.gui.model.ProcessorButtonState;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.model.SlotSize;
import zone.moddev.mc.lib.gui.model.containers.BoxElement;
import zone.moddev.mc.lib.gui.model.containers.SelectiveContainer;
import zone.moddev.mc.lib.gui.model.elements.ItemSlotElement;
import zone.moddev.mc.lib.gui.model.elements.ProcessorButtonElement;
import zone.moddev.mc.lib.gui.runtime.ScreenClick;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.sync.ElementStateView;

/** Gym-specific policies using MMDLib's standard slot and control presentation. */
public final class GymScreenElements {
    private static final String NAMESPACE = "https://moddev.zone/industrial-overkill/screen";
    private GymScreenElements() {
    }
    public static void register() {
        ScreenTags.GLOBAL.register(new QName(NAMESPACE, "food-slot"),
            ScreenTag
                .element(FoodSlot.class,
                    xml -> new FoodSlot(xml.common(), Integer.parseInt(xml.attribute("index"))))
                .attribute("index", ScreenAttribute.required("xs:nonNegativeInteger"))
                .group("slots").build());
        ScreenTags.GLOBAL.register(new QName(NAMESPACE, "run-button"),
            ScreenTag.element(RunButton.class, xml -> new RunButton(xml.common())).group("buttons")
                .build());
        ScreenTags.GLOBAL.register(new QName(NAMESPACE, "jei-only"),
            ScreenTag.element(JeiOnly.class, xml -> new JeiOnly(xml.common(), xml.children()))
                .children("widget", 1, -1).build());
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
            var stack = gym.food.getStackInSlot(index());
            if (stack.isEmpty())
                return List.of();
            var animal = gym.training.animal();
            if (animal == null)
                return List.of(Component.translatable("gym.food.no_animal"));
            var recipe = GymAnimalRecipe.find(animal, stack);
            if (recipe == null)
                return List.of(Component.translatable("gym.food.unsuitable"));
            int intensity = gym.getIntensity();
            var format = NumberFormat.getNumberInstance(Locale.ROOT);
            format.setMaximumFractionDigits(2);
            long total = (long) recipe.energyPerTick() * recipe.foodDurationTicks();
            return List.of(
                Component.translatable("gym.food.intensity", intensity),
                Component.translatable("gym.food.power",
                    format.format(recipe.energyPerTick() * intensity)),
                Component.translatable("gym.food.duration",
                    format.format(recipe.foodDurationTicks() / (20.0 * intensity))),
                Component.translatable("gym.food.total_energy", format.format(total)));
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

    /** Keep the same definition on both sides; JEI links are client-only interactions. */
    public static final class JeiOnly extends BoxElement implements SelectiveContainer {
        public JeiOnly(Common common, List<Element> children) {
            super(common, true, 0, 0, Align.CENTER, children);
        }
        @Override
        public boolean childVisible(Element child, ElementStateView state) {
            return ModList.get().isLoaded("jei");
        }
    }
}
