package com.rose.mapeditor.map;

import com.rose.mapeditor.render.HeightmapBlock;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/** Bakes terrain plane lighting maps from HIM height data and writes Rose .DDS files. */
public final class TerrainLightmapBaker {

    public static final int SIZE = PlaneLightingMapWriter.SIZE;

    private static final int GRID = 64;
    private static final float CELL = 2.5f;
    private static final float LIGHT_X = 0.30f;
    private static final float LIGHT_Y = 0.90f;
    private static final float LIGHT_Z = 0.20f;
    private static final float AMBIENT = 0.38f;
    private static final float DIFFUSE = 0.62f;
    /** Matches Rose placeholder lightmaps and the terrain shader's {@code * 2.0} scale. */
    private static final float BASE = 136f;

    private TerrainLightmapBaker() {
    }

    /** Bakes and writes plane lightmaps for every loaded heightmap block. */
    public static int bakeBlocks(HeightmapBlock[] blocks, Path mapFolder) throws IOException {
        if (blocks == null || blocks.length == 0) {
            return 0;
        }
        int count = 0;
        for (HeightmapBlock block : blocks) {
            if (block == null || block.heightFile == null || block.heightFile.position == null) {
                continue;
            }
            byte[] pixels = bake(block.heightFile);
            Path himPath = Path.of(block.heightFile.filePath);
            Path mapRoot = PlaneLightmapPaths.resolveMapRoot(himPath);
            Path destination = PlaneLightmapPaths.resolvePlaneLightmapPath(mapRoot,
                (int) block.mapOffset.y, (int) block.mapOffset.x);
            PlaneLightingMapWriter.writeBgra8888(destination, pixels);
            count++;
        }
        return count;
    }

    /** Bakes plane lightmaps for named sectors (keys like {@code 30_31}). */
    public static int bakeSectors(Map<String, Him> hims, Path mapFolder) throws IOException {
        if (hims == null || hims.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Map.Entry<String, Him> entry : hims.entrySet()) {
            Him him = entry.getValue();
            if (him == null || him.position == null) {
                continue;
            }
            int[] coords = MapSectorCatalog.parseSectorCoords(entry.getKey());
            if (coords == null) {
                continue;
            }
            bakeToFile(him, PlaneLightmapPaths.resolvePlaneLightmapPath(mapFolder, coords[0], coords[1]));
            count++;
        }
        return count;
    }

    public static void bakeToFile(Him him, Path destination) throws IOException {
        byte[] pixels = bake(him);
        PlaneLightingMapWriter.writeBgra8888(destination, pixels);
    }

    public static Path resolvePlaneLightmapPath(Path mapFolder, int cellY, int cellX) {
        return PlaneLightmapPaths.resolvePlaneLightmapPath(mapFolder, cellY, cellX);
    }

    /** Returns 512×512 BGRA8888 pixels (row-major). */
    public static byte[] bake(Him him) {
        float[][] height = him.position;
        byte[] pixels = new byte[SIZE * SIZE * 4];
        float lightLen = (float) Math.sqrt(LIGHT_X * LIGHT_X + LIGHT_Y * LIGHT_Y + LIGHT_Z * LIGHT_Z);

        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                float dx = py / (float) (SIZE - 1) * GRID;
                float dy = px / (float) (SIZE - 1) * GRID;

                float hLeft = sampleHeight(height, dx, dy - 1f);
                float hRight = sampleHeight(height, dx, dy + 1f);
                float hNorth = sampleHeight(height, dx - 1f, dy);
                float hSouth = sampleHeight(height, dx + 1f, dy);

                float dhdEast = (hRight - hLeft) * 0.5f;
                float dhdSouth = (hSouth - hNorth) * 0.5f;

                float nx = -CELL * dhdEast;
                float ny = CELL * CELL;
                float nz = -CELL * dhdSouth;
                float nLen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (nLen > 1e-6f) {
                    nx /= nLen;
                    ny /= nLen;
                    nz /= nLen;
                } else {
                    nx = 0f;
                    ny = 1f;
                    nz = 0f;
                }

                float ndotl = (nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z) / lightLen;
                if (ndotl < 0f) {
                    ndotl = 0f;
                }

                float shade = AMBIENT + DIFFUSE * ndotl;
                shade *= 0.75f + 0.25f * ny;

                int value = Math.round(clamp(BASE * shade, 32f, 240f));
                int offset = (py * SIZE + px) * 4;
                pixels[offset] = (byte) value;
                pixels[offset + 1] = (byte) value;
                pixels[offset + 2] = (byte) value;
                pixels[offset + 3] = (byte) 255;
            }
        }
        return pixels;
    }

    private static float sampleHeight(float[][] height, float dx, float dy) {
        dx = clamp(dx, 0f, GRID);
        dy = clamp(dy, 0f, GRID);

        int x0 = (int) Math.floor(dx);
        int y0 = (int) Math.floor(dy);
        int x1 = Math.min(x0 + 1, GRID);
        int y1 = Math.min(y0 + 1, GRID);
        float tx = dx - x0;
        float ty = dy - y0;

        float h00 = height[x0][y0];
        float h10 = height[x1][y0];
        float h01 = height[x0][y1];
        float h11 = height[x1][y1];

        float h0 = h00 + (h10 - h00) * tx;
        float h1 = h01 + (h11 - h01) * tx;
        return h0 + (h1 - h0) * ty;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
