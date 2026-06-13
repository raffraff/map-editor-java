package com.rose.mapeditor.map;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Stitches mosaic sectors by cross-fading height profiles across seam bands, then splitting
 * back to per-sector HIM files so shared boundary vertices stay identical.
 * <p>
 * Rose sector names are {@code cellY_cellX}. In HIM data {@code position[dx][dy]}:
 * {@code dy} runs west→east (cellY axis), {@code dx} runs north→south (cellX axis).
 * Layout indices {@code (gy, gx)} map to {@code cellY = gy + 30}, {@code cellX = gx + 30}.
 */
public final class MosaicTerrainStitcher {

    private static final int VERTS_PER_SECTOR = 65;
    private static final int QUAD_CELLS_PER_SECTOR = 64;
    /** Height vertices cross-faded on each side of an internal seam. */
    private static final int HEIGHT_FADE = 48;
    /** Tile columns/rows copied from the neighbor at each seam. */
    private static final int TILE_BLEND = 8;
    private static final int SEAM_SMOOTH_RADIUS = 12;
    private static final int SEAM_SMOOTH_PASSES = 8;

    private MosaicTerrainStitcher() {
    }

    /** Stitches sector height/tile seams after multi-sector import or mosaic generation. */
    public static void stitchImportedSectors(Map<String, Him> hims, Map<String, Til> tils,
                                             int layoutSizeX, int layoutSizeY) throws IOException {
        stitch(hims, tils, layoutSizeX, layoutSizeY, true);
    }

    static void stitch(Map<String, Him> hims, Map<String, Til> tils, int sizeX, int sizeY,
                       boolean refineSeams) throws IOException {
        if (sizeX * sizeY <= 1) {
            return;
        }

        Map<String, float[][]> originals = snapshotPositions(hims);
        float[][] grid = buildCrossfadedGrid(originals, sizeX, sizeY);

        if (refineSeams) {
            smoothNearInternalSeams(grid, sizeX, sizeY);
        }

        writeGridToSectors(grid, hims, sizeX, sizeY);
        stitchTiles(tils, sizeX, sizeY);
    }

    static void saveAll(Map<String, Him> hims, Map<String, Path> himPaths,
                        Map<String, Til> tils, Map<String, Path> tilPaths) throws IOException {
        for (Map.Entry<String, Path> entry : himPaths.entrySet()) {
            Him him = hims.get(entry.getKey());
            if (him != null) {
                him.filePath = entry.getValue().toString();
                him.save();
            }
        }
        for (Map.Entry<String, Path> entry : tilPaths.entrySet()) {
            Til til = tils.get(entry.getKey());
            if (til != null) {
                til.save(entry.getValue().toString());
            }
        }
    }

    private static Map<String, float[][]> snapshotPositions(Map<String, Him> hims) {
        Map<String, float[][]> snapshots = new HashMap<>();
        for (Map.Entry<String, Him> entry : hims.entrySet()) {
            Him him = entry.getValue();
            if (him == null || him.position == null) {
                continue;
            }
            float[][] copy = new float[VERTS_PER_SECTOR][VERTS_PER_SECTOR];
            for (int row = 0; row < VERTS_PER_SECTOR; row++) {
                System.arraycopy(him.position[row], 0, copy[row], 0, VERTS_PER_SECTOR);
            }
            snapshots.put(entry.getKey(), copy);
        }
        return snapshots;
    }

    /**
     * Unified grid: row = {@code gx*64+dx} (north/south), col = {@code gy*64+dy} (west/east).
     */
    private static float[][] buildCrossfadedGrid(Map<String, float[][]> originals,
                                                 int sizeX, int sizeY) {
        int rows = sizeX * QUAD_CELLS_PER_SECTOR + 1;
        int cols = sizeY * QUAD_CELLS_PER_SECTOR + 1;
        float[][] grid = new float[rows][cols];

        for (int gy = 0; gy < sizeY; gy++) {
            for (int gx = 0; gx < sizeX; gx++) {
                float[][] source = originals.get(sectorName(gy, gx));
                if (source == null) {
                    continue;
                }
                for (int dx = 0; dx < VERTS_PER_SECTOR; dx++) {
                    for (int dy = 0; dy < VERTS_PER_SECTOR; dy++) {
                        grid[gx * QUAD_CELLS_PER_SECTOR + dx][gy * QUAD_CELLS_PER_SECTOR + dy]
                            = source[dx][dy];
                    }
                }
            }
        }

        for (int gy = 1; gy < sizeY; gy++) {
            for (int gx = 0; gx < sizeX; gx++) {
                crossfadeEastWestSeam(grid, originals, gy, gx);
            }
        }

        for (int gx = 1; gx < sizeX; gx++) {
            for (int gy = 0; gy < sizeY; gy++) {
                crossfadeNorthSouthSeam(grid, originals, gy, gx);
            }
        }

        fixInteriorCorners(grid, originals, sizeX, sizeY);
        return grid;
    }

