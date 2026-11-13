package net.modcrafters.minecraft.industrialoverkill.gym;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierData;
import zone.moddev.mc.lib.api.automation.AutomationInventory;
import zone.moddev.mc.lib.api.automation.ResourceGroup;
import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.SideConfig;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.api.redstone.RedstoneOutput;
import zone.moddev.mc.lib.machine.feature.ItemReceiver;
import zone.moddev.mc.lib.machine.multiblock.MultiblockInstance;
import zone.moddev.mc.lib.machine.multiblock.StructureTransform;
import zone.moddev.mc.lib.machine.neoforge.BasePoweredMachine;
import zone.moddev.mc.lib.machine.neoforge.MultiblockMembers;
import zone.moddev.mc.lib.machine.neoforge.MultiblockResources;

public final class AnimalGymEntity extends BasePoweredMachine implements MultiblockResources, ItemReceiver {
    public final ItemStorage food = new ItemStorage(4)
        .restrict(stack -> GymAnimalRecipe.food(level, stack), 0, 1, 2)
        .restrict(CritterCourierData::occupied, 3).onChange(this::inventoryChanged);
    public final GymCaptive captive = new GymCaptive(this);
    private CompoundTag clientCaptive = new CompoundTag();
    private Mob captivePreview;
    public final GymFeeding feeding = new GymFeeding(this);
    private boolean captivePreviewResolved;
    private boolean persistingCaptive;
    private Direction boundsFacing;
    private AABB cachedBeltBounds, cachedRenderBounds;
    private Vec3 cachedBeltCenter;
    public final GymTraining training = new GymTraining(this);
    private final MultiblockInstance structure;
    public final SideConfig foodSides = new SideConfig(IoMode.INPUT)
        .disabledFaces(AnimalGym.UNAVAILABLE_FACES);
    public final SideConfig energySides = new SideConfig(IoMode.OUTPUT)
        .disabledFaces(AnimalGym.UNAVAILABLE_FACES);
    private final AutomationInventory automation;
    private final RedstoneControl redstone;
    private int clientAnimal = -1, clientGeneration;
    private long clientPowerLeft, clientFoodTicksLeft;
    private String clientStatus = "waiting", clientName = "";
    public AnimalGymEntity(BlockPos pos, BlockState state) {
        super(AnimalGym.CONTROLLER_TYPE.get(), pos, state, 32000, 0, 128);
        structure = new MultiblockInstance(this, AnimalGym.definition(), MultiblockMembers.INSTANCE,
            new MultiblockInstance.Listener() {
                @Override
                public void changed(MultiblockInstance.Status previous,
                    MultiblockInstance.Status current, String reason) {
                    if (current != MultiblockInstance.Status.READY)
                        training.reset();
                }
                @Override
                public void dismantling(MultiblockInstance.Cause cause) {
                    training.reset();
                    feeding.dismantle();
                    food.dropAll(level, worldPosition,
                        payload -> recoverResources(level, worldPosition, payload));
                    if (cause == MultiblockInstance.Cause.SURVIVAL)
                        net.minecraft.world.Containers.dropItemStack(level,
                            worldPosition.getX() + .5, worldPosition.getY() + .5,
                            worldPosition.getZ() + .5, new ItemStack(AnimalGym.ITEM.get()));
                }
            });
        var foodGroup = new ResourceGroup<>("food", Component.translatable("gym.group.food"),
            0xc9b441,
            ResourceType.ITEMS, IoMode.INPUT, List.of(0, 1, 2), List.of("food0", "food1", "food2"),
            foodSides)
            .inventory("items").bind(this::facing, side -> multiblockItems("food", side));
        var energyGroup = new ResourceGroup<>("energy", Component.translatable("gym.group.energy"),
            0xd95b40,
            ResourceType.ENERGY, IoMode.OUTPUT, List.of(0), List.of("energy"), energySides)
            .bind(this::facing, side -> multiblockEnergy("energy", side));
        automation = new AutomationInventory(this, List.of(foodGroup, energyGroup));
        redstone = new RedstoneControl(this, List.of(
            RedstoneOutput.resource(energyGroup,
                () -> (double) energy.stored() / energy.capacity()),
            new RedstoneOutput("working", Component.translatable("gym.redstone.working"),
                Component.translatable("gym.redstone.working.help"), 0x69c741, Optional.empty(),
                () -> training.generation() > 0 ? 15 : 0)))
            .disabledFaces(AnimalGym.UNAVAILABLE_FACES);
        features.add(training);
    }
    @Override
    public boolean accepts(ItemStack offered) {
        return CritterCourierData.occupied(offered) || GymAnimalRecipe.food(level, offered);
    }
    @Override
    public int offerLimit(ItemStack held) {
        return CritterCourierData.occupied(held) ? 1 : held.getCount();
    }
    @Override
    public Exchange receive(ItemStack offered, UseOnContext context) {
        if (context.getLevel().isClientSide() || !accepts(offered) || !structureReady())
            return Exchange.reject();
        if (!CritterCourierData.occupied(offered))
            return receiveFood(offered);
        if (offered.getCount() != 1 || !food.getStackInSlot(3).isEmpty() || !food.insertItem(3, offered, true).isEmpty())
            return Exchange.reject();
        if (!food.insertItem(3, offered, false).isEmpty())
            return Exchange.reject();
        training.reset();
        synchronize();
        return Exchange.accept(ItemStack.EMPTY);
    }
    private Exchange receiveFood(ItemStack offered) {
        var animal = training.foodAnimal();
        if (animal != null ? !GymAnimalRecipe.foodFor(animal, offered) : captive.installed())
            return Exchange.reject();
        var remaining = offered.copy();
        // Top up existing stacks before using empty slots; slot 3 belongs to the occupant.
        for (boolean empty : new boolean[]{false, true}) {
            for (int slot = 0; slot < 3; slot++) {
                if (food.getStackInSlot(slot).isEmpty() != empty)
                    continue;
                remaining = food.insertItem(slot, remaining, false);
                if (remaining.isEmpty())
                    return Exchange.reusable(ItemStack.EMPTY);
            }
        }
        return remaining.getCount() < offered.getCount()
            ? Exchange.reusable(remaining) : Exchange.reject();
    }
    private void inventoryChanged() {
        setChanged();
        if (!persistingCaptive && level != null && !level.isClientSide)
            synchronize();
    }
    /** Save every training tick, but let the feature host batch visual updates. */
    void persistCaptive(ItemStack stack) {
        persistingCaptive = true;
        try {
            food.restoreStack(3, stack);
        } finally {
            persistingCaptive = false;
        }
    }
    public void ejectTrap(Player player) {
        training.control("release");
        var trap = food.extractItem(3, 1, false);
        player.getInventory().placeItemBackInInventory(trap);
        synchronize();
    }
    public Mob captivePreview() {
        if (level == null || !level.isClientSide)
            return captive.mob();
        if (!captivePreviewResolved) {
            captivePreviewResolved = true;
            captivePreview = CritterCourierData.load(clientCaptive, level);
        }
        return captivePreview;
    }
    @Override
    public MultiblockInstance multiblock() {
        return structure;
    }
    @Override
    public ItemStorage screenItems() {
        return food;
    }
    @Override
    public AutomationInventory screenAutomation() {
        return automation;
    }
    @Override
    public Map<String, SideConfig> screenSides() {
        return Map.of("food", foodSides, "energy", energySides);
    }
    @Override
    public RedstoneControl screenRedstone() {
        return redstone;
    }
    @Override
    public boolean screenAutomationAddons() {
        return false;
    }
    @Override
    public IItemHandler multiblockItems(String resource, Direction side) {
        return resource.equals("food")
            ? food.view(slot -> slot == 3 || side == null
                ? IoMode.DISABLED
                : ResourceGroup.intersect(IoMode.INPUT, foodSides.mode(facing(), side)))
            : null;
    }
    @Override
    public IEnergyStorage multiblockEnergy(String resource, Direction side) {
        return resource.equals("energy")
            ? energy.view(() -> side == null
                ? IoMode.DISABLED
                : ResourceGroup.intersect(IoMode.OUTPUT, energySides.mode(facing(), side)))
            : null;
    }
    public void control(String action) {
        if (level != null && !level.isClientSide)
            training.control(action);
    }
    /** Auxiliary light data is safe for the lighting thread and leaves member states intact. */
    void updateLampLight() {
        if (level == null || structure == null || structure.transform() == null)
            return;
        int emission = getGeneration() > 0 ? 14 : 0;
        for (int i = 1; i <= 2; i++) {
            var pos = structure.transform().position(new BlockPos(-i, 0, 0));
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(AnimalGym.BELT.get()))
                continue;
            var lights = level.getAuxLightManager(pos);
            if (lights != null && lights.getLightAt(pos) != emission)
                lights.setLightAt(pos, emission);
        }
    }
    public void synchronize() {
        syncToClient();
    }
    @Override
    protected void recoverResources(net.minecraft.world.level.Level world, BlockPos pos,
        CompoundTag payload) {
        var stack = new ItemStack(AnimalGym.RECOVERY.get());
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.of(payload.copy()));
        net.minecraft.world.Containers.dropItemStack(world, pos.getX() + .5, pos.getY() + .5,
            pos.getZ() + .5, stack);
    }
    public AABB beltBounds() {
        var facing = facing();
        if (boundsFacing != facing) {
            boundsFacing = facing;
            var transform = new StructureTransform(worldPosition, facing);
            cachedBeltBounds = new AABB(transform.position(new BlockPos(-1, 0, 0)))
                .minmax(new AABB(transform.position(new BlockPos(-2, 0, 0))))
                .expandTowards(0, 1, 0);
            cachedBeltCenter = new Vec3((cachedBeltBounds.minX + cachedBeltBounds.maxX) / 2,
                worldPosition.getY() + .25, (cachedBeltBounds.minZ + cachedBeltBounds.maxZ) / 2);
            cachedRenderBounds = cachedBeltBounds
                .minmax(new AABB(worldPosition).expandTowards(0, 1, 0))
                .inflate(.05);
        }
        return cachedBeltBounds;
    }
    public AABB renderBounds() {
        beltBounds();
        return cachedRenderBounds;
    }
    /** The belt extends along local west, so animals run east toward the cabinet. */
    public Direction trainingFacing() {
        return facing().getClockWise();
    }
    public Vec3 beltCenter() {
        beltBounds();
        return cachedBeltCenter;
    }
    public boolean onBelt(Animal animal) {
        var bounds = beltBounds();
        // Acquire by foot position, before fitting. Oversized eligible animals must
        // be allowed onto the belt so their temporary scale can be applied.
        return animal.getX() >= bounds.minX + .15 && animal.getX() <= bounds.maxX - .15
            && animal.getZ() >= bounds.minZ + .15 && animal.getZ() <= bounds.maxZ - .15
            && Math.abs(animal.getY() - (worldPosition.getY() + .25)) < .5;
    }
    public int getAnimalId() {
        return level != null && level.isClientSide
            ? clientAnimal
            : captive.installed() || training.animal() == null ? -1 : training.animal().getId();
    }
    public int getIntensity() {
        return training.intensity();
    }
    public int getMaxIntensity() {
        return 3;
    }
    public int getGeneration() {
        return level != null && level.isClientSide ? clientGeneration : training.generation();
    }
    public long getPowerLeft() {
        return foodReserve().energy();
    }
    public long getFoodTicksLeft() {
        return foodReserve().ticks();
    }
    private GymTraining.FoodReserve foodReserve() {
        return level != null && level.isClientSide
            ? new GymTraining.FoodReserve(clientPowerLeft, clientFoodTicksLeft)
            : training.foodReserve();
    }
    private static Component foodDuration(long ticks) {
        long seconds = (ticks + 19) / 20;
        if (seconds >= 3600)
            return Component.translatable("gym.duration.hours", seconds / 3600,
                seconds / 60 % 60, seconds % 60);
        if (seconds >= 60)
            return Component.translatable("gym.duration.minutes", seconds / 60, seconds % 60);
        return Component.translatable("gym.duration.seconds", seconds);
    }
    /** The actual paid serving, or the last bite during the short approach to the stick. */
    public ItemStack displayFood() {
        return feeding.display();
    }
    public Component getStatusText() {
        var reserve = foodReserve();
        String status = level != null && level.isClientSide ? clientStatus : training.status();
        String name = level != null && level.isClientSide
            ? clientName
            : training.animal() == null ? "" : training.animal().getName().getString();
        return Component.translatable("gym.status." + status).append("\n").append(name)
            .append("\n").append(Component.translatable("gym.output", getGeneration()))
            .append("\n").append(Component.translatable("gym.power_left",
                String.format(java.util.Locale.ROOT, "%,d", reserve.energy())))
            .append("\n").append(Component.translatable("gym.time_left", foodDuration(reserve.ticks())));
    }
    @Override
    public List<Component> getWarnings() {
        return List.of(Component.translatable("gym.help"));
    }
    @Override
    protected void afterMachineTick() {
        for (var side : Direction.values()) {
            if (!energySides.mode(facing(), side).canExtract())
                continue;
            var target = worldPosition.relative(side);
            if (!level.hasChunkAt(target))
                continue;
            var receiver = level.getCapability(Capabilities.EnergyStorage.BLOCK, target,
                side.getOpposite());
            if (receiver != null)
                energy.extract(receiver.receiveEnergy((int) Math.min(128, energy.stored()), false),
                    false);
        }
    }
    @Override
    protected void saveMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("food", food.serializeNBT(registries));
        tag.put("feeding", feeding.save(registries));
        tag.put("automation", automation.save(registries));
    }
    @Override
    protected void loadMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        food.deserializeNBT(registries, tag.getCompound("food"));
        feeding.load(tag.getCompound("feeding"), registries);
        if (tag.contains("automation"))
            automation.load(tag.getCompound("automation"), registries);
    }
    @Override
    protected void saveClientData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("feeding", feeding.save(registries));
        tag.put("captive", CritterCourierData.read(food.getStackInSlot(3)));
        tag.putInt("animal", getAnimalId());
        tag.putInt("generation", training.generation());
        var reserve = foodReserve();
        tag.putLong("power_left", reserve.energy());
        tag.putLong("food_ticks_left", reserve.ticks());
        tag.putString("status", training.status());
        tag.putString("name",
            training.animal() == null ? "" : training.animal().getName().getString());
    }
    @Override
    protected void loadClientData(CompoundTag tag, HolderLookup.Provider registries) {
        var data = tag.getCompound("captive");
        if (!data.equals(clientCaptive)) {
            if (captivePreview != null && !data.isEmpty()
                && data.getString("id").equals(clientCaptive.getString("id"))
                && data.hasUUID("UUID") && data.getUUID("UUID").equals(captivePreview.getUUID())) {
                captivePreview.load(data.copy());
                CritterCourierData.restorePose(captivePreview, data);
            } else {
                captivePreview = null;
                captivePreviewResolved = false;
            }
            clientCaptive = data.copy();
        }
        clientAnimal = tag.getInt("animal");
        clientGeneration = tag.getInt("generation");
        clientPowerLeft = tag.getLong("power_left");
        clientFoodTicksLeft = tag.getLong("food_ticks_left");
        updateLampLight();
        feeding.load(tag.getCompound("feeding"), registries);
        clientStatus = tag.getString("status");
        clientName = tag.getString("name");
    }
    @Override
    public void onLoad() {
        super.onLoad();
        // Recheck saved light arrays, including worlds saved with always-on lamps.
        if (level != null && !level.isClientSide && structure.transform() != null)
            for (int i = 1; i <= 2; i++) {
                var pos = structure.transform().position(new BlockPos(-i, 0, 0));
                if (level.hasChunkAt(pos))
                    level.getChunkSource().getLightEngine().checkBlock(pos);
            }
    }
    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide)
            training.reset();
        super.setRemoved();
    }
    @Override
    public void onChunkUnloaded() {
        training.reset();
        super.onChunkUnloaded();
    }
}
