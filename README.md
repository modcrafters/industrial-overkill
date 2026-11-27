# Industrial Overkill

NeoForge 1.21.1 / Java 21 mod using MMDLib. The entry point is
`net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill`; the mod ID is
`industrial_overkill`. Machines include **Animal Gym 2.0**, **Critter Courier**, and
**Rock Stew Factory**, **Agri-Brain**, and **Ranch Controller**.

This is a development version. MMDLib is a required, separate mod; JEI is optional.

The XML machines use MMDLib's inherited machine features, stored processors,
XML actions, conditional shapes, recipe chains, and resource reservation
restoration. The published MMDLib dependency is pinned in `gradle.properties`.

## Getting started

Install Java 21, clone this repository, and build against the published MMDLib pin.

```sh
git clone https://github.com/modcrafters/industrial-overkill.git
cd industrial-overkill
./gradlew build -PmmdlibUseLocal=false
```

On Windows, use `gradlew.bat` in place of `./gradlew`. `build` includes the server
GameTests; output jars are written to `build/libs/`. Use `./gradlew runClient` for
a development client. The optional local MMDLib workflow is described below.

## Agri-Brain foundation

Craft Agri-Brain with iron ingots in the four corners, an iron hoe at the top,
a composter between two wheat seeds in the middle, and redstone dust at the bottom.
The mod's tab is defined in `src/main/resources/mmdlib/creative_tab/machines.xml`;
`include-mod-items="true"` automatically includes all of the mod's registered
items, sorted by ID. `<exclude tag="industrial_overkill:internal_items"/>` hides
recovery items and legacy upgrade aliases. Maintain that list in
`src/main/resources/data/industrial_overkill/tags/item/internal_items.json`;
new machines need no manual tab entry.
Shared Range, Gearbox and Capacitor Upgrades are in the **MMDLib** creative tab.
It stores 100,000 FE and accepts up to 1,000 FE/t before capacitor upgrades. Its
first processor follows a snake path through the field:
left to right on the first row, right to left on the next, alternating farther
behind the machine. Planting, harvesting and work particles follow the same path.
It visits every position and loops
after covering the whole area. On each visit it tills dirt or grass blocks one
block below the displayed crop layer into farmland, provided there is air above.
Covered soil, vegetation, existing farmland and other blocks are left alone.
Insert a hoe in the main screen's Hoe input slot to enable tilling. The slot holds
one item and accepts `minecraft:hoes` through both the menu and automation.
The machine scans without a hoe but cannot till. Successful tills alternate
between free and normal tool wear (one durability per two tills for an ordinary
hoe); Unbreaking applies on the charged uses. Skips and cancelled actions cost
nothing. The wear counter survives saves and tool swaps, and a broken hoe leaves
the slot empty. Tool names, enchantments and other components are retained.

Four seed inputs form a 2×2 grid, numbered left to right, top to bottom. A third
row places the hoe under the left column and the 18×18 planting-mode button
under the right column, forming a compact 2×3 grid. The empty hoe slot shows a
muted hoe icon and accepts only items tagged `minecraft:hoes`, including through
automation. The button's 16×16 pixel-art
icon uses native pixel art with a padded 12×12 drawing area, without resizing.
It changes with the mode (sprouting seed, cycling arrows, or planted rows);
hovering shows the current mode name and instructions:

- **First found:** use the first non-empty seed slot until it runs out.
- **One at a time:** plant one from each slot in order, skipping empty slots.
  A green border marks the next populated slot. Only a successful planting advances it.
- **Rows:** assign each work-area row to a slot, repeating every four rows.
  Rows 1/5/9 use slot 1, rows 2/6/10 use slot 2, and so on. Rows start nearest
  the machine. An empty assigned slot leaves that row unplanted, with no fallback.

The mode and round-robin cursor survive saves. Changing mode resets that cursor
to slot 1. The screen uses MMDLib's ordinary inventories: slot counts come from
storage, and the next-seed indicator uses the shared inventory selection binding.
The planting selector uses MMDLib's shared `<cycle-button>`: mode values, icons,
labels, help and selection actions live in the machine XML. Its processor actions
change the saved mode and reset the seed cursor; no custom UI element is needed.
Inputs accept `industrial_overkill:agri_brain_seeds`, which includes
the common seeds tag, vanilla farmland seeds, carrots and potatoes. On each scan
visit, the machine evaluates fertilizer, then checks for harvest, tills, and plants in the empty crop space
above farmland. Planting existing farmland needs no hoe. Each successful placement
consumes one seed, honors normal placement/cancellation rules, and preserves
immature crops. Planting failure consumes nothing and does not advance the cursor.

Mature crops are harvested into nine output slots arranged in a 3×3 grid.
Seed drops refill matching seed input stacks first, then empty seed inputs;
overflow and other drops go to output. All drops must fit before a crop is
removed, so full storage leaves it intact until a later pass. Replanting then
uses the selected planting mode, including newly collected seeds. Harvesting
needs no hoe and uses empty-hand loot. It respects block-break cancellation.
Wheat-style crops (including carrots, potatoes and beetroot), mature torchflowers
and pitcher plants are supported, as are melon and pumpkin fruits; their stems
stay in place. Other plants are left alone.

### Fertilizer and sewage

Pipe surplus seeds from Agri-Brain's Harvest inventory to a Fertilizer Maker.
Honest Sewer collects 25 mB per live animal in range every 30 seconds without
power or every 5 seconds at 2 FE per tick. Each powered tick advances the current
batch six times as fast. The machine draws energy when animals are in range and
the tank has room. Raw sewage can go directly to the Fertilizer Maker or into a
Sewage Filter.
The Sewage Filter selects its recipe from the fluid in its input tank. Sewage
converts 250 mB to 200 mB of filtered sewage, with an independent 1.25% chance
for each listed seed and scrap item. Filtered sewage converts 200 mB to 150 mB
of water and one filter cake. Both recipes take 10 work seconds and 1,000 FE.
Drain the shared output tank before switching to a recipe with a different
fluid output. Data packs can override or add `industrial_overkill:sewage_filter`
recipes using the `input`, `energy`, `liquid`, and `solids` recipe groups.

| Fertilizer Maker input | Yield | Work time |
| --- | --- | --- |
| 4 seeds + 100 mB water | 2 regular doses | 40 seconds |
| 4 seeds + 100 mB sewage | 4 regular doses and 50 mB water | 10 seconds |
| 4 seeds + 100 mB filtered sewage | 2 filtered doses and 50 mB water | 10 seconds |
| 1 filter cake + 8 seeds | 2 slow-release pellets | 20 seconds |

