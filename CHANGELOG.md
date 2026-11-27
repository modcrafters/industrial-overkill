# Industrial Overkill changelog

## [Unreleased]

For Minecraft 1.21.1 on NeoForge 21.1.265 or later in the 21.1 series. Requires Java 21 and MMDLib `2.0.4.121012+Neo`. JEI is optional.

### New since v0.0.1-alpha

- **Agri-Brain bush care.** Plant sweet berries from a seed slot and pick 2–3 berries from each fully ripe bush. The bush stays in place to regrow. Datapacks can add compatible bushes with the `industrial_overkill:agri_brain_bushes` block tag.

- **Agri-Brain stalk crops.** Plant sugar cane, cactus and bamboo on their usual ground. The drone trims the top block and leaves the base to regrow. Compatibility packs can add stalks through item and block tags, and mods can register a harvest handler for crops with custom drops or regrowth.

- **Generator recipe browser.** Open the recipe-book tab to browse foods and solid fuels, including their burn time, FE yield, and possible remains. The processing arrow opens the generator category in JEI when installed or the built-in drawer otherwise. JEI supports lookups by fuel, returned container, charcoal, and ash.

- **Omnivorous Generator.** Burn food or solid furnace fuel to power adjacent machines. A raw potato supplies 800 FE over five seconds at 8 FE/t. Coal and charcoal each supply 32,000 FE over 40 seconds at 40 FE/t. An Animal Gym still gets more from food its animal can eat. A pig gets 3,840 FE from that same potato.
- **Fuel and burn remains.** The burning item stays visible and reserved until it burns out. Redstone or a full buffer prevents the next ignition. A lit item keeps burning and any FE that cannot fit is lost. Four output slots collect returned containers and remains. Dry and lava burns yield one charcoal per burnable log and have a one-in-eight chance to leave ash per item when space permits. Hover the processing arrow for remaining FE and the current burn rate.
- **Generator catalysts.** Add 100 mB of lava to double a serving's energy and burn rate, or 50 mB of water to collect 25 mB of sewage as the only burn residue at its usual rate. Both tanks hold 4,000 mB and support buckets and pipes. Datapack catalyst recipes define the fluid dose, energy and rate multipliers, ash and charcoal chances, and optional fluid and item outputs with separate chances. The drawer and JEI show each fuel's dry and catalyzed recipes.
- **Ash fertilizer.** Use ash by hand or in a dispenser for a 30% chance to apply bone meal. In either of Agri-Brain’s alternative fertilizer slots, each ash has a 30% chance to advance a crop one stage.
- **Agri-Brain in survival.** Craft the controller with four iron ingots, an iron hoe, two wheat seeds, a composter, and redstone dust. Its five fertilizer slots now share one colour and one side configuration. The left column holds regular fertilizer, filtered fertilizer, and slow-release pellets. The two right slots accept bone meal or items in the alternative-fertilizer tag, with the mode button directly below. Existing fertilizer stacks carry over.

### Also included from the first alpha

#### Animals and power

- **Animal Gym 2.0.** Feed an adult animal on the three-block treadmill to generate FE. Lead an animal onto the belt or install an occupied Critter Courier. Three intensity settings let you spend a ration faster for higher FE/t while keeping the same total FE per ration. The Gym rests when its buffer fills and preserves the animal's remaining ration.
- **Critter Courier.** Place the pressure-plate trap, lure a mob across it, then pick up the occupied cage. Its item preview shows the passenger. Transport and release preserve the mob's identity, name, health, equipment, and variant. The trap can also carry hostile mobs. The wither and ender dragon are blacklisted by default.
- **Ranch Controller.** Tend a herd and collect milk from cows and mooshrooms into containers or a pipeable tank. **Pair Pressure** breeds animals up to a herd target, **Snackcelerator** feeds the young to speed growth, **Baa-rber Shop** shears sheep, and **Livestock To Go** packs surplus adults into empty Couriers.

#### Crops and fertilizer

- **Agri-Brain.** A farming drone follows rows behind the controller, tills with a supplied hoe, irrigates from its water tank, plants, fertilizes, harvests mature crops, and replants. Choose the first available seed, rotate through seed slots, or assign a crop to each row. Harvested seeds refill the inputs, and the drone leaves mature crops standing when their drops cannot fit.
- **Honest Sewer and Sewage Filter.** Collect sewage from nearby animals passively or supply FE to collect faster. Use raw sewage in fertilizer production or filter it into filtered sewage with a chance of seeds and scraps. Both fluids can be piped or carried in buckets. A second filtering pass produces water and filter cake.
- **Fertilizer Maker.** Process surplus seeds with water, sewage, or filtered sewage to make regular or filtered fertilizer. Filter cake and seeds also make slow-release pellets. Regular doses advance a crop one stage, filtered doses advance it two, and pellets feed the crop at planting and twice more as it grows. Agri-Brain offers Off, Smart, Finish Only, and Pellets Only modes.

#### Building materials and integration

- **Rock Stew Factory.** Supply FE, water, and lava and select a building material. The factory can generate 13 stone types and run up to five stages concurrently along a production path. For example, it can turn cobblestone into gravel, sand, glass, and finally glass panes. Each stage has an extractable output and its own stock target.
- **Upgrades and automation.** Use MMDLib Range, Gearbox, and Capacitor upgrades on compatible machines to expand the work area, speed up work, or increase FE capacity and transfer rates. Side controls let players route items, fluids, and FE. Agri-Brain and the Ranch Controller can show their working areas in the world.
- **Recipes and modpacks.** The Animal Gym, Rock Stew Factory, Sewage Filter, and Fertilizer Maker have in-game recipe drawers and optional JEI integration. Datapacks can customize animal diets and power yields, plantable and compostable seeds, Sewage Filter recipes, and Rock Stew sources and grinding steps.