    /** East-west seam between {@code (gy-1, gx)} and {@code (gy, gx)} along {@code dy}. */
    private static void crossfadeEastWestSeam(float[][] grid, Map<String, float[][]> originals,
                                              int gy, int gx) {
        float[][] west = originals.get(sectorName(gy - 1, gx));
        float[][] east = originals.get(sectorName(gy, gx));
        if (west == null || east == null) {
            return;
        }

        int seamCol = gy * QUAD_CELLS_PER_SECTOR;
        int rowBase = gx * QUAD_CELLS_PER_SECTOR;
        int fade = Math.min(HEIGHT_FADE, QUAD_CELLS_PER_SECTOR);

        for (int dx = 0; dx < VERTS_PER_SECTOR; dx++) {
            int row = rowBase + dx;
            float seamHeight = (west[dx][QUAD_CELLS_PER_SECTOR] + east[dx][0]) * 0.5f;
            grid[row][seamCol] = seamHeight;

            for (int k = 1; k <= fade; k++) {
                float weight = smoothStep(1f - (k - 1) / Math.max(1f, fade - 1f));
                int westCol = seamCol - k;
                int eastCol = seamCol + k;
                if (westCol >= 0) {
                    float westVal = west[dx][QUAD_CELLS_PER_SECTOR - k];
                    grid[row][westCol] = lerp(westVal, seamHeight, weight);
                }
                if (eastCol < grid[row].length) {
                    float eastVal = east[dx][k];
                    grid[row][eastCol] = lerp(eastVal, seamHeight, weight);
                }
            }
        }
    }

    /** North-south seam between {@code (gy, gx-1)} and {@code (gy, gx)} along {@code dx}. */
    private static void crossfadeNorthSouthSeam(float[][] grid, Map<String, float[][]> originals,
                                                int gy, int gx) {
        float[][] north = originals.get(sectorName(gy, gx - 1));
        float[][] south = originals.get(sectorName(gy, gx));
        if (north == null || south == null) {
            return;
        }

        int seamRow = gx * QUAD_CELLS_PER_SECTOR;
        int colBase = gy * QUAD_CELLS_PER_SECTOR;
        int fade = Math.min(HEIGHT_FADE, QUAD_CELLS_PER_SECTOR);

        for (int dy = 0; dy < VERTS_PER_SECTOR; dy++) {
            int col = colBase + dy;
            float seamHeight = (north[QUAD_CELLS_PER_SECTOR][dy] + south[0][dy]) * 0.5f;
            grid[seamRow][col] = seamHeight;

            for (int k = 1; k <= fade; k++) {
                float weight = smoothStep(1f - (k - 1) / Math.max(1f, fade - 1f));
                int northRow = seamRow - k;
                int southRow = seamRow + k;
                if (northRow >= 0) {
                    float northVal = north[QUAD_CELLS_PER_SECTOR - k][dy];
                    grid[northRow][col] = lerp(northVal, seamHeight, weight);
                }
                if (southRow < grid.length) {
                    float southVal = south[k][dy];
                    grid[southRow][col] = lerp(southVal, seamHeight, weight);
                }
            }
        }
    }

    /** Averages the single shared vertex where four sectors meet. */
    private static void fixInteriorCorners(float[][] grid, Map<String, float[][]> originals,
                                           int sizeX, int sizeY) {
        for (int gy = 1; gy < sizeY; gy++) {
            for (int gx = 1; gx < sizeX; gx++) {
                float[][] nw = originals.get(sectorName(gy - 1, gx - 1));
                float[][] ne = originals.get(sectorName(gy, gx - 1));
                float[][] sw = originals.get(sectorName(gy - 1, gx));
                float[][] se = originals.get(sectorName(gy, gx));
                if (nw == null || ne == null || sw == null || se == null) {
                    continue;
                }
                int row = gx * QUAD_CELLS_PER_SECTOR;
                int col = gy * QUAD_CELLS_PER_SECTOR;
                float avg = (nw[QUAD_CELLS_PER_SECTOR][QUAD_CELLS_PER_SECTOR]
                    + ne[QUAD_CELLS_PER_SECTOR][0]
                    + sw[0][QUAD_CELLS_PER_SECTOR]
                    + se[0][0]) * 0.25f;
                grid[row][col] = avg;
            }
        }
    }

    private static void writeGridToSectors(float[][] grid, Map<String, Him> hims,
                                           int sizeX, int sizeY) {
        for (int gy = 0; gy < sizeY; gy++) {
            for (int gx = 0; gx < sizeX; gx++) {
                Him him = sector(hims, gy, gx);
                if (him == null) {
                    continue;
                }
                for (int dx = 0; dx < VERTS_PER_SECTOR; dx++) {
                    for (int dy = 0; dy < VERTS_PER_SECTOR; dy++) {
                        him.position[dx][dy] = grid[gx * QUAD_CELLS_PER_SECTOR + dx]
                            [gy * QUAD_CELLS_PER_SECTOR + dy];
                    }
                }
            }
        }
    }