A filter cake can replace four seeds in any liquid recipe. In Liquid mode, the
maker selects its recipe from its shared input tank. Drain the input before
switching liquids. Sewage batches wait for 50 mB of room in the water output
tank. Filter and maker work cost 5 FE per work tick. Regular fertilizer advances
one growth stage and filtered fertilizer advances two. Pellets advance two at
planting, followed by one each after 30 and 60 seconds while that crop continues
growing.

On existing makers, stored raw sewage stays in the input and stored water moves
to the output. Filtered sewage from the removed tank is preserved in the
machine's recovery data and is dropped in a recovery capsule when it is broken.

All five fertilizer slots share one colour and side configuration. The left
column takes regular fertilizer, filtered fertilizer, and slow-release pellets
from top to bottom. The two slots above the mode button both accept bone
meal and anything added to the `industrial_overkill:agri_brain_alternative_fertilizers`
item tag. Each item supplies one stage of growth for the regular fertilizer cost.
When a strategy calls for a one-stage dose, Agri-Brain tries regular fertilizer
first, then the upper and lower alternative slots in order.

**Off** skips doses.
**Smart** applies pellets at planting, lets active pellets work, and prefers
filtered fertilizer when two stages remain. Otherwise, it prefers regular
fertilizer and saves filtered doses for crops that can use both stages.
**Finish Only** applies a full dose only when it can mature the crop.
**Pellets Only** applies pellets at planting.

The drone takes six work ticks to travel between plots, then hovers for five
work ticks to check maturity plus five for each applicable fertilize, harvest,
and replant phase. At the end of the hover it applies the finishing dose,
checks actual maturity, harvests, and replants on one server tick. A pellet for
the replacement crop is part of the replant phase. A full visit takes 20 work
ticks of hover time. Fertilizer applications cost 100 FE apiece.

Scanning costs **5 FE per work tick**, including empty or unsuitable blocks.
Gearboxes increase work ticks per game tick, up to **10 FE per game tick** with eight.
Each successful planting costs another **100 FE**, tilling **200 FE**, and
harvesting **100 FE**. Combined actions add their costs: harvest and replant
cost 200 FE plus scanning; till and plant cost 300 FE plus scanning. Cancelled
or blocked actions cost only scanning. Actions without enough energy leave
their blocks and items untouched and are retried on later passes. Below 5 FE,
the scan countdown and particles stop until power returns; pause consumes nothing.
Consumption is reported through the shared energy statistics.

Tilling uses the normal hoe interaction and respects NeoForge tool/placement
cancellation. Fill the **4,000 mB water tank** with a water bucket at the gauge
or pipe water into a configured input face. The drone restores each visited
farmland block to moisture level 7, including soil it tills on the same visit.
For starting levels 0 through 7, the cost is **100, 86, 71, 57, 43, 29, 14,
and 0 mB**, respectively. The more water the drone applies, the wider the
splash and the more droplets it produces. Small top-ups make a soft sprinkle
at the soil. Larger doses make a fuller, louder splash.
If the tank cannot cover the full cost, the drone skips watering and continues
farming. Inside the powered work area, each moisture level lasts about eight
times longer on average. Bare, fully dry farmland also takes longer to revert
to dirt. Rain and nearby water hydrate the soil normally. Moisture retention
consumes no tank water.
Pause freezes its position and countdown, which survive
save/reload. Changing range restarts the scan at the nearest row's left corner.

The button beside Pause toggles a green outline of a 3×3 crop area
directly behind the machine, at controller height. Visibility is local to
the player and remains on after closing the screen. Breaking or unloading the
machine, changing dimensions, or disconnecting clears its outline.

The quadcopter projects a green scan line from its lens onto the ground beneath
each crop. The line points front-to-back relative to the controller and sweeps
in the drone's direction of travel, with small sparks along the line. The front
display, indicator lights, and top ring glow while the drone is active and go
dark when it pauses or loses power. The drone leans into each flight and levels
out as it hovers over a block. It keeps the same altitude over irrigation
water and turns into each new row. After the last block, it switches off the
scanner and faces the first block as it flies diagonally back. The crossing
takes 40 work ticks on a 3×3 field and 80 on a 5×5 field. Travel uses the
usual 5 FE per work tick and follows gearbox speed. Pausing or losing power
freezes the work route. The drone lands on the controller and its rotors coast
to a stop over 16 ticks. From rest, the rotors spin up for 8 ticks before liftoff.
Their quiet hum follows the drone, rising in pitch and volume with rotor speed and
fading to silence on the dock.
The roof has a flush landing target and four brass contacts. The dock reserves
a full half-block of collision above the casing, even while the drone is in the field.
Its scanner stays off during docking and takeoff. The machine XML sets the scanner color
with `work-color` and the area outline with `range-color`.

MMDLib's Range Upgrade adds one to the radius: 5×5 with one, 7×7 with two, up to
21×21 with nine. The near edge stays fixed behind the controller. Adding or
removing an upgrade updates the open screen's area snapshot and visible outline,
even while paused. Craft one with `IGI / RCR / IGI`: four iron ingots, two glass,
two redstone and a compass. It is also available in the MMDLib creative tab. Items can
stack in your inventory, but each installed upgrade occupies its own slot.

The Upgrades drawer is the first right-side tab. Its two top sockets show gearbox
and capacitor pixel art; nine general slots form a 3×3 grid below. There is no
machine image, heading or coloured resource-group highlight. All eleven slots
are manual. The gearbox and capacitor sockets each hold eight; the nine general slots hold one item each.
Their storage uses `automation="false"`, so
Upgrades does not appear in the coloured resource selectors or side configuration.
The powered-machine base supplies the shared sockets and default drawer to Rock
Stew Factory automatically. Agri-Brain overrides the `upgrades` screen fill with
`<machine-upgrades storage="upgrades"/>`. Agri-Brain declares
nine extra slots; MMDLib supplies separate `mmdlib_gearbox` and `mmdlib_capacitor`
inventories from the processor and energy capabilities, including their icons,
tooltips, filters and eight-item limits. Old eleven-slot saves migrate their
installed components and range items into the new layout on load.
Their item tags are
`mmdlib:gearbox_upgrades`,
`mmdlib:capacitor_upgrades` and
`mmdlib:range_upgrades`, respectively. The gearbox tag accepts
`mmdlib:gearbox_upgrade` (plus the old IO ID for saved stacks); the capacitor tag accepts
`mmdlib:capacitor_upgrade`, and the
general tag accepts `mmdlib:range_upgrade` and the old IO ID for saved stacks.
XML binds `range-storage="upgrades"`; MMDLib counts its nine occupied sockets and applies
range to the processor's base working area. Agri-Brain uses the shared area's
indexed block traversal and row helper for scanning and planting modes, keeping
the display and actual work positions in agreement.

