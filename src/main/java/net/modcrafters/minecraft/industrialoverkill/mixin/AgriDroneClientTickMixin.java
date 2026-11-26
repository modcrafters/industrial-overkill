package net.modcrafters.minecraft.industrialoverkill.mixin;

import net.modcrafters.minecraft.industrialoverkill.agri.AgriDockBlock;
import net.modcrafters.minecraft.industrialoverkill.client.AgriDroneClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

@Mixin(value = DefinedMachineEntity.class, remap = false)
abstract class AgriDroneClientTickMixin {
    @Inject(method = "clientTick", at = @At("TAIL"), require = 1)
    private void industrialOverkill$tickDrone(CallbackInfo callback) {
        var machine = (DefinedMachineEntity) (Object) this;
        if (AgriDockBlock.isController(machine.getBlockState())) AgriDroneClient.tick(machine);
    }
}
