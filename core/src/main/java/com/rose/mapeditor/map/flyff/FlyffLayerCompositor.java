package com.rose.mapeditor.map.flyff;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Bakes Flyff multi-layer alpha maps into Rose 2-layer terrain blend patches.
 * Rose uses {@code mix(bottom, top, top.alpha)} - overlay strength is stored in the top DDS alpha channel.
 */
public final class FlyffLayerCompositor {

    /** Resolution of the baked top-texture patch per Rose TIL cell. */
    public static final int PATCH_SIZE = 64;
    /** Minimum overlay visibility before enabling Rose {@code blending}. */
    public static final float BLEND_THRESHOLD = 8f / 255f;

    public static final class CellBlendPatch {
        public int baseFlyffTextureId;
        public int topFlyffTextureId;
        public byte[] topBgra;
        public boolean blending;

        public int topWidth() {
            return PATCH_SIZE;
        }

        public int topHeight() {
            return PATCH_SIZE;
        }
    }

    private FlyffLayerCompositor() {
    }

    public static CellBlendPatch bakeRoseCell(List<FlyffLndReader.Layer> layers, int subGx, int subGy,
                                              int roseTileX, int roseTileY,
                                              FlyffTextureImageCache cache) throws IOException {
        CellBlendPatch patch = new CellBlendPatch();
        if (layers == null || layers.isEmpty()) {
            patch.blending = false;
            return patch;
        }

        patch.baseFlyffTextureId = layers.get(0).textureId;
        patch.topFlyffTextureId = dominantOverlayTextureId(layers, subGx, subGy, roseTileX, roseTileY);
        patch.topBgra = new byte[PATCH_SIZE * PATCH_SIZE * 4];

        float maxVisibility = 0f;
        float[] baseColor = new float[4];
        float[] finalColor = new float[4];
        float[] topColor = new float[4];

        for (int py = 0; py < PATCH_SIZE; py++) {
            for (int px = 0; px < PATCH_SIZE; px++) {
                float u = (px + 0.5f) / PATCH_SIZE;
                float v = (py + 0.5f) / PATCH_SIZE;

                int cellX = subGx * FlyffLndReader.ROSE_CELLS_PER_LND + roseTileX * 4
                    + Math.min(3, (int) (u * 4f));
                int cellZ = subGy * FlyffLndReader.ROSE_CELLS_PER_LND + roseTileY * 4
                    + Math.min(3, (int) (v * 4f));

                cache.require(patch.baseFlyffTextureId).sample(u, v, baseColor);
                float[] composed = FlyffTextureImageCache.compositeColorAt(
                    layers, cellX, cellZ, u, v, cache);
                System.arraycopy(composed, 0, finalColor, 0, 4);
                float visibility = FlyffTextureImageCache.overlayVisibilityAt(layers, cellX, cellZ);
                maxVisibility = Math.max(maxVisibility, visibility);

                if (visibility < BLEND_THRESHOLD) {
                    writeBgraPixel(patch.topBgra, px, py, baseColor, 0);
                    continue;
                }

                solveTopColor(baseColor, finalColor, visibility, topColor);
                writeBgraPixel(patch.topBgra, px, py, topColor, Math.round(visibility * 255f));
            }
        }

        patch.blending = maxVisibility >= BLEND_THRESHOLD;
        return patch;
    }

    private static void solveTopColor(float[] base, float[] finalColor, float visibility, float[] topOut) {
        float inv = 1f - visibility;
        for (int i = 0; i < 3; i++) {
            float solved = (finalColor[i] - base[i] * inv) / visibility;
            topOut[i] = clamp01(solved);
        }
        topOut[3] = 1f;
    }

    private static void writeBgraPixel(byte[] dest, int x, int y, float[] rgba, int alphaByte) {
        int offset = (y * PATCH_SIZE + x) * 4;
        dest[offset] = (byte) Math.round(clamp01(rgba[2]) * 255f);
        dest[offset + 1] = (byte) Math.round(clamp01(rgba[1]) * 255f);
        dest[offset + 2] = (byte) Math.round(clamp01(rgba[0]) * 255f);
        dest[offset + 3] = (byte) alphaByte;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int dominantOverlayTextureId(List<FlyffLndReader.Layer> layers, int subGx, int subGy,
                                                int roseTileX, int roseTileY) {
        int startX = subGx * FlyffLndReader.ROSE_CELLS_PER_LND + roseTileX * 4;
        int startZ = subGy * FlyffLndReader.ROSE_CELLS_PER_LND + roseTileY * 4;

        int bestId = -1;
        float bestScore = 0f;
        for (int dz = 0; dz < 4; dz++) {
            for (int dx = 0; dx < 4; dx++) {
                int cellX = startX + dx;
                int cellZ = startZ + dz;
                for (int i = 1; i < layers.size(); i++) {
                    FlyffLndReader.Layer layer = layers.get(i);
                    if (!layer.isEnabledAt(cellX, cellZ)) {
                        continue;
                    }
                    float alpha = layer.alphaAt(cellX, cellZ);
                    if (alpha > bestScore) {
                        bestScore = alpha;
                        bestId = layer.textureId;
                    }
                }
            }
        }
        return bestId;
    }

    /** Hash for deduplicating identical blend patches. */
    public static int patchSignature(CellBlendPatch patch) {
        int result = patch.baseFlyffTextureId;
        result = 31 * result + patch.topFlyffTextureId;
        result = 31 * result + (patch.blending ? 1 : 0);
        if (patch.blending && patch.topBgra != null) {
            result = 31 * result + Arrays.hashCode(patch.topBgra);
        }
        return result;
    }
}
