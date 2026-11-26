package net.modcrafters.minecraft.industrialoverkill.client;

import java.util.List;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

final class GymControllerModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Integer> GANTRY_LIGHT = new ModelProperty<>();

    GymControllerModel(BakedModel model) {
        super(model);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state,
        ModelData data) {
        var base = super.getModelData(level, pos, state, data);
        return base.derive().with(GANTRY_LIGHT, LevelRenderer.getLightColor(level, pos.above()))
            .build();
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random,
        ModelData data, RenderType renderType) {
        var quads = super.getQuads(state, side, random, data, renderType);
        var light = data.get(GANTRY_LIGHT);
        if (side != null || light == null || light == 0 || quads.isEmpty())
            return quads;

        // Vanilla merges baked light with its normal face light and still applies shading.
        var applyLight = QuadTransformers.applyingLightmap(light);
        return quads.stream().map(quad -> extendsAboveCasing(quad) ? applyLight.process(quad) : quad)
            .toList();
    }

    private static boolean extendsAboveCasing(BakedQuad quad) {
        var vertices = quad.getVertices();
        for (int vertex = 0; vertex < 4; vertex++) {
            int y = vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + 1;
            if (Float.intBitsToFloat(vertices[y]) > 1)
                return true;
        }
        return false;
    }
}
