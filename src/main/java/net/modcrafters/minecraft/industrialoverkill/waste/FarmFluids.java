package net.modcrafters.minecraft.industrialoverkill.waste;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import static net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill.MOD_ID;

public final class FarmFluids {
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items BUCKETS = DeferredRegister.createItems(MOD_ID);

    public static final Family SEWAGE = new Family("sewage", 0xff84623a);
    public static final Family FILTERED_SEWAGE = new Family("filtered_sewage", 0xff54aa99);

    private FarmFluids() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        FLUIDS.register(bus);
        BLOCKS.register(bus);
        BUCKETS.register(bus);
    }

    public static final class Family {
        public final DeferredHolder<FluidType, FluidType> type;
        public final DeferredHolder<Fluid, BaseFlowingFluid.Source> source;
        public final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing;
        public final DeferredItem<BucketItem> bucket;
        public final int tint;
        private final DeferredBlock<LiquidBlock> block;

        private Family(String name, int tint) {
            this.tint = tint;
            type = TYPES.register(name, () -> new FluidType(FluidType.Properties.create()));
            source = FLUIDS.register(name, () -> new BaseFlowingFluid.Source(properties()));
            flowing = FLUIDS.register("flowing_" + name, () -> new BaseFlowingFluid.Flowing(properties()));
            block = BLOCKS.register(name, () -> new LiquidBlock(source.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.WATER)));
            bucket = BUCKETS.register(name + "_bucket", () -> new BucketItem(source.get(),
                new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
        }

        private BaseFlowingFluid.Properties properties() {
            return new BaseFlowingFluid.Properties(type, source, flowing).block(block).bucket(bucket);
        }
    }
}
