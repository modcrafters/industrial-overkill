# Industrial Overkill

NeoForge 1.21.1 / Java 21 mod using MMDLib. The entry point is
`net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill`; the mod ID is
`industrial_overkill`. Machines include **Animal Gym 2.0**, **Critter Courier**, and
**Rock Stew Factory**.

This is a development version. MMDLib is a required, separate mod; JEI is optional.

The current Critter Courier migration requires the accompanying MMDLib changes
for stored processors, XML actions and conditional shapes. Use the local development
workflow below until those changes are released and `mmdlib_version` is updated.
The currently pinned public release does not contain these APIs.

## Getting started

Install Java 21, clone this repository, and build against the pinned public MMDLib
release:

```sh
git clone https://github.com/modcrafters/industrial-overkill.git
cd industrial-overkill
./gradlew build -PmmdlibUseLocal=false
```

On Windows, use `gradlew.bat` in place of `./gradlew`. `build` includes the server
GameTests; output jars are written to `build/libs/`. Use `./gradlew runClient` for
a development client. The optional local MMDLib workflow is described below.

## Rock Stew Factory

A one-block FE-powered stone generator with a grey industrial frame, cyan
Resources-category trim and a cobblestone display. Craft it with
`ICI / BFB / IRI`: four iron ingots, cobblestone, two empty buckets, a furnace,
and redstone. It is also in the Industrial Overkill creative tab.

The small input slot beside the processor is a **ghost inventory slot**. Open the
machine, view its recipes in JEI, and click **+** to select the recipe's sample.
This works without owning the sample or filling the tanks first. You can also click
the slot with any supported block, or drop that ingredient from JEI. It records a
one-item sample without taking the item. Right-click clears the sample. Recipes
match that sample as an ordinary item input, while the ghost storage prevents it
from being consumed, dropped, recovered or accessed by automation.

Every recipe requires **1,000 mB water and 1,000 mB lava** in the two static
left-hand tanks. Fill these manually by clicking their screen gauges with buckets.
They are never consumed and have no automation group or side configuration.

Energy is **160 FE (RF) per point of vanilla block hardness** per output block.
All recipes produce one block. Most take 40 processing ticks (2 seconds); obsidian
scales to 400 ticks (20 seconds). These are ordinary, editable datapack costs.

| Output | Consumed from third tank | Energy | Time |
|---|---|---:|---:|
| Cobblestone (default) | None | 320 FE | 2 s |
| Cobbled Deepslate | None | 560 FE | 2 s |
| Stone, Andesite, Diorite, Granite, Tuff | 100 mB lava | 240 FE | 2 s |
| Deepslate | 200 mB lava | 480 FE | 2 s |
| Mossy Cobblestone | 250 mB water | 320 FE | 2 s |
| Basalt | 250 mB lava | 200 FE | 2 s |
| Blackstone | 200 mB lava | 240 FE | 2 s |
| Calcite | 100 mB lava | 120 FE | 2 s |
| Obsidian | 1,000 mB lava | 8,000 FE | 20 s |

The screen order is: static water, static lava, ghost sample, progress arrow,
2x2 output slots, reagent tank, energy gauge. The third (reagent) tank holds
1,000 mB and accepts water or lava through its gauge, bucket interaction or pipes.
Changing the sample does not replace its contents: empty the reagent tank manually
before switching fluids. Cobblestone and cobbled deepslate need no reagent. Supply FE to run; the
processor takes one additional tick to select each cycle. Hoppers and pipes can
extract output; they cannot insert items or drain the tanks. JEI shows each recipe
and its costs, including both full static tanks for every recipe.

The ghost sample is saved with the machine. Changing or clearing it affects the
next cycle; already running or paused work retains its original costs and paid
progress. Missing resources never cause production of a different selected stone.
The default sample is Cobblestone, including old saves. A removed recipe starts no
new work, while an already reserved cycle may finish.

The storage declaration is `<slots id="input" count="1" role="input" type="ghost"
default-item="minecraft:cobblestone"/>`. Recipes name it under `inputs.input`, for
example `[{"items":{"item":"minecraft:stone"},"count":1}]`. This uses the normal recipe
matcher, with no machine-specific output-selection rule.

