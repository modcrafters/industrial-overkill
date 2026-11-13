package zone.moddev.mc.industrialoverkill.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.Animal;
import zone.moddev.mc.industrialoverkill.gym.AnimalGymEntity;
import zone.moddev.mc.industrialoverkill.gym.GymAnimalFit;
import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.model.ProcessorButtonState;
import zone.moddev.mc.lib.gui.runtime.ScreenRuntime;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/** Focused mode of GymClientSmoke: real factory load, stable demand rest and restart. */
final class GymDemandClientSmoke {
    private static int stage, samples;

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void render(AnimalGymEntity gym) {
        var mc = Minecraft.getInstance();
        if (stage == 0) {
            stage = 1;
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                ScreenRuntime.open(player, player.serverLevel().getBlockEntity(gym.getBlockPos()));
            });
            return;
        }
        if (!(mc.screen instanceof MachineScreen screen)) return;
        var menu = screen.getMenu();
        var toggle = menu.bound.screen().find("toggle").orElseThrow();
        var status = menu.bound.screen().find("status").orElseThrow();
        Object value = menu.value(status, false);
        String text = value instanceof Component component ? component.getString() : String.valueOf(value);
        Object button = menu.value(toggle, false);
        if (stage == 1) {
            if (samples == 0 && (!text.contains("Waiting for energy demand")
                || !ProcessorButtonState.IDLE.wireValue().equals(button))) return;
            require(gym.getGeneration() == 0 && text.contains("Waiting for energy demand")
                && ProcessorButtonState.IDLE.wireValue().equals(button), "Demand rest must not flicker in client or UI");
            if (++samples < 100) return;
            shot("animal-gym-demand-idle.png");
            stage = 2;
            samples = 0;
            mc.getSingleplayerServer().execute(() -> {
                var serverGym = (AnimalGymEntity) mc.getSingleplayerServer().overworld().getBlockEntity(gym.getBlockPos());
                serverGym.energy.setStored(24000);
            });
        } else if (stage == 2) {
            boolean fitted = mc.level.getEntity(gym.getAnimalId()) instanceof Animal cow
                && cow.getBbWidth() <= GymAnimalFit.MAX_WIDTH + .001
                && cow.getBbHeight() <= GymAnimalFit.MAX_HEIGHT + .001;
            // Entity attributes and the machine/menu use separate packets; allow initial convergence.
            if (samples == 0 && (gym.getGeneration() == 0 || !text.contains("Generating power")
                || !ProcessorButtonState.PROCESSING.wireValue().equals(button) || !fitted)) return;
            require(gym.getGeneration() == 16 && text.contains("Generating power")
                && ProcessorButtonState.PROCESSING.wireValue().equals(button), "Restarted work remains steady in client and UI");
            require(fitted, "Cow remains consistently fitted while running");
            if (++samples < 100) return;
            shot("animal-gym-demand-running.png");
            stage = 3;
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld();
                var factory = (DefinedMachineEntity) level.getBlockEntity(gym.getBlockPos().relative(gym.facing().getOpposite()));
                boolean produced = !factory.items.getStackInSlot(0).isEmpty();
                mc.execute(() -> {
                    MMDLib.LOGGER.info(produced ? "ANIMAL_GYM_CLIENT_PASS: stable demand idle/running UI, cow fit, and adjacent factory production"
                        : "ANIMAL_GYM_CLIENT_FAIL: adjacent factory did not produce");
                    mc.stop();
                });
            });
        }
    }

    private static void shot(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {});
    }
}
