package net.modcrafters.minecraft.industrialoverkill.stew;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.namespace.QName;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.layout.LayoutContext;
import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.load.ScreenAttribute;
import zone.moddev.mc.lib.gui.load.ScreenTag;
import zone.moddev.mc.lib.gui.load.ScreenTags;
import zone.moddev.mc.lib.gui.model.Align;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.ElementSnapshot;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.model.SlotSize;
import zone.moddev.mc.lib.gui.model.containers.BoxElement;
import zone.moddev.mc.lib.gui.model.containers.SelectiveContainer;
import zone.moddev.mc.lib.gui.model.elements.ButtonControl;
import zone.moddev.mc.lib.gui.model.elements.SpriteElement;
import zone.moddev.mc.lib.gui.model.elements.ItemSlotElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.runtime.ScreenClick;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.sync.ElementStateView;
import zone.moddev.mc.lib.gui.sync.ElementSync;
import zone.moddev.mc.lib.gui.sync.SynchronizedElement;

/** Common-side UI models. The server owns choices, stock edits and native slot visibility. */
public final class RockStewScreen {
    public static final ResourceLocation RECIPE_DRAWER = RockStewRecipes.id("rock_stew_recipes");
    public static final String RECIPE_DRAWER_ID = "rock_stew_recipes";
    private static final String NS = "https://moddev.zone/industrial-overkill/screen";
    private RockStewScreen() {}
    public static void register() {
        zone.moddev.mc.lib.gui.drawer.DrawerRegistry.GLOBAL.register(RECIPE_DRAWER, context ->
            context.screen().id().equals(RockStewRecipes.id("rock_stew_factory"))
                ? List.of(new zone.moddev.mc.lib.gui.drawer.DrawerDefinition(RECIPE_DRAWER, RECIPE_DRAWER_ID,
                    zone.moddev.mc.lib.gui.drawer.DrawerPlacement.top(zone.moddev.mc.lib.gui.model.Edge.LEFT, 50)))
                : List.of());
        ScreenTags.GLOBAL.register(new QName(NS, "stew-line"), ScreenTag.element(Line.class,
            x -> new Line(x.common())).build());
        ScreenTags.GLOBAL.register(new QName(NS, "stew-control"), ScreenTag.element(Control.class,
            x -> new Control(x.common(), x.attribute("kind"), Integer.parseInt(x.attribute("index"))))
            .attribute("kind", ScreenAttribute.required("xs:string"))
            .attribute("index", ScreenAttribute.defaulted("xs:integer", "0")).group("buttons").build());
        ScreenTags.GLOBAL.register(new QName(NS, "stew-display"), ScreenTag.element(Display.class,
            x -> new Display(x.common(), x.attribute("kind"), Integer.parseInt(x.attribute("index"))))
            .attribute("kind", ScreenAttribute.required("xs:string"))
            .attribute("index", ScreenAttribute.defaulted("xs:integer", "0")).build());
    }

