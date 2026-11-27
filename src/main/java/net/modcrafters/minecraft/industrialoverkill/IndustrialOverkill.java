package net.modcrafters.minecraft.industrialoverkill;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.slf4j.Logger;

import net.modcrafters.minecraft.industrialoverkill.gym.AnimalGym;
import net.modcrafters.minecraft.industrialoverkill.power.OmnivorousGenerator;
import net.modcrafters.minecraft.industrialoverkill.agri.AgriFertilizerMigration;
import net.modcrafters.minecraft.industrialoverkill.agri.AgriPellets;
import net.modcrafters.minecraft.industrialoverkill.courier.CourierOccupantLoot;
import net.modcrafters.minecraft.industrialoverkill.ranch.RanchAddons;
import net.modcrafters.minecraft.industrialoverkill.waste.FarmFluids;
import net.modcrafters.minecraft.industrialoverkill.waste.FarmRecipeBooks;
import net.modcrafters.minecraft.industrialoverkill.waste.FarmSupplies;
import zone.moddev.mc.lib.MMDLib;

@Mod(IndustrialOverkill.MOD_ID)
public final class IndustrialOverkill {
    public static final String MOD_ID = "industrial_overkill";
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> AGRI_DRONE_ROTOR = SOUNDS.register(
        "agri_drone_rotor", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> AGRI_WATER_SPRINKLE = SOUNDS.register(
        "agri_water_sprinkle", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> AGRI_WATER_SPLASH = SOUNDS.register(
        "agri_water_splash", SoundEvent::createVariableRangeEvent);
    private static final Logger LOGGER = LogUtils.getLogger();

    public IndustrialOverkill(IEventBus bus, ModContainer container) {
        NeoForgeMod.enableMilkFluid();
        SOUNDS.register(bus);
        FarmSupplies.register(bus);
        AgriFertilizerMigration.register();
        FarmFluids.register(bus);
        CourierOccupantLoot.register(bus);
        RanchAddons.register(bus);
        FarmRecipeBooks.register();
        NeoForge.EVENT_BUS.addListener(AgriPellets::tick);
        net.modcrafters.minecraft.industrialoverkill.stew.RockStewConfig.register(bus, container);
        net.modcrafters.minecraft.industrialoverkill.stew.RockStewRecipes.register(bus);
        AnimalGym.register(bus);
        OmnivorousGenerator.register(bus);
        net.modcrafters.minecraft.industrialoverkill.agri.AgriDockBlock.register(bus);
        net.modcrafters.minecraft.industrialoverkill.upgrade.MachineUpgrades.register(bus);
        net.modcrafters.minecraft.industrialoverkill.gym.GymAnimalRecipe.register(bus);
        LOGGER.info("Initializing Industrial Overkill with {}", MMDLib.MODID);
    }
}
