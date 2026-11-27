package net.modcrafters.minecraft.industrialoverkill.client;

import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.power.GeneratorEntity;
import net.modcrafters.minecraft.industrialoverkill.power.GeneratorFuel;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;

@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, value = Dist.CLIENT)
public final class GeneratorFuelTooltip {
    @SubscribeEvent
    public static void fuelValue(ItemTooltipEvent event) {
        var player = event.getEntity();
        if (player == null || !(player.containerMenu instanceof MachineMenu menu)
            || !(menu.blockEntity instanceof GeneratorEntity)) return;
        var fuel = GeneratorFuel.forStack(player.level(), event.getItemStack());
        if (fuel != null) event.getToolTip().add(Component.translatable("omnivore.serving", fuel.energy(), fuel.rate()));
    }
}
