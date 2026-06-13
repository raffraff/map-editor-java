package com.rose.mapeditor.map.flyff;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads Flyff / landscape tiles (.lnd): heightmap, texture layers, and objects (skipped).
 */
public final class FlyffLndReader {

    public static final int MAP_SIZE = 128;
    public static final int SAMPLES_PER_SIDE = MAP_SIZE + 1;
    public static final int PATCHES_PER_SIDE = 16;
    public static final int ROSE_CELLS_PER_LND = 64;
    public static final int ALPHA_MAP_SIZE = MAP_SIZE * MAP_SIZE;

    public static final float HGT_NOWALK = 1000.0f;
    public static final float HGT_NOFLY = 2000.0f;
    public static final float HGT_NOMOVE = 3000.0f;
    public static final float HGT_DIE = 4000.0f;

    public static final class Layer {
        public int textureId;
        public final boolean[] patchEnable = new boolean[PATCHES_PER_SIDE * PATCHES_PER_SIDE];
        public final byte[] alphaMap = new byte[ALPHA_MAP_SIZE];

        public boolean isEnabledAt(int cellX, int cellZ) {
            if (cellX < 0 || cellZ < 0 || cellX >= MAP_SIZE || cellZ >= MAP_SIZE) {
                return false;
            }
            int patchX = cellX / (MAP_SIZE / PATCHES_PER_SIDE);
            int patchZ = cellZ / (MAP_SIZE / PATCHES_PER_SIDE);
            int patchOffset = patchX + patchZ * PATCHES_PER_SIDE;
            return patchEnable[patchOffset];
        }

        /** Overlay alpha in 0..1. */
        public float alphaAt(int cellX, int cellZ) {
            if (cellX < 0 || cellZ < 0 || cellX >= MAP_SIZE || cellZ >= MAP_SIZE) {
                return 0f;
            }
            return Byte.toUnsignedInt(alphaMap[cellX + cellZ * MAP_SIZE]) / 255f;
        }
    }

    public static final class LandscapeTile {
        /** Height samples indexed {@code [z][x]} (row = z, column = x). */
        public final float[][] heights = new float[SAMPLES_PER_SIDE][SAMPLES_PER_SIDE];
        public final List<Layer> layers = new ArrayList<>();

        public float at(int x, int z) {
            return heights[z][x];
        }

        public boolean isBlocked(int x, int z) {
            return heights[z][x] >= HGT_NOWALK;
        }
    }

    private static final int OBJECT_RECORD_BYTES = 64;

    private FlyffLndReader() {
    }

    public static LandscapeTile read(Path lndPath) throws IOException {
        if (!Files.isRegularFile(lndPath)) {
            throw new IOException("Landscape file not found: " + lndPath);
        }

        LandscapeTile tile = new LandscapeTile();
        try (FlyffBinaryReader file = new FlyffBinaryReader(lndPath)) {
            int version = file.readInt();
            if (version >= 1) {
                file.skip(8);
            }

            for (int z = 0; z < SAMPLES_PER_SIDE; z++) {
                for (int x = 0; x < SAMPLES_PER_SIDE; x++) {
                    tile.heights[z][x] = file.readFloat();
                }
            }

            file.skip(PATCHES_PER_SIDE * PATCHES_PER_SIDE * 2);
            if (version >= 2) {
                file.skip(PATCHES_PER_SIDE * PATCHES_PER_SIDE);
            }

            int layerCount = Byte.toUnsignedInt(file.readByte());
            for (int layer = 0; layer < layerCount; layer++) {
                Layer landLayer = new Layer();
                landLayer.textureId = file.readUnsignedShort();

                for (int patch = 0; patch < PATCHES_PER_SIDE * PATCHES_PER_SIDE; patch++) {
                    landLayer.patchEnable[patch] = file.readInt() != 0;
                }

                byte[] lightMap = new byte[ALPHA_MAP_SIZE * 4];
                for (int i = 0; i < lightMap.length; i++) {
                    lightMap[i] = file.readByte();
                }
                for (int j = 0; j < ALPHA_MAP_SIZE; j++) {
                    landLayer.alphaMap[j] = lightMap[j * 4 + 3];
                }

                tile.layers.add(landLayer);
            }

            skipObjectBlock(file);
            skipObjectBlock(file);
        }
        return tile;
    }

    private static void skipObjectBlock(FlyffBinaryReader file) {
        int count = file.readInt();
        for (int i = 0; i < count; i++) {
            file.skip(OBJECT_RECORD_BYTES);
        }
    }
}
