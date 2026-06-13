package com.rose.mapeditor.map.flyff;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads and caches decoded Flyff terrain textures. */
final class FlyffTextureImageCache {

    private final Path flyffRoot;
    private final FlyffTerrainRegistry registry;
    private final Map<Integer, FlyffTextureImage> images = new HashMap<>();

    FlyffTextureImageCache(Path flyffRoot, FlyffTerrainRegistry registry) {
        this.flyffRoot = flyffRoot;
        this.registry = registry;
    }

    FlyffTextureImage require(int flyffTextureId) throws IOException {
        FlyffTextureImage cached = images.get(flyffTextureId);
        if (cached != null) {
            return cached;
        }

        String filename = registry.filenameFor(flyffTextureId);
        if (filename == null || filename.isBlank()) {
            throw new IOException("Unknown Flyff terrain texture ID " + flyffTextureId);
        }

        Path sourcePath = FlyffRoseTextureCatalog.resolveFlyffTexturePath(flyffRoot, filename);
        if (!Files.isRegularFile(sourcePath)) {
            throw new IOException("Flyff terrain texture file not found for ID " + flyffTextureId + ": " + sourcePath);
        }

        FlyffTextureImage image = FlyffTgaToDds.loadBgra(sourcePath);
        images.put(flyffTextureId, image);
        return image;
    }

    boolean canLoad(int flyffTextureId) {
        String filename = registry.filenameFor(flyffTextureId);
        if (filename == null || filename.isBlank()) {
            return false;
        }
        return Files.isRegularFile(FlyffRoseTextureCatalog.resolveFlyffTexturePath(flyffRoot, filename));
    }

    static float[] compositeColorAt(List<FlyffLndReader.Layer> layers, int cellX, int cellZ,
                                    float u, float v, FlyffTextureImageCache cache) throws IOException {
        float[] color = new float[4];
        if (layers.isEmpty()) {
            return color;
        }

        cache.require(layers.get(0).textureId).sample(u, v, color);
        for (int i = 1; i < layers.size(); i++) {
            FlyffLndReader.Layer layer = layers.get(i);
            if (!layer.isEnabledAt(cellX, cellZ)) {
                continue;
            }
            float alpha = layer.alphaAt(cellX, cellZ);
            if (alpha <= 0f) {
                continue;
            }
            float[] layerColor = new float[4];
            cache.require(layer.textureId).sample(u, v, layerColor);
            lerpInPlace(color, layerColor, alpha);
        }
        return color;
    }

    static float overlayVisibilityAt(List<FlyffLndReader.Layer> layers, int cellX, int cellZ) {
        float visibility = 0f;
        for (int i = 1; i < layers.size(); i++) {
            FlyffLndReader.Layer layer = layers.get(i);
            if (!layer.isEnabledAt(cellX, cellZ)) {
                continue;
            }
            float alpha = layer.alphaAt(cellX, cellZ);
            if (alpha <= 0f) {
                continue;
            }
            visibility = visibility + alpha * (1f - visibility);
        }
        return visibility;
    }

    private static void lerpInPlace(float[] dst, float[] src, float t) {
        float inv = 1f - t;
        dst[0] = dst[0] * inv + src[0] * t;
        dst[1] = dst[1] * inv + src[1] * t;
        dst[2] = dst[2] * inv + src[2] * t;
        dst[3] = dst[3] * inv + src[3] * t;
    }
}