Install up to **eight Gearbox Upgrades** in the gearbox socket. Each adds
**12.5% processor speed and FE usage rate**: one gives 1.125×, four give 1.5×,
and eight give 2×. At eight the scan visits a block every 10 game ticks instead
of 20. Harvesting, tilling and planting all follow the shared MMDLib processor
clock. XML binds it with `<upgrades gearbox-slot="upgrades/4"/>`; there is no
Agri-Brain-specific speed calculation. Fractional work is retained across pauses,
gearbox swaps and save/reload. Individual actions retain their 100/200/100 FE
prices; more work per second uses proportionally more FE per second.
The gearbox has no durability and is never consumed. Craft it with `INI / RPR / INI`: four iron
ingots, two iron nuggets, two redstone and a piston. It is also in the MMDLib creative tab.

Install up to **eight Capacitor Upgrades** in the capacitor socket. Each adds
**12.5% base energy capacity and input/output rate**. Agri-Brain reaches
200,000 FE storage and 2,000 FE/t input with eight; its zero output rate stays zero.
Changes apply immediately, including while paused, without creating energy.
Removing capacitors clamps excess stored FE to the reduced capacity. Upgraded
capacity and stored energy survive reloads. Craft one with `ICI / RQR / ICI`:
four iron ingots, two copper ingots, two redstone and a quartz. It is also in
the MMDLib creative tab. XML binds it with `capacitor-slot="upgrades/5"` on `<upgrades>`.

Upgrades are permanent and never wear out. MMDLib's `machine-upgrades`
element supplies this layout; mods can replace the component icons in XML.

## Ranch Controller

The Ranch Controller tends a single-species herd in a 5×5×3 area immediately
behind it. Use the area button to see its reach or install Range Upgrades to
extend it. Leave space above the controller for its half-block scanner mount.
At the start of each cycle it lists the animals in range and scans them one at a
time. Its antenna aims at each animal and sends a particle ray toward it.
With eight Gearbox Upgrades the antenna holds on each animal for one second,
then takes a quarter-second to turn to its next target. At base speed these
times are two seconds and half a second. A breeding turn focuses on each parent
before feeding the pair. Most successful actions cost 100 FE.

- **Milk:** Load an empty bucket or a milk-compatible container in the middle
  tool slot to collect 1,000 mB from an adult cow or mooshroom. Each animal has
  a 30-second cooldown. The controller tops up partly filled milk containers
  in the 3×3 output before moving fresh containers there. It keeps the last
  empty vanilla bucket as a reusable tool and sends milk that could not go into
  containers to the 10,000 mB output tank. Milk can be drawn or piped out of the
  tank.

Install these add-ons in the regular Upgrades grid alongside Range Upgrades.
**Adults target** appears below the grid with Pair Pressure or Livestock To Go
installed. It defaults to 8 and can be set from 0 to 32 for the whole work area.

- **Pair Pressure** feeds two nearby adults of the same breedable species so
  they can mate when the herd is below target. It uses one suitable food and
  100 FE per animal. Young animals count toward the target as future adults.
- **Snackcelerator** uses suitable food from the 2×3 input to speed up young
  animals' growth, just like hand feeding.
- **Baa-rber Shop** uses shears from the top tool slot to collect 1–3 wool of
  the sheep's color and applies ordinary tool wear. A sheep waits until the
  output has room for its full possible yield.
- **Livestock To Go** packs surplus adults into empty Critter Couriers from the
  bottom tool slot when the adult count exceeds the target. Occupied Couriers
  go to the 3×3 item output. Packing waits if the item output is full.

The feed input opens with Pair Pressure or Snackcelerator. The shears and
Courier slots open with Baa-rber Shop and Livestock To Go, respectively.
Removing an add-on immediately blocks new items from its input through the menu
and automation. Locked tool slots dim and show **Disabled** on hover. You can
still take stored items out by hand.

## Rock Stew Factory

A one-block FE-powered stone generator with a grey industrial frame, cyan
Resources-category trim and a cobblestone display. Craft it with
`ICI / BFB / IRI`: four iron ingots, cobblestone, two empty buckets, a furnace,
and redstone. It is also in the Industrial Overkill creative tab.

Supply FE, water and lava. The two tanks each hold **4,000 mB** and accept buckets
through their vertical gauges on the left or fluids through configured input faces. Every
source requires **at least 1,000 mB of each fluid before starting**. Source consumption
comes out of that amount: if either tank falls below 1,000 mB, generation waits for refill.
Downstream furnaces need only their consumed lava and can drain the tank to zero. Fluid tanks and the
energy gauge use the standard three-slot height.

A new machine waits for a selected production path. Click any active processor sprite
to open JEI when available, or the recipes drawer otherwise. The vanilla recipe-book
side tab always toggles the drawer, keeping the processing screen in place.
Use the top arrows to choose a source and the bottom
arrows or mouse wheel to page through its paths.
Each recipe occupies its own row, repeating the source and intermediate steps:
`[water] [lava] -> source -> grinding/smelting/crafting products`.
Drawer and JEI path tanks show a full ghosted 1000 ml background, with source-cycle
consumption overlaid from the bottom at full opacity. Their tooltips show only the
fluid and consumed amount; main-screen tanks explain the 1000 ml rock reserve.
In JEI, use the standard **+** button to apply a path to the open Rock Stew.
The machine's front displays the selected final product, even while idle or blocked.
No product appears until a path is selected. The display reads the synchronized
`$processors.rock_stew.display_product` variable, independently of the inventory or open GUI.
The front preview is declared with MMDLib's `<item-display>` in the machine XML;
Rock Stew needs no custom block-entity renderer.
Five independent front lights show the current cycle of processors 1 through 5.
The visual variables `$processors.rock_stew.progress_lights_1` through
`$processors.rock_stew.progress_lights_5` select eight-frame strips: 0 for idle or
unused stages, and 1–7 for seven equal portions of active cycle progress. Pauses,
redstone holds and energy stalls retain each stage's frame. Completion clears it
or starts at frame 1 when another cycle begins immediately; changing the path or
resetting clears all five. The original `progress_lights` binding retains its
three-band generator behavior for older resource packs.
MMDLib `<face-overlay>` regions layer the lights and independent front/top activity
glows over the static casing without replacing faces or requiring a custom renderer.
In the drawer, click a row to request its final product and return to processing. Generation uses a
blue-grey slot background, grinding a light stone-grey, smelting a warm shade,
and crafting a green-grey. Hover an item to see its operation.

The drawer fills the main panel height with the standard 4 px top/bottom insets and preserves native GUI icon size. It opens beside the machine where space
permits and overlaps the panel on narrower screens. MMDLib's `RecipePresentation`
supplies both the drawer and JEI with the same ingredient positions, roles, fluid
requirements, tooltips and artwork; JEI supplies its native ingredient slots.

