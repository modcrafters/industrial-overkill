package net.modcrafters.minecraft.industrialoverkill.client;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import net.modcrafters.minecraft.industrialoverkill.IndustrialOverkill;
import net.modcrafters.minecraft.industrialoverkill.courier.CritterCourierData;

/** Item previews are detached entities; they never join or tick in the client world. */
@EventBusSubscriber(modid = IndustrialOverkill.MOD_ID, value = Dist.CLIENT)
public final class CritterCourierItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static CritterCourierItemRenderer instance;
    private final Map<CustomData, Mob> previews = new LinkedHashMap<>(16, .75f, true);
    private ClientLevel level;
    private boolean renderingAnimal;

    private CritterCourierItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
            Minecraft.getInstance().getEntityModels());
    }

    public static CritterCourierItemRenderer instance() {
        if (instance == null)
            instance = new CritterCourierItemRenderer();
        return instance;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (instance != null) {
            instance.previews.clear();
            instance.level = null;
        }
    }

    private Mob preview(ItemStack stack) {
        var current = Minecraft.getInstance().level;
        if (current != level) {
            previews.clear();
            level = current;
        }
        if (level == null)
            return null;
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (data.isEmpty())
            return null;
        if (!previews.containsKey(data)) {
            var mob = CritterCourierData.load(CritterCourierData.read(stack), level);
            if (mob != null) {
                mob.setPos(0, 0, 0);
                var size = mob.getDimensions(mob.getPose());
                if (!Float.isFinite(size.width()) || !Float.isFinite(size.height())
                    || size.width() <= 0 || size.height() <= 0)
                    mob = null;
            }
            previews.put(data, mob);
            if (previews.size() > 64)
                previews.remove(previews.keySet().iterator().next());
        }
        return previews.get(data);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
        MultiBufferSource buffers, int light, int overlay) {
        var minecraft = Minecraft.getInstance();
        var items = minecraft.getItemRenderer();
        var model = items.getModel(stack, minecraft.level, null, 0);
        if (model instanceof CritterCourierItemModel courier)
            model = courier.geometry();
        // ItemRenderer has already applied the display transform and block-origin offset.
        for (var pass : model.getRenderPasses(stack, true))
            for (var type : pass.getRenderTypes(stack, true))
                items.renderModelLists(pass, stack, light, overlay, pose,
                    ItemRenderer.getFoilBufferDirect(buffers, type, true, stack.hasFoil()));
        // A captured mob can itself hold a Courier: only its cage is drawn recursively.
        if (renderingAnimal)
            return;
        var mob = preview(stack);
        if (mob == null)
            return;
        renderingAnimal = true;
        pose.pushPose();
        try {
            pose.translate(.5, .2, .5);
            CagedMobRenderer.render(mob, pose, buffers, light,
                minecraft.getTimer().getGameTimeDeltaPartialTick(false), .62f, .45f, 180, false, 1);
        } finally {
            pose.popPose();
            renderingAnimal = false;
        }
    }
}