    /** The whole selected route, with at least five columns and unused processors visibly disabled. */
    public static final class Line extends BoxElement implements SelectiveContainer, SynchronizedElement {
        public static final ElementSync<Integer> COUNT = ElementSync.field(RockStewRecipes.id("line_length"),
            ByteBufCodecs.VAR_INT.cast(), 5, e -> 5).sample(s -> RockStewProcessor.from(s.machine()).displaySize()).validate((e, count) -> count >= 1 && count <= RockStewProcessor.SLOTS)
            .immutable().affectsLayout().build();
        public Line(Common common) { super(common, false, 0, 4, Align.CENTER, columns(common.line())); }
        private static List<Element> columns(int sourceLine) {
            var columns = new ArrayList<Element>();
            var appearance = SlotElement.Appearance.standard(
                SlotSize.NORMAL, Optional.empty(), Optional.empty());
            for (int i = 0; i < RockStewProcessor.SLOTS; i++) {
                columns.add(new BoxElement(named("stage_column_" + i, sourceLine), true, 0, 3, Align.CENTER, List.of(
                    new ItemSlotElement(named("stage_item_" + i, sourceLine), i,
                        SlotKind.NORMAL, appearance),
                    new Display(named("stage_processor_" + i, sourceLine), "operation", i),
                    new Display(named("stage_progress_" + i, sourceLine), "progress", i),
                    new Control(named("stage_stock_" + i, sourceLine), "stock", i))));
            }
            return List.copyOf(columns);
        }
        private static Common named(String id, int sourceLine) {
            return new Common(Optional.of(id), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Common.Clicks.NONE, Optional.empty(), Optional.empty(), List.of(), 0, 0, sourceLine);
        }
        private int count(TextMeasure text) {
            return text instanceof LayoutContext context ? context.state().get(this, COUNT) : 5;
        }
        private int columnWidth(TextMeasure text) {
            return children().stream().mapToInt(child -> child.measure(text)[0]).max().orElse(20);
        }
        @Override public int[] measure(TextMeasure text) {
            int count = count(text);
            int height = children().stream().limit(count).mapToInt(child -> child.measure(text)[1]).max().orElse(0);
            return new int[]{count * columnWidth(text) + (count - 1) * spacing(), height};
        }
        @Override public java.util.Map<Element, zone.moddev.mc.lib.gui.layout.Rect> childLayout(
            zone.moddev.mc.lib.gui.layout.Rect bounds, TextMeasure text) {
            var positions = new java.util.IdentityHashMap<Element, zone.moddev.mc.lib.gui.layout.Rect>();
            int width = columnWidth(text), count = count(text);
            for (int i = 0; i < children().size(); i++) {
                // Keep native slot identities fixed; only selected-route columns participate.
                positions.put(children().get(i), new zone.moddev.mc.lib.gui.layout.Rect(
                    i < count ? i * (width + spacing()) : 0, 0, width, bounds.height()));
            }
            return positions;
        }
        @Override public List<? extends ElementAction<?>> actions() { return List.of(); }
        @Override public List<ElementSync<?>> synchronizedValues() { return List.of(COUNT); }
        @Override public boolean childVisible(Element child, ElementStateView state) {
            int index = children().indexOf(child);
            return index >= 0 && index < state.get(this, COUNT);
        }
    }