Recipe tanks show a full ghosted 1,000 ml background with consumed fluid drawn
solid from the bottom on the same scale. For example, stone overlays 100 ml lava;
obsidian overlays the whole 1,000 ml. Tooltips report only the consumed amount.
These amounts are per source-generation cycle; downstream crafting batches may
require several source cycles. Rock Stew discovers deterministic grinding, ordinary smelting
and single-material shaped/shapeless crafting, including multi-item batches:

`Cobblestone -> Gravel -> Sand -> Glass -> Glass Panes`

The XML declares one `<io:stew-line/>`; its model generates the repeated stage
columns with stable slot indices and control IDs. Stages run concurrently using shared FE. Each column has an extractable output,
a recipe-opening processor sprite, progress and a stock setting: **None / One Run /
Full Stack**. The controls show a crossed square, one cube, and stacked cubes.
Processors use Minecraft item icons: the selected base product for generation,
a crafting table for crafting, a grindstone for grinding and a furnace for smelting.
Progress bars show activity; processor icons have no custom animation. Unused
processors show a faint dash. Clicking an active-path icon opens recipe selection.
Tooltips show the setting with a short grey explanation for None and One Run.
None keeps no independent stock: existing downstream items can satisfy demand
without the source running. One Run keeps the input quantity for one cycle of
the next processor (six glass for panes); the final stage keeps one output batch.
One Run caps that stage's buffer at this batch size. A ready buffer does not
consume more from the previous processor merely because the final output is
set to Full Stack. It refills as its batch is consumed or extracted.
Choose One Run or Full Stack on the source if you want base material available there. These are refill goals. Processing claims its next batch first; any
surplus remains available to clicks, hoppers and pipes. Blocked outputs stop passing demand upstream; each earlier processor can still refill its own stock target. A six-glass pane recipe
can protect three glass while waiting for the rest. Pausing releases pending
batch protection while retaining reservations and paid progress for active work.

Processor tooltips start with the product name in light blue, followed by
**Output blocked** only if that processor's output is incompatible or lacks room
for a full batch. The energy line is light red: `FE / t (FE / cycle)`, calculated
from the recipe duration (560 FE over 40 ticks is 14 FE/t). The choose-path hint
appears only before selection. Operation, switch-status and tick-count lines are
omitted; progress remains on the bar. Other occupied outputs do not block a
processor with valid inputs and output room.

Available paths have at most **five steps including generation**: one source
plus up to four crafting, grinding or smelting operations. A strict building-block
allowlist applies to sources, intermediate products and final outputs. Buttons,
pressure plates, gears and functional blocks such as furnaces are not included.
Stone building variants, stairs, slabs, walls, glass, sand and gravel remain available.
Ingot exclusions also take precedence over the allowlist. Both the
recipes drawer and JEI use the same filtered graph; the server also rejects
selections outside it.

Configure allowed and excluded **item tags** in the world's
`serverconfig/industrial_overkill-server.toml`:

```toml
[rockStew]
allowedItemTags = ["industrial_overkill:rock_stew_building_blocks"]
excludedItemTags = ["c:ingots", "c:ingots/*"]
```

Entries are namespaced tag IDs, without `#`. An exact entry matches only that
tag; a trailing `/*` matches descendant tag paths, including deeper paths, but
not the parent tag. For example, `c:ingots/*` matches `c:ingots/obsidian` but
not `c:ingots` or `other:ingots/obsidian`. Add entries such as `c:gems` and
`c:gems/*` to exclude more categories, or use `[]` to disable tag exclusions.
Each list replaces its defaults. An empty `allowedItemTags` permits no products;
an empty `excludedItemTags` removes exclusions but still requires allowlist membership.
These are item-tag patterns, not item IDs or
arbitrary regular expressions. Invalid entries are rejected by config validation.

Extend the default allowlist with a datapack file at
`data/industrial_overkill/tags/item/rock_stew_building_blocks.json`:

```json
{
  "replace": false,
  "values": ["your_mod:decorative_stone"]
}
```

Use `"replace": true` to supply your own complete list. Modded building blocks in
the referenced stairs/slabs/walls/fences and common stone/glass tags are included;
other decorative blocks need explicit entries. Merely being placeable does not
make an item eligible. Datapack tag edits take effect on `/reload`.

Reopen the world or restart the server after editing. NeoForge synchronizes the
server setting to joining clients, so the recipes drawer and JEI follow the same
policy. A `defaultconfigs/industrial_overkill-server.toml` file supplies defaults
for newly created worlds. Existing stored products are preserved if a changed
policy invalidates their production path.

The complete selected processing line is visible at once with five columns and
no stage scrolling or paging. Unused output slots and progress tracks fade into
the panel grey, with softly outlined disabled stock buttons. Legacy storage
slots remain intact so items from older, longer paths can still be extracted.
Resource groups use subtle background tints; their outlines appear when the
group controls are highlighted. Recipes with unrelated ingredients, special
behavior, remainders or chance products are excluded. Discovery is bounded and
rebuilt after recipe/tag reload.

Selecting a different path immediately cancels unfinished work, releases its
reservations and resets progress. Stored items and fluids stay in place; already
spent FE is not refunded and canceled work produces no outputs. The new path
starts wherever its own inputs and output space permit, without waiting for all
old products to be removed. Leftovers beyond a shorter path remain extractable.
Selecting the same healthy path preserves its current work and stock settings.
If saved work cannot be restored, clicking a stock setting or selecting a valid
path cancels that unrecoverable work and restarts the admitted path. Stored items,
fluids and spent FE are preserved; the original failed snapshot is kept separately.

Source generation retains its previous time and FE costs:

| Output | Consumed above catalyst floor | Energy | Time |
|---|---|---:|---:|
| Cobblestone | None | 320 FE | 2 s |
| Cobbled Deepslate | None | 560 FE | 2 s |
| Stone, Andesite, Diorite, Granite, Tuff | 100 mB lava | 240 FE | 2 s |
| Deepslate | 200 mB lava | 480 FE | 2 s |
| Mossy Cobblestone | 250 mB water | 320 FE | 2 s |
| Basalt | 250 mB lava | 200 FE | 2 s |
| Blackstone | 200 mB lava | 240 FE | 2 s |
| Calcite | 100 mB lava | 120 FE | 2 s |
| Obsidian | 1,000 mB lava | 8,000 FE | 20 s |

Grinding cobblestone into gravel and gravel into sand costs 320 FE over 40 ticks
per step. Adapted smelting costs 800 FE and **100 mB lava per cycle** over
100 ticks, with no water or minimum lava reserve requirement. Crafting costs 160 FE over
20 ticks. Furnace fuel is shown on its processor and recipe-step tooltips; the
leading recipe tanks continue to show source-generation requirements. Full crafting batches can exceed the selected stock target; their
surplus is retained. The power buffer holds 100,000 FE.