Storage `name` attributes supply the display names: Water Catalyst, Lava Catalyst,
Reagent, Rock Selection and Products. MMDLib generates English translations under
`industrial_overkill:rock_stew_factory.group.<id>`; language files can override
these keys. Manual tank names remain visible without enabling automation.

The shared MMDLib screen includes progress, all three tanks, energy, pause/resume,
side automation and redstone controls. Power loss preserves paid progress;
full output storage stops new work without spending FE. Fluid reservations
protect the catalysts while a cycle runs; pause before removing them through
the screen. Save/reload preserves tanks, output, settings and processing progress.
Breaking the block drops its output; stored FE and tank fluids are lost.
Pause and recover the catalysts with empty buckets before dismantling it.

The machine uses MMDLib's XML runtime. Its ordinary datapack recipes are in
`data/industrial_overkill/recipe/rock_stew/`; override them and `/reload` to change
time, power cost, fluid consumption or output. Every recipe reserves
static water/lava with `"consumed": false` on each reusable input. They remain in their
tanks throughout processing and need no matching outputs. JEI marks these inputs
and the ghost stone sample as not consumed. These appear faded before a colon,
separate from the consumed ingredients. Fluid fills use whole 1,000 mB scales.
Regenerate the 32px textures with `py -3 scripts/generate_rock_stew_assets.py`.
Run its real-client check with `gradlew.bat runRockStewClientSmoke --offline`.

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

The screen places the occupied-trap slot to the left of the live animal portrait,
with a recipe-book button below it when JEI is installed. Food slots sit directly
under the portrait; their tooltips show power and food duration at the selected intensity,
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
The overhead lamps glow and emit level-14 block light only while the gym works.
They switch off on pause, release, exhausted food allowance, full energy storage
or a blocked structure. The last consumed ration still provides its paid work
before the gym stops; an empty inventory alone does not cancel that allowance. Models and pixel textures are
generated reproducibly with `py -3 scripts/generate_gym_assets.py` (standard library
only). They follow the concept's compact silhouette: chamfered grey controller, recessed
paw display, green indicators, front vents, yellow-black edge stripes, rear uprights,
sloped end support and two amber overhead lamps. The item preview shows the whole gym.
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

The registry ID is `industrial_overkill:critter_courier`.
Its block, item properties, conditional models/shapes and screen are defined in
`mmdlib/machine/critter_courier.xml`. `CritterCourierProcessor` owns capture, transport,
release and the declared Release/Pick Up actions on MMDLib's generic machine host.
Release is disabled while empty and both actions recheck availability on the server.
The standard status label reads the processor's stored `details` lines. Each line
is translated independently, including health arguments and age. The standard
`entity-portrait` reads the typed `occupant` variable and renders a display-only
client entity; Courier needs no custom screen elements.
MMDLib registers the block, item and block entity type automatically; Java integrations
look them up through `MachineContent`, without a Courier-specific registration class.
The processor opts its `EntitySnapshot` occupant into MMDLib's portable state. The
library handles item transfer, placement and the standard `mmdlib:pickup` action,
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
food. Adding food does not interrupt training.
The shared right-click path also handles fluid buckets
and compatible modded containers on fluid machines, returning the resulting
container and leaving rejected exchanges unchanged.
Old development block-entity saves are not migrated.

All four sides share the paw display and status indicators; occupied cages also
have an amber overhead lamp on each side.
Occupied Courier items render their captured animal inside the cage in inventories,
in hand and when dropped. The preview uses the saved appearance and pose without
creating a world entity; empty items retain the armed plate model.

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
mobs; players, bosses and riding/passenger groups are excluded. A lead is dropped
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

Animal Gym uses MMDLib's **multiblock implementation**, and the
Rock Stew Factory's running indicator requires **conditional machine model parts**.
The indicator and front/top face-texture switches are declared in its machine XML with
`$processors.rock_stew.running`;
its casing override is `rock_stew_factory_base.json`, leaving the generated conditional
root model intact. The asset generator writes idle/running textures and black-and-white
emissive masks; component notifications update the appearance without tick polling.
Use a local publication that also provides the stored processor/action APIs used
by Critter Courier. CI uses the pinned published version and will need a new
MMDLib release and dependency update before this migration can be published.

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