    public record Selection(ResourceLocation source, ItemStack destination) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Selection> CODEC = StreamCodec.of((buffer, value) -> {
            buffer.writeResourceLocation(value.source());
            ItemStack.STREAM_CODEC.encode(buffer, value.destination());
        }, buffer -> new Selection(buffer.readResourceLocation(), ItemStack.STREAM_CODEC.decode(buffer)));
    }
    public static final ElementAction<Selection> SELECT = new ElementAction<>(RockStewRecipes.id("select_path"),
        Selection.CODEC, 4096, (context, selection) -> RockStewProcessor.from(context.menu().blockEntity)
            .select(selection.source(), selection.destination()));

    public static final class Control implements ButtonControl, SynchronizedElement {
        public static final ElementSync<Boolean> ENABLED = ElementSync.field(RockStewRecipes.id("control_enabled"),
            ByteBufCodecs.BOOL.cast(), 1, element -> false)
            .sample(sample -> {
                var control = (Control) sample.element();
                var processor = RockStewProcessor.from(sample.machine());
                return processor.route() != null && control.index < processor.route().steps().size();
            }).immutable().build();
        @Override public List<ElementSync<?>> synchronizedValues() { return List.of(ENABLED); }
        @Override public boolean enabled(ElementStateView state) { return state.get(this, ENABLED); }
        private final Common common;
        public final String kind;
        public final int index;
        public Control(Common common, String kind, int index) {
            if (!kind.equals("stock")) throw new IllegalArgumentException("Unknown Rock Stew control: " + kind);
            this.common = common; this.kind = kind; this.index = index;
        }
        @Override public String toString() { return "StewControl[" + common + "," + kind + "," + index + "]"; }
        public Common common() { return common; }
        @Override public boolean interactive() { return true; }
        public boolean flat() { return false; }
        public Optional<Text> text() { return Optional.empty(); }
        public Optional<Text> labelFor(Object value) { return Optional.empty(); }
        public Optional<ResourceLocation> iconFor(Object value) { return Optional.empty(); }
        public int[] measure(TextMeasure text) { return new int[]{20, 20}; }
        public Optional<ElementSnapshot> snapshot(ScreenHost host) {
            var p = RockStewProcessor.from(host);
            return Optional.of(new ElementSnapshot(p.stockMode(index), 64));
        }
        public boolean interact(ScreenClick click) {
            if (click.button() != 0 || click.option() != 0) return true;
            var p = RockStewProcessor.from(click.menu().blockEntity);
            p.stock(index);
            return true;
        }
        public List<Component> tooltip(ScreenHost host, ItemStack carried) {
            var p = RockStewProcessor.from(host);
            var rows = new ArrayList<Component>();
            if (p.displayRoute() == null || index >= p.displayRoute().steps().size())
                return List.of(Component.translatable("stew.disabled"));
            String current = switch (p.stockMode(index)) { case 1 -> "one"; case 2 -> "stack"; default -> "none"; };
            rows.add(Component.translatable("stew.stock.label." + current));
            if (!current.equals("stack")) {
                String description = current.equals("one") && index == p.displayRoute().steps().size() - 1 ? "final_run" : current;
                rows.add(Component.translatable("stew.stock." + description).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            return rows;
        }
    }

    /** Unframed recipe-opening processor glyphs and their separate thin progress strip. */
    public static final class Display extends SpriteElement {
        public final String kind;
        public final int index;
        public Display(Common common, String kind, int index) {
            super(common, ResourceLocation.parse("mmdlib:progress_arrow"), 20, kind.equals("progress") ? 3 : 20);
            this.kind = kind; this.index = index;
        }
        @Override public String toString() { return super.toString() + "[" + kind + "," + index + "]"; }
        public Optional<ElementSnapshot> snapshot(ScreenHost host) {
            var p = RockStewProcessor.from(host);
            var chain = p.chain();
            var shown = p.displayRoute();
            if (shown == null) return Optional.of(new ElementSnapshot(kind.equals("progress") ? 0 : index == 0 ? new ItemStack(Items.COBBLESTONE) : ItemStack.EMPTY, 1));
            if (index >= shown.steps().size()) return Optional.of(new ElementSnapshot(kind.equals("progress") ? 0 : ItemStack.EMPTY, 1));
            if (kind.equals("progress")) return Optional.of(chain == null
                ? new ElementSnapshot(0, shown.steps().get(index).recipe().time())
                : new ElementSnapshot(chain.cycle(index).progress(), chain.cycle(index).plan().map(plan -> plan.time()).orElse(1)));
            String operation = shown.steps().get(index).kind();
            if (operation.equals("generate")) return Optional.of(new ElementSnapshot(shown.steps().get(index).product().copyWithCount(1), 1));
            return Optional.of(new ElementSnapshot(new ItemStack(switch (operation) {
                case "grind" -> Items.GRINDSTONE;
                case "smelt" -> Items.FURNACE;
                case "craft" -> Items.CRAFTING_TABLE;
                default -> Items.COBBLESTONE;
            }), 1));
        }
        public List<Component> tooltip(ScreenHost host, ItemStack carried) {
            var p = RockStewProcessor.from(host);
            var rows = new ArrayList<Component>();
            var shown = p.displayRoute();
            if (shown == null && index == 0) return List.of(Component.translatable("stew.open_tree"));
            if (shown == null || index >= shown.steps().size()) return List.of(Component.translatable("stew.disabled"));
            var binding = shown.steps().get(index);
            rows.add(binding.product().getHoverName().copy().withStyle(net.minecraft.ChatFormatting.AQUA));
            if (p.outputBlocked(index)) rows.add(Component.translatable("stew.output_blocked"));
            long lava = binding.recipe().inputs().getOrDefault("lava", List.of()).stream()
                .filter(i -> i instanceof zone.moddev.mc.lib.machine.ingredient.MachineIngredient.FluidPart fluid && fluid.consumed())
                .mapToLong(i -> i.amount()).sum();
            if (lava > 0) rows.add(Component.translatable("stew.lava_cost", lava).withStyle(net.minecraft.ChatFormatting.GOLD));
            long energy = binding.recipe().inputs().values().stream().flatMap(List::stream)
                .filter(i -> i instanceof zone.moddev.mc.lib.machine.ingredient.MachineIngredient.EnergyPart)
                .mapToLong(i -> i.amount()).sum();
            String perTick = java.math.BigDecimal.valueOf(energy)
                .divide(java.math.BigDecimal.valueOf(binding.recipe().time()), 2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
            rows.add(Component.translatable("stew.cost", perTick, energy).withStyle(net.minecraft.ChatFormatting.RED));
            return rows;
        }
        @Override public List<? extends ElementAction<?>> actions() {
            return kind.equals("operation") ? List.of(SELECT) : List.of();
        }
    }
}