The XML definition owns storage, appearance and the composed screen;
`stew/RockStewProcessor` owns source/path selection, saved bindings and transitions.
Datapack source recipes live in `recipe/rock_stew/`, and grinding recipes in
`recipe/rock_stew_grinding/`. Source recipes use water/lava/energy groups and a
single deterministic `output`; there is no ghost item input. JEI displays source
and grinding recipes with reusable fluid requirements separate from consumed costs.
JEI provides one production-path recipe per row, searchable by its final product.
Reusable fluids are indexed as catalysts and consumed fluid as inputs. When
opened from Rock Stew, the standard JEI + button applies its destination through the same
server validation as the built-in drawer. Source and grinding cost categories remain available.

Save/reload restores active reservations before the first tick. Earlier active
single-source work finishes with its saved plan; old reagent water/lava merges
into the corresponding new tank when it fits. Unknown or incompatible saved
content is retained for recovery rather than discarded. Old output stock remains
extractable. Saved pending selections from the earlier drain-before-switch design
are upgraded to immediate selections, releasing the canceled work's claims. Breaking drops stored
items; stored FE and fluids are lost. Active fluid reservations remain protected
while paused, so finish active work before draining those fluids to relocate.

The focused server runner is `gradlew.bat runGameTestServer -ProckStewOnly`.
The screen runner is `gradlew.bat runRockStewClientSmoke` (JEI production paths); add
`-PwithoutJei` to exercise the built-in fallback panel. Its screenshots are in
`build/rock-stew-client-smoke/screenshots`. JEI has a separate
`runRockStewJeiSmoke` runner. Routine validation is left to CI.

## Animal Gym 2.0

Craft the gym with `ILI / RHR / IPI`: iron ingots, lantern, redstone, hay block and
piston, or take it from the Industrial Overkill creative tab. One item places the
whole machine facing you: **one controller block and two treadmill blocks in a
1x3 footprint**. The rear gantry is part of those models, reaching just under two
blocks high. Leave the three positions immediately above the base clear. The three
physical members rotate together in all four directions. This replaces the earlier
15-member prototype layout; existing prototype machines need to be removed and
placed again to use the compact layout.

Lead or lure an adult cow, pig, sheep, goat, horse, chicken, rabbit, cat, wolf, fox or panda onto the belt. The animal
faces the controller along the belt. Animals that are too large shrink temporarily
to fit within the belt and below the gantry; small animals are never enlarged.
Open any part and put suitable food in the three slots. Cows/sheep/goats eat wheat; pigs eat
carrots, potatoes or beetroot; horses accept wheat, apples, golden carrots, sugar,
hay or golden apples. Chickens eat seeds; rabbits eat carrots, golden carrots or
dandelions. Cats accept raw cod or salmon; dogs/wolves accept their vanilla meat
foods (including cooked meat and rotten flesh). Foxes eat sweet berries and glow berries; pandas eat bamboo.
Cat/wolf/fox/panda food tags also support
data-pack additions. Baby, ridden and riding animals cannot power the gym.

The gym starts automatically. Work **consumes food to generate FE**;
there is no FE input. Configure food and energy access in the side drawer, or put an
FE consumer beside an output-enabled controller face for automatic export. The
belt-facing right side and the top (hanging food) are unavailable for both resources and redstone.
Unavailable faces show only a centered gray X, half the slot size, without a neighbor preview or name. The redstone
drawer can pause generation on an input or emit stored-energy/fullness and working signals.
The buffer holds 32,000 FE. When there is no room for the next work unit, the gym
rests until stored energy falls to **24,000 FE (75%)** before restarting. This
prevents rapid lamp, cow-size, animation and UI switching when a slow consumer
such as the Rock Stew Factory draws less than the gym generates. During the rest,
the screen says **Waiting for energy demand**, the run control stays idle rather
than manually paused, and no food allowance is spent. Export continues normally.
The wait survives save/reload; manual pause and redstone controls still apply.
Datapack recipes with unusually large work units also need enough room for the
entire unit before restarting.
Food can also be inserted/extracted on controller faces. Unsided capability access
is disabled. The standard MMDLib run control pauses or resumes generation.

Default recipes scale output and appetite with animal size. These baseline-food values are at
**intensity 1**, while working continuously with room for generated FE:

| Animals | Generation | One food lasts | Food/minute |
| --- | --- | --- | --- |
| Chickens, rabbits | 2 FE/t | 60 seconds | 1 |
| Cats, foxes | 4 FE/t | 45 seconds | 1.33 |
| Wolves | 6 FE/t | 30 seconds | 2 |
| Pigs, sheep | 8 FE/t | 24 seconds | 2.5 |
| Goats | 10 FE/t | 20 seconds | 3 |
| Cows, mooshrooms | 16 FE/t | 15 seconds | 4 |
| Pandas | 20 FE/t | 12 seconds | 5 |
| Horse family (including donkeys, mules and llamas) | 24 FE/t | 10 seconds | 6 |

Food tiers change the baseline per serving (also at intensity 1):

| Animal | Food | Power | Duration |
| --- | --- | --- | --- |
| Wolf | Raw meat / other wolf-food tag additions | 6 FE/t | 30s |
| Wolf | Cooked meat | 6 FE/t | 45s |
| Wolf | Rotten flesh | 6 FE/t | 12s |
| Horse family | Wheat, apple, sugar | 24 FE/t | 10s |
| Horse family | Hay block | 24 FE/t | 90s |
| Horse family | Golden carrot or golden apple | 30 FE/t | 20s |
| Rabbit | Carrot or dandelion | 2 FE/t | 60s |
| Rabbit | Golden carrot | 3 FE/t | 90s |

A hay block supplies the same total energy as its nine wheat ingredients, with less
refilling. Cooked meat trades cooking effort for longer operation; rotten flesh is
cheap but needs frequent replacement. Golden foods add power and duration. Other
animals keep their existing food rates.

Intensity 2 doubles output and food consumption; intensity 3 triples both. Smaller
animals offer low-maintenance generation, while larger animals provide more power
per gym and require a steadier food supply. Baseline energy per serving rises modestly from
2,400 FE for the smallest animals to 4,800 FE for the largest; food tiers adjust it. All default durations
leave time for the 56-tick eating/refill sequence, even at intensity 3.

Intensity controls power output and food consumption, not animal attributes.
The gym does not accumulate fitness or grant speed, health, strength or jump bonuses.
Old gym attribute bonuses are removed when animals load into the world or a trap;
old fitness scores remain dormant for save compatibility. Unspent food allowance
stays with the animal. Datapacks can override the balance per recipe.

