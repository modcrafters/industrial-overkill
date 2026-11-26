package net.modcrafters.minecraft.industrialoverkill.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.model.data.ModelData;

import net.modcrafters.minecraft.industrialoverkill.gym.AnimalGym;
import net.modcrafters.minecraft.industrialoverkill.gym.AnimalGymEntity;

/** Scrolls the whole rubber surface away from the controller while an animal works. */
public final class GymRenderer implements BlockEntityRenderer<AnimalGymEntity> {
    private static final net.minecraft.resources.ResourceLocation BELT_TEXTURE = AnimalGym
        .id("block/gym_belt");
    private static final net.minecraft.resources.ResourceLocation CORD_TEXTURE = AnimalGym
        .id("block/gym_gold");
    static final ModelResourceLocation POWERED_SIDES = ModelResourceLocation
        .standalone(AnimalGym.id("block/machine/generator_powered_sides"));
    static final ModelResourceLocation POWERED_DISPLAY = ModelResourceLocation
        .standalone(AnimalGym.id("block/gym_front_display"));
    static final ModelResourceLocation[] EFFORT_LIGHTS = java.util.stream.IntStream.rangeClosed(1, 3)
        .mapToObj(level -> ModelResourceLocation.standalone(AnimalGym.id("block/gym_effort_lights_" + level)))
        .toArray(ModelResourceLocation[]::new);
    // Keep the powered decals in the plane of their unlit artwork.
    private static final RenderType POWERED_RENDER_TYPE = RenderType.create(
        "industrial_overkill_gym_powered_decals", DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS, 256, RenderType.CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(
                ClientHooks.ClientEvents::getEntityTranslucentUnlitShader))
            .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS,
                false, false))
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOverlayState(RenderStateShard.OVERLAY)
            .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
            .createCompositeState(false));
    static final net.minecraft.client.resources.model.ModelResourceLocation[] LAMPS = java.util.stream.Stream
        .of("gym_belt_lamp", "gym_belt_end_lamp", "gym_belt_lamp_off", "gym_belt_end_lamp_off")
        .map(name -> net.minecraft.client.resources.model.ModelResourceLocation
            .standalone(AnimalGym.id("block/" + name)))
        .toArray(net.minecraft.client.resources.model.ModelResourceLocation[]::new);
    public GymRenderer(BlockEntityRendererProvider.Context context) {
    }
    @Override
    public void render(AnimalGymEntity gym, float partialTick, PoseStack pose,
        MultiBufferSource buffers, int light, int overlay) {
        var captive = gym.captivePreview();
        if (captive != null) {
            var center = gym.beltCenter();
            if (!captive.position().equals(center))
                captive.setPos(center.x, center.y, center.z);
            GymBiteAnimation.track(captive, gym);
            pose.pushPose();
            pose.translate(center.x - gym.getBlockPos().getX(), .25,
                center.z - gym.getBlockPos().getZ());
            CagedMobRenderer.render(captive, pose, buffers, light, partialTick, .8f, 1.25f,
                gym.trainingFacing().toYRot(), gym.getGeneration() > 0, gym.getIntensity(),
                gym.getGeneration() > 0 && (!gym.displayFood().isEmpty() || gym.feeding
                    .age(partialTick) < net.modcrafters.minecraft.industrialoverkill.gym.GymFeeding.EAT_END)
                        ? GymFoodDisplay.position(gym, partialTick)
                        : null);
            pose.popPose();
        }
        var level = gym.getLevel();
        if (level == null)
            return;
        var runner = captive != null ? captive : level.getEntity(gym.getAnimalId());
        if (runner instanceof net.minecraft.world.entity.Mob mob) {
            GymBiteAnimation.track(mob, gym);
            GymBiteAnimation.particles(gym, mob);
        }
        pose.pushPose();
        pose.translate(.5, 0, .5);
        float rotation = switch (gym.facing()) {
            case EAST -> -90;
            case SOUTH -> 180;
            case WEST -> 90;
            default -> 0;
        };
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        pose.translate(-.5, 0, -.5);
        var mc = Minecraft.getInstance();
        boolean running = gym.getGeneration() > 0;
        var unlit = NeoForgeRenderTypes.getUnlitTranslucent(TextureAtlas.LOCATION_BLOCKS, false);
        // Disable diffuse shading to keep the lamp lenses bright.
        var lightVertices = buffers.getBuffer(running ? unlit
            : RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS));
        for (int i = 0; i < 2; i++) {
            pose.pushPose();
            pose.translate(-i - 1, 0, 0);
            var model = mc.getModelManager().getModel(LAMPS[i + (running ? 0 : 2)]);
            mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), lightVertices,
                null, model, 1, 1, 1,
                running ? LightTexture.FULL_BRIGHT : light, overlay, ModelData.EMPTY, null);
            pose.popPose();
        }
        var effort = mc.getModelManager().getModel(EFFORT_LIGHTS[gym.getIntensity() - 1]);
        int gantryLight = LevelRenderer.getLightColor(level, gym.getBlockPos().above());
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(unlit),
            null, effort, 1, 1, 1, gantryLight, overlay, ModelData.EMPTY, null);
        if (!running) {
            pose.popPose();
            return;
        }
        var poweredSides = mc.getModelManager().getModel(POWERED_SIDES);
        var poweredDisplay = mc.getModelManager().getModel(POWERED_DISPLAY);
        var poweredVertices = buffers.getBuffer(POWERED_RENDER_TYPE);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), poweredVertices,
            null, poweredSides, 1, 1, 1, LightTexture.FULL_BRIGHT, overlay, ModelData.EMPTY, null);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), poweredVertices,
            null, poweredDisplay, 1, 1, 1,
            LightTexture.FULL_BRIGHT, overlay, ModelData.EMPTY, null);
        var food = gym.displayFood();
        float foodY = GymFoodDisplay.height(gym, partialTick);
        renderCord(pose, buffers, light, overlay, foodY + .08f);
        if (!food.isEmpty()) {
            pose.pushPose();
            // Hang under the rod's end collar; rotation remains entirely client-side.
            pose.translate(GymFoodDisplay.X, foodY, .5);
            float spin = ((level.getGameTime() % 160) + partialTick) * 2.25f;
            pose.mulPose(Axis.YP.rotationDegrees(90 + spin));
            pose.scale(.38f, .38f, .38f);
            Minecraft.getInstance().getItemRenderer().renderStatic(food, ItemDisplayContext.FIXED,
                light, overlay, pose, buffers, gym.getLevel(), 0);
            pose.popPose();
        }
        var sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
            .apply(BELT_TEXTURE);
        // The belt is 13 model units wide. The atlas pads its 26 texels to a 32-pixel sprite.
        float beltVEnd = sprite.getV(26f / 32);
        var vertices = buffers
            .getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        float movement = ((gym.getLevel().getGameTime() + partialTick) * .035f * gym.getIntensity())
            % .25f;
        // Local +X points toward the controller. Scroll toward -X, opposite the runner.
        // Cover the static model's ribs, including cropped strips at both ends.
        for (int rib = 0; rib <= 8; rib++) {
            float origin = -2 + rib * .25f - movement;
            float start = Math.max(-2, origin);
            float end = Math.min(0, origin + .25f);
            if (end <= start)
                continue;
            float u0 = sprite.getU(start - origin), u1 = sprite.getU(end - origin);
            var matrix = pose.last();
            vertices.addVertex(matrix, start, .251f, .09375f).setColor(-1)
                .setUv(u0, sprite.getV0()).setOverlay(overlay).setLight(light)
                .setNormal(matrix, 0, 1, 0);
            vertices.addVertex(matrix, start, .251f, .90625f).setColor(-1)
                .setUv(u0, beltVEnd).setOverlay(overlay).setLight(light)
                .setNormal(matrix, 0, 1, 0);
            vertices.addVertex(matrix, end, .251f, .90625f).setColor(-1)
                .setUv(u1, beltVEnd).setOverlay(overlay).setLight(light)
                .setNormal(matrix, 0, 1, 0);
            vertices.addVertex(matrix, end, .251f, .09375f).setColor(-1)
                .setUv(u1, sprite.getV0()).setOverlay(overlay).setLight(light)
                .setNormal(matrix, 0, 1, 0);
        }
        pose.popPose();
    }
    private static void renderCord(PoseStack pose, MultiBufferSource buffers, int light,
        int overlay, float bottom) {
        var sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
            .apply(CORD_TEXTURE);
        var vertices = buffers
            .getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        var matrix = pose.last();
        // Two crossed, double-sided ribbons keep the thin cord visible at every angle.
        for (int side = 0; side < 2; side++) {
            float dx = side == 0 ? .006f : 0, dz = side == 1 ? .006f : 0;
            float nx = side == 1 ? 1 : 0, nz = side == 0 ? 1 : 0;
            vertices.addVertex(matrix, GymFoodDisplay.X - dx, bottom, .5f - dz).setColor(-1)
                .setUv(sprite.getU0(), sprite.getV1()).setOverlay(overlay).setLight(light)
                .setNormal(matrix, nx, 0, nz);
            vertices.addVertex(matrix, GymFoodDisplay.X + dx, bottom, .5f + dz).setColor(-1)
                .setUv(sprite.getU1(), sprite.getV1()).setOverlay(overlay).setLight(light)
                .setNormal(matrix, nx, 0, nz);
            vertices.addVertex(matrix, GymFoodDisplay.X + dx, GymFoodDisplay.CORD_TOP, .5f + dz)
                .setColor(-1)
                .setUv(sprite.getU1(), sprite.getV0()).setOverlay(overlay).setLight(light)
                .setNormal(matrix, nx, 0, nz);
            vertices.addVertex(matrix, GymFoodDisplay.X - dx, GymFoodDisplay.CORD_TOP, .5f - dz)
                .setColor(-1)
                .setUv(sprite.getU0(), sprite.getV0()).setOverlay(overlay).setLight(light)
                .setNormal(matrix, nx, 0, nz);
        }
    }
    @Override
    public AABB getRenderBoundingBox(AnimalGymEntity gym) {
        return gym.renderBounds();
    }
}