    /** Laplacian smooth near internal seams without moving exact seam lines. */
    private static void smoothNearInternalSeams(float[][] grid, int sizeX, int sizeY) {
        int rows = grid.length;
        int cols = grid[0].length;

        for (int pass = 0; pass < SEAM_SMOOTH_PASSES; pass++) {
            float[][] next = copyGrid(grid);
            for (int row = 1; row < rows - 1; row++) {
                for (int col = 1; col < cols - 1; col++) {
                    float dist = seamDistance(row, col, sizeX, sizeY);
                    if (dist <= 0f || dist > SEAM_SMOOTH_RADIUS) {
                        continue;
                    }
                    float avg = (grid[row - 1][col] + grid[row + 1][col]
                        + grid[row][col - 1] + grid[row][col + 1]) * 0.25f;
                    float blend = 1f - dist / SEAM_SMOOTH_RADIUS;
                    blend = blend * blend;
                    next[row][col] = lerp(grid[row][col], avg, 0.55f * blend);
                }
            }
            grid = next;
        }
    }

    private static float seamDistance(int row, int col, int sizeX, int sizeY) {
        float dist = Float.MAX_VALUE;

        for (int gy = 1; gy < sizeY; gy++) {
            dist = Math.min(dist, Math.abs(col - gy * QUAD_CELLS_PER_SECTOR));
        }
        for (int gx = 1; gx < sizeX; gx++) {
            dist = Math.min(dist, Math.abs(row - gx * QUAD_CELLS_PER_SECTOR));
        }
        return dist;
    }

    private static float[][] copyGrid(float[][] grid) {
        float[][] copy = new float[grid.length][grid[0].length];
        for (int row = 0; row < grid.length; row++) {
            System.arraycopy(grid[row], 0, copy[row], 0, grid[row].length);
        }
        return copy;
    }

    private static void stitchTiles(Map<String, Til> tils, int sizeX, int sizeY) {
        for (int gy = 0; gy < sizeY; gy++) {
            for (int gx = 0; gx < sizeX; gx++) {
                Til current = sectorTil(tils, gy, gx);
                if (current == null) {
                    continue;
                }
                if (gy > 0) {
                    Til west = sectorTil(tils, gy - 1, gx);
                    if (west != null) {
                        blendEastWestTileSeam(west, current, TILE_BLEND);
                    }
                }
                if (gx > 0) {
                    Til north = sectorTil(tils, gy, gx - 1);
                    if (north != null) {
                        blendNorthSouthTileSeam(north, current, TILE_BLEND);
                    }
                }
            }
        }

        unifyTileCorners(tils, sizeX, sizeY);
    }

    /** Copies west tile paint onto the east sector's west edge ({@code tiles[y][0..]}). */
    private static void blendEastWestTileSeam(Til west, Til east, int columns) {
        int cols = Math.min(columns, 16);
        for (int ty = 0; ty < 16; ty++) {
            east.tiles[ty][0] = copyTile(west.tiles[ty][15]);
            for (int c = 1; c < cols; c++) {
                int srcCol = 15 - (cols - 1 - c);
                east.tiles[ty][c] = copyTile(west.tiles[ty][srcCol]);
            }
        }
    }

    /** Copies north tile paint onto the south sector's north edge ({@code tiles[0..][x]}). */
    private static void blendNorthSouthTileSeam(Til north, Til south, int rows) {
        int blendRows = Math.min(rows, 16);
        for (int tx = 0; tx < 16; tx++) {
            south.tiles[0][tx] = copyTile(north.tiles[15][tx]);
            for (int r = 1; r < blendRows; r++) {
                int srcRow = 15 - (blendRows - 1 - r);
                south.tiles[r][tx] = copyTile(north.tiles[srcRow][tx]);
            }
        }
    }

    private static void unifyTileCorners(Map<String, Til> tils, int sizeX, int sizeY) {
        for (int gy = 1; gy < sizeY; gy++) {
            for (int gx = 1; gx < sizeX; gx++) {
                Til nw = sectorTil(tils, gy - 1, gx - 1);
                Til ne = sectorTil(tils, gy, gx - 1);
                Til sw = sectorTil(tils, gy - 1, gx);
                Til se = sectorTil(tils, gy, gx);
                if (nw == null || ne == null || sw == null || se == null) {
                    continue;
                }
                Til.Tile corner = copyTile(nw.tiles[15][15]);
                ne.tiles[15][0] = copyTile(corner);
                sw.tiles[0][15] = copyTile(corner);
                se.tiles[0][0] = copyTile(corner);
            }
        }
    }

    private static Til.Tile copyTile(Til.Tile source) {
        Til.Tile tile = new Til.Tile();
        tile.brushId = source.brushId;
        tile.tileIndex = source.tileIndex;
        tile.tileSetNumber = source.tileSetNumber;
        tile.tileId = source.tileId;
        return tile;
    }

    private static Him sector(Map<String, Him> hims, int gy, int gx) {
        return hims.get(sectorName(gy, gx));
    }

    private static Til sectorTil(Map<String, Til> tils, int gy, int gx) {
        return tils.get(sectorName(gy, gx));
    }

    private static String sectorName(int gy, int gx) {
        return (gy + 30) + "_" + (gx + 30);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smoothStep(float t) {
        float x = Math.max(0f, Math.min(1f, t));
        return x * x * (3f - 2f * x);
    }
}