Loose animals are held in place while working without disabling AI. Fitting scales both the
model and hitbox, preserves existing scale modifiers, and restores the original
size whenever work ends, the animal leaves, or the gym unloads. Temporary shrinking
is never stored in animal saves. Pause, insufficient food, full storage, obstruction,
unloading and teardown release loose animals. Animals installed through a Critter Courier
remain contained in their trap through all of those conditions. Breaking any
member dismantles the gym, drops remaining food and awards one machine item for a
survival break. Stored FE is lost. Creative destruction and generic replacement
do not award a machine item. Missing/undecodable saved food is retained in Recovered
Gym Supplies; right-click a gym with that item after restoring the missing content.

The screen places the occupied-trap slot beside the food slots below the live animal
portrait, keeping the main panel compact. The recipe drawer is always available from
its side tab. Clicking the animal portrait opens JEI when installed, otherwise the
drawer. Both viewers share animal cards, accepted foods and power calculations;
drawer rates follow the machine's selected intensity. Food tooltips show power and food duration at the selected intensity,
plus total FE per item for the current animal. Total FE stays constant across intensities.
The right end of that row holds a larger three-light yellow intensity indicator
(click the indicator to cycle 1–3; no button frame).
The inventory toolbar lists Food then Energy on the left, with MMDLib's standard
run control aligned to the right. Food automation accepts input only; food can still
be removed manually. Existing bidirectional food sides become input-only on load.
Animal status, current power output, **Power left: ... FE** and **Time left: 8m 20s** appear beside the
portrait. Remaining power counts food usable by the current animal, the uneaten
serving on the stick and its remaining paid ration, excluding already stored FE.
The total stays constant across intensities and remains visible while resting or
paused; without an eligible animal it is zero. Time appears on its own line below
remaining power to keep the screen compact. It is the remaining
active generation time at the selected intensity (20 ticks per second), summed
from each meal's duration rather than dividing by the current power rate. Pauses,
full-buffer rests and feeding delays extend the elapsed time before food runs out.
Working animals walk/run in place toward the controller while the rubber
belt scrolls backward beneath them. Higher intensity increases the gait and belt
speed. The controller's pole sits opposite the animal, with a longer overhead beam
spanning the cabinet. A wooden bait rod rises 30 degrees toward the animal from
the cow-facing side of that pole.
While working with suitable food available, a small food item hangs from its end
on a tether, rotating once every eight seconds and bobbing 0.08 blocks above/below
its center every four seconds.
The tether follows the food so it stays attached. Running animals track the food
with their heads while keeping their bodies aligned with the belt, including
animals installed in traps. Captured cats and dogs stand and run during work,
even if captured sitting; pausing restores their sitting pose and their original
sit command is preserved when released. Captured foxes also stand and run even when
captured sleeping or sitting; their original resting state returns on pause/release. Captured sitting pandas also
stand and run, with their sitting state preserved in the trap through reload and release.
Eating follows actual ration consumption:
the animal lunges toward the bait, the food disappears at mouth contact, and matching
food crumbs appear for about 1.5 seconds. Its body returns to running immediately
after the lunge. One second after chewing ends, one new serving is removed from the
food inventory and hung on the stick. This reserved serving supplies the next ration;
refilling never grants extra work or charges twice. The timer freezes when work stops.
A reserved serving survives pause/reload and drops with the other supplies on dismantling.
The food display hides when work stops; eating and refilling never move animal hitboxes.
The controller's blue lights, paw display, and overhead lamps glow while the gym works.
The overhead lamp lenses are dark blue when idle.
The overhead lamps also emit level-14 block light. The work lights switch off on pause,
release, exhausted food allowance, full energy storage or a blocked structure.
Three small lights on each side of the beam above the controller show the selected intensity,
even while paused.
The last consumed ration still provides its paid work before the gym stops;
an empty inventory alone does not cancel that allowance. The gym
models are generated with `py -3 scripts/generate_gym_assets.py` (standard library
only). The controller is a full block with generator casing textures and the gym's
paw-and-meter front decal. The assembly has yellow-black belt edges, rear uprights,
a sloped end support and two pairs of blue overhead lamps. The item preview shows the whole gym.
The attachment atlas is `textures/atlas/gym_attachments.png`. The square upright shares a
single panel tile on its four sides. The far upright, slanted support, and beam each share
two tiles across opposite faces. Small fittings crop from common material tiles at 32 pixels
per block, rounded to the nearest pixel with a one-pixel minimum. Tile positions are listed
in `scripts/assets/gym_attachment_faces.json`. Leave the surrounding edge padding in place
when editing tiles so adjacent colors stay out of their mipmaps. The block atlas also loads
individual sprites from the sheet for the animated belt, particles, and shared materials.
Both gantry supports have bolted mounting feet and reinforced beam connections;
the outer angled support also has a knee collar.
Asset generation checks for overlapping coplanar faces, including rotated parts
and the assembled gym. Run the geometry regression checks with
`py -3 -m unittest discover -s scripts -p test_model_geometry.py`.

## JEI integration

With JEI installed, open the gym item's uses to browse **Animal Gym**.
Each page shows an adult animal running in place (rabbits hop), accepted foods (choose any one), FE/t,
and seconds per food. The top-right **Intensity: 1** button cycles through **1 → 2 → 3 → 1** and updates all visible cards
together: power scales up, food duration scales down, and the preview gait speeds up.
The animation uses the same stride speeds as the gym, without ticking AI or spawning
preview animals into the world. This only changes the JEI
preview, not any running gym. Compact cards fit three per page at the reference
GUI scale; smaller windows or larger GUI scales can show fewer. Foods support normal JEI recipe/usage lookup; spawn eggs identify
animals for lookup without being consumed. Animal-tag recipes expand into one page
per matching animal, including modded animals. Each animal has one card: the right side groups
foods by identical power and duration, with a compact `FE/t / seconds` line above each food row.
JEI uses a fixed height per category, not variable heights per recipe. Default cards still fit
three per page at the reference GUI scale; datapacks with more than three distinct food tiers
increase the height of all cards. Large food groups cycle through alternatives in their row.
Each food's tooltip repeats its power
and duration at the selected intensity, plus total FE generated per food item. Total energy
is the base FE/t multiplied by the food duration in ticks and stays the same at every intensity.
Disabled recipes, empty food tags and foods
shadowed by a higher-priority matching recipe are omitted, so JEI reflects what the
gym will actually use. JEI rebuilds the category from synchronized recipes after `/reload`.
Already eaten meals in a running gym keep their paid allowance; JEI shows current
recipe values for future meals.

JEI is optional and is not bundled in the mod jar. Development runs include it by
default; pass `-PwithoutJei` to test without it. `runGymJeiClientSmoke` checks the real
JEI category, food lookup, gym catalyst, animal-tag expansion and recipe reloads.

