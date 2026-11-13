package net.modcrafters.minecraft.industrialoverkill;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import org.slf4j.Logger;

import net.modcrafters.minecraft.industrialoverkill.gym.AnimalGym;
import zone.moddev.mc.lib.MMDLib;

@Mod(IndustrialOverkill.MOD_ID)
public final class IndustrialOverkill {
    public static final String MOD_ID = "industrial_overkill";
    private static final Logger LOGGER = LogUtils.getLogger();

    public IndustrialOverkill(IEventBus bus) {
        AnimalGym.register(bus);
        net.modcrafters.minecraft.industrialoverkill.gym.GymAnimalRecipe.register(bus);
        LOGGER.info("Initializing Industrial Overkill with {}", MMDLib.MODID);
    }
}
