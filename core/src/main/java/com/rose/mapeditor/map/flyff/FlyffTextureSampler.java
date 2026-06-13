package com.rose.mapeditor.map.flyff;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Replicates Flyff {@code CLandscape::GetTextureID} for a cell within one land tile. */
public final class FlyffTextureSampler {

    private static final int PATCH_SIZE = FlyffLndReader.MAP_SIZE / FlyffLndReader.PATCHES_PER_SIDE;

    private FlyffTextureSampler() {
    }

    public static int resolveTextureId(List<FlyffLndReader.Layer> layers, int cellX, int cellZ) {
        if (layers == null || layers.isEmpty()) {
            return -1;
        }
        if (cellX < 0 || cellZ < 0 || cellX >= FlyffLndReader.MAP_SIZE || cellZ >= FlyffLndReader.MAP_SIZE) {
            return -1;
        }

        int patchX = cellX / PATCH_SIZE;
        int patchZ = cellZ / PATCH_SIZE;
        int patchOffset = patchX + patchZ * FlyffLndReader.PATCHES_PER_SIDE;
        int alphaOffset = cellX + cellZ * FlyffLndReader.MAP_SIZE;

        if (layers.size() == 1) {
            return layers.get(0).textureId;
        }

        int textureId = -1;
        int alpha = 0;
        int alphaCount = 0;

        for (int i = layers.size() - 1; i >= 1; i--) {
            FlyffLndReader.Layer layer = layers.get(i);
            if (!layer.patchEnable[patchOffset]) {
                continue;
            }
            int layerAlpha = Byte.toUnsignedInt(layer.alphaMap[alphaOffset]);
            alphaCount += layerAlpha;
            if (layerAlpha > alpha) {
                alpha = layerAlpha;
                textureId = layer.textureId;
                if (alpha > 127) {
                    return textureId;
                }
            }
        }

        if (textureId == -1 || alphaCount < 64) {
            return layers.get(0).textureId;
        }
        return textureId;
    }

    /** Dominant Flyff texture for a Rose 4x4-cell block inside a land tile. */
    public static int resolveForRoseCell(List<FlyffLndReader.Layer> layers, int subGx, int subGy,
                                         int roseTileX, int roseTileY) {
        int startX = subGx * FlyffLndReader.ROSE_CELLS_PER_LND + roseTileX * 4;
        int startZ = subGy * FlyffLndReader.ROSE_CELLS_PER_LND + roseTileY * 4;

        Map<Integer, Integer> counts = new HashMap<>();
        for (int dz = 0; dz < 4; dz++) {
            for (int dx = 0; dx < 4; dx++) {
                int textureId = resolveTextureId(layers, startX + dx, startZ + dz);
                if (textureId < 0) {
                    continue;
                }
                counts.merge(textureId, 1, Integer::sum);
            }
        }

        int bestId = -1;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestId = entry.getKey();
            }
        }

        if (bestId >= 0) {
            return bestId;
        }
        return layers.isEmpty() ? -1 : layers.get(0).textureId;
    }
}