## Animal Gym recipes

Animal eligibility and food are defined by normal datapack recipes in
`data/<namespace>/recipe/<path>.json`, with type `industrial_overkill:gym_animal`.
For example, `data/my_pack/recipe/gym/fox.json`:

```json
{
  "type": "industrial_overkill:gym_animal",
  "entity": "minecraft:fox",
  "food": [
    { "item": "minecraft:sweet_berries" },
    { "item": "minecraft:glow_berries" }
  ],
  "energy_per_tick": 4,
  "food_duration_ticks": 900
}
```

`entity` accepts an entity ID or `#namespace:entity_tag`; entity tags live in
`data/<namespace>/tags/entity_type/`. `food` is a standard Minecraft ingredient:
an item, item tag, or array of alternatives. NeoForge custom ingredients also work.
The recipe's foods are authoritative; vanilla breeding-food restrictions do not
limit custom recipes. Multiple matching recipes combine their accepted foods. When several recipes match
the same animal and serving, the first recipe by full recipe ID (alphabetical order)
supplies its rates. Food slots are checked in order, with an already hanging serving first.
Only living adult `Animal` entities can power the gym; passengers and ridden animals remain
excluded. Modded subclasses of `Animal` can be enabled with recipes too; special
modded resting poses may need their own renderer support.

Defaults are in `data/industrial_overkill/recipe/gym_animal/` (for example
`pandas.json`, `foxes.json`, `horses.json`). Additional default tier files are
`wolves_cooked.json`, `wolves_rotten_flesh.json`, `horses_hay.json`,
`horses_golden.json`, and `rabbits_golden.json`. The base wolf recipe uses a NeoForge
difference ingredient to retain `minecraft:wolf_food` additions while excluding
cooked foods and rotten flesh. Cooked meats use the extensible item tag
`industrial_overkill:gym/wolf_cooked_food`.
Override the same resource path to change
a default. To disable one, keep its `entity` and `food` fields and add `"enabled": false`.
This disables that recipe, not other recipes matching the same animal. New recipe
IDs add rules. Use `/reload` to apply changes; rules synchronize to clients and
update the inventory filter. Removing an animal's last rule stops its power generation.
Existing items and paid food allowances are retained; eligibility and future meals
use the new rules.

- `energy_per_tick`: FE generated per work unit, fallback when omitted **8**, range **1–10,666**.
  The maximum ensures an intensity-3 tick fits the 32,000 FE buffer.
- `food_duration_ticks`: work units supplied by one food item, fallback when omitted **200**, range
  **1–2,147,483,647**. At intensity 1, 20 ticks equals one second, so 200 means 10 seconds.

Intensity 2/3 consumes 2/3 work units per tick and multiplies output accordingly.
For example, 12 FE/t and 600 ticks yields 36 FE/t for 200 working ticks at intensity 3.
A partially funded final tick generates only its remaining units.
Each eaten meal snapshots its power and duration on the animal; pause, save/load,
trap transport and `/reload` preserve that allowance. Updated rates apply to the next
meal, including food already on the stick. Legacy allowances retain the old 8 FE rate.
Very short durations can expire before the existing 56-tick eating/refill sequence
finishes; the gym waits for the next serving without generating unpaid energy.
The eating/refill animation timing is independent of recipe rates.

## Critter Courier

The Critter Courier uses the registry ID `industrial_overkill:critter_courier`.
The previous development ID, `industrial_overkill:mob_trap`, has no migration.
Release any captured animals and remove old Mob Traps before upgrading a development world.
Its block, item properties, conditional models/shapes and screen are defined in
`mmdlib/machine/critter_courier.xml`. `CritterCourierProcessor` owns capture, transport,
release and the declared Release/Pick Up actions on MMDLib's generic machine host.
Release is disabled while empty and both actions recheck availability on the server.
The standard status label reads the processor's stored `details` lines. Each line
is translated independently, including health arguments and age. The standard
`entity-portrait` reads the typed `occupant` variable and renders a display-only
client entity; Courier needs no custom screen elements. The portrait uses the
occupant declaration's snapshot policy, including its saved panda resting pose.
MMDLib registers the block, item and block entity type automatically; Java integrations
look them up through `MachineContent`, without a Courier-specific registration class.
`CritterCourierProcessor` is the sole Courier gameplay class. Its shared
`OCCUPANT` portable-variable reference declares the processor value and provides
typed item access for Animal Gym and rendering. Its `ENTITIES` snapshot policy
pairs capture and restore hooks, preserving panda pose consistently during
transport and preview updates. Consumers keep `EntitySnapshot` values until NBT
serialization boundaries; there is no separate Courier data utility or Gym
migration logic in the Courier. Animal Gym uses the library's cached portable
reader to avoid decoding unchanged item components. Release returns a standard
action result, including translated failure messages, for the library to deliver.
The library handles item transfer, placement and the standard `mmdlib:pickup` action,
which is shared by the button and sneak-use. Occupancy and status lines are cached
derived variables, updated when the occupant changes or loads. The release
cooldown uses a persisted idle-only timer, and placement checks and detached
preview caching use shared entity helpers. Capture rules, wording, pose extensions
and Animal Gym installation remain mod-owned.
Animal Gym implements MMDLib's item receiver contract, so Courier needs no gym
classes or slot knowledge. Right-click any formed gym part with supported food to
insert as much of the held stack as fits, topping up existing stacks first and
leaving the rest in hand. With an animal installed or on the belt (even paused),
only that animal's food is accepted; an empty gym accepts any supported animal
food. GUI insertion and hopper automation use the same live food-slot filter.
Changing animals preserves existing food, which can still be removed through the
GUI. Adding food does not interrupt training.
Hover any food slot, empty or occupied, to see the current animal's accepted
foods across all recipe tiers. Suitable stored food also shows its energy details.
The shared right-click path also handles fluid buckets
and compatible modded containers on fluid machines, returning the resulting
container and leaving rejected exchanges unchanged.
Old development block-entity saves are not migrated.

All four sides share the paw display and status indicators; occupied cages also
have an amber overhead lamp on each side.
Occupied Courier items render their captured animal inside the cage in inventories,
in hand and when dropped. The preview uses the saved appearance and pose without
creating a world entity; empty items retain the armed plate model.
The XML `entity-display` supplies both block and item previews, their fit bounds,
and the occupied-item model predicate. MMDLib handles renderer registration and
model wrapping; Courier has no dedicated renderer classes. Carried-item tooltips
show both the custom name and species, for example **Bessie (Cow)**.

Craft one with `B B / B B / IPI`: four iron bars, two iron ingots and a stone
pressure plate. It is also in the Industrial Overkill creative tab. The compact
trap screen shows the animal portrait, name, health and age, with Release and
Pick Up icon buttons on the right; hover either icon for its action.

