package zone.moddev.mc.industrialoverkill;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import org.slf4j.Logger;

import zone.moddev.mc.industrialoverkill.gym.AnimalGym;
import zone.moddev.mc.lib.MMDLib;

@Mod(IndustrialOverkill.MOD_ID)
public final class IndustrialOverkill {
    public static final String MOD_ID = "industrial_overkill";
    private static final Logger LOGGER = LogUtils.getLogger();

    public IndustrialOverkill(IEventBus bus) {
        AnimalGym.register(bus);
        zone.moddev.mc.industrialoverkill.gym.GymAnimalRecipe.register(bus);
        zone.moddev.mc.industrialoverkill.trap.MobTrap.register(bus);
        LOGGER.info("Initializing Industrial Overkill with {}", MMDLib.MODID);
    }
}