1. Place the empty trap on the ground. It appears as a low, armed plate.
2. Lure a mob across it. The cage snaps closed and holds one occupant.
3. Open its GUI and choose **Pick Up**, sneak-right-click with an empty hand,
   or break the cage. The resulting item contains the mob.
4. Right-click any formed gym part with the occupied trap, or put it in the
   gym's **Critter Courier** slot. Add the animal's food and extract the generated FE.
5. Take the trap out of the slot to carry the animal with you.
6. Place the trap back in the world and press its **Release** button to restore
   the mob in clear space beside the cage.

Installed animals stay visible on the treadmill, face the controller, and animate
while running. They cannot escape when paused, starved, obstructed or full of FE.
Breaking the gym drops their occupied trap alongside the normal machine contents.
Food automation cannot insert or extract the trap slot.

Traps preserve entity identity, names, health, equipment, variants, existing scale,
and remaining food allowance. They capture ordinary mobs, including hostile
mobs; players and riding/passenger groups are excluded. The entity-type tag
`industrial_overkill:critter_courier_blacklist` excludes the wither and ender dragon
by default. Datapacks can extend or replace it at
`data/industrial_overkill/tags/entity_type/critter_courier_blacklist.json`.
This restriction applies to new captures; already carried mobs remain releasable.
A lead is dropped
when its mob is captured. Only the gym's supported adult animals can power the gym; other
occupants stay safely stored and can be removed. A trapped mob's aging and AI are
suspended. Blocked releases, missing mob types and duplicate loaded UUIDs leave the
occupant in the trap. Occupied trap placement consumes the item even in creative.

The trap owns saved entity data. Its in-world preview and the gym's captive preview
are rendered without spawning another world entity. Remaining food allowance is written
back to the occupied item, so transport and reload preserve already paid work.

## Local development

Put these settings in your Gradle user home `gradle.properties` (normally
`%USERPROFILE%\.gradle\gradle.properties` on Windows):

```properties
mmdlibLocalRepo=/absolute/path/to/local/maven
mmdlibLocalVersion=2.0.0.121012-development
```

Both projects read these settings. In the MMDLib checkout, publish your current
working tree, including uncommitted edits:

```powershell
.\gradlew.bat publishAllPublicationsToLocalRepository
```

Animal Gym uses MMDLib's **multiblock implementation**. Rock Stew Factory uses
`rock_stew_factory_casing.json` for the casing and declares its powered overlays
and per-stage progress lights in its machine XML. Component notifications update
the appearance as processor state changes.
Use a local publication that provides the stored processor and action APIs used
by Critter Courier, plus RecipeChain, RecipeOperations, and the resource
restoration hook used by Rock Stew. CI uses the pinned published version.

Then, in this project:

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

Import this folder as a Gradle project in your IDE. A local MMDLib publish includes
sources for navigation/debugging. Republish whenever you change MMDLib, then rebuild
or restart the game. Local MMDLib dependencies are marked as changing and checked
on every build. MMDLib remains a separate required mod, not embedded in this jar.

Mod metadata is generated from `src/main/templates/META-INF/neoforge.mods.toml` by
`generateModMetadata`, which runs during both Gradle builds and NeoForge IDE sync.
Edit that template rather than the generated file under `build/generated/resources`.

The metadata requires exactly the MMDLib version selected for compilation. A local
development jar therefore requires the corresponding local MMDLib jar.

`./gradlew check` runs the server GameTests. `./gradlew runGymClientSmoke`
checks placement, real power generation, portrait and inventory synchronization, actual
food-energy tooltips, recipe links, intensity and pause/resume clicks, and teardown in a client; screenshots are saved under
`build/gym-client-smoke/screenshots`. Test classes and fixtures are excluded from
the shipped jar and normal client/server runs. Add `-PgymUiOnly` to check the gym screen
without the world-animation prelude. `./gradlew runCritterCourierClientSmoke`
checks ground capture, cage and GUI previews, pickup, gym installation through the
normal block interaction, contained generation, pause, ejection and world release;
its screenshots are under `build/courier-client-smoke/screenshots`.

`./gradlew runGymPerformance` benchmarks an empty scene, three working gyms with
loose cows, and three with captive cows. Each case warms up for 100 server ticks
and samples 200 ticks, with a fixed camera, 1280x960 window, render distance 8,
uncapped FPS and VSync off. A constant energy sink keeps all gyms working.
`build/gym-performance/results.csv` records mean/p95 server tick and frame times;
`profile.jfr` contains Java Flight Recorder CPU/allocation samples. These are
controlled development measurements, not an FPS guarantee for a player's world.
Captive progress is saved to its trap every tick, while routine visual updates use
MMDLib's 10-tick batching; item insertion/removal and controls still synchronize
immediately. Cached bounds/components and normal frustum culling reduce rendering
and copying work.

## Published dependency and GitHub CI

Without `mmdlibLocalRepo`, the build uses `mmdlib_version` from this project's
`gradle.properties` and `https://maven.moddev.zone/releases`. The workflow explicitly disables
local mode. To run the same build on your computer:

```powershell
.\gradlew.bat build -PmmdlibUseLocal=false
```

MMDLib is resolved exclusively from the selected repository. A missing local
publication fails rather than silently using a remote copy.

For a dependency update, publish a new MMDLib release, wait for Maven publishing to
finish, update the committed `mmdlib_version`, and push this project. Keep the remote
version pinned; use a new release version rather than overwriting it.

### Tag releases

Push a tag such as `v0.1.0`, `v0.1.0-alpha`, or `v0.1.0-beta` on a commit
already on `main`. The Release workflow uses the tag as the JAR version and
builds against the published MMDLib pin above. The build must pass before the
workflow uploads the NeoForge JAR and GitHub-generated release notes to
CurseForge and GitHub Releases.

Set the repository variable `CURSEFORGE_PROJECT_ID` and the repository secret
`CURSEFORGE_TOKEN`. Leave `MODRINTH_PROJECT_ID` unset to skip Modrinth validation
and publishing. When that variable is set, `MODRINTH_TOKEN` is required.

## Adding content

Java code belongs under `src/main/java/net/modcrafters/minecraft/industrialoverkill`.
Assets and recipes belong under `src/main/resources/assets/industrial_overkill`
and `src/main/resources/data/industrial_overkill`. MMDLib startup definitions use
its root-level `src/main/resources/mmdlib/` layout; consult MMDLib's
`docs/definition-loading.md` and `docs/xml-machines.md` for supported definitions.

## License

Industrial Overkill's code and assets are **All Rights Reserved**, as declared in
the mod metadata. No open-source license is granted for this project. Third-party
components, including the Gradle wrapper, retain their own licenses.
