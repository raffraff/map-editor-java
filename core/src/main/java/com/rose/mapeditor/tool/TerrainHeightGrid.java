package com.rose.mapeditor.tool;

import com.rose.mapeditor.map.Zone;
import com.rose.mapeditor.render.HeightmapBlock;

import java.util.HashSet;
import java.util.Set;

/**
 * Global height sample access across loaded HIM blocks
 * Cell coordinates match Rose ground plane units / 2.5
 */
public final class TerrainHeightGrid {

    private static final float WORLD_HEIGHT = 4160f;
    private static final int CELLS_PER_BLOCK = 64;

    private final HeightmapBlock[] blocks;
    private final Zone zone;

    public TerrainHeightGrid(HeightmapBlock[] blocks, Zone zone) {
        this.blocks = blocks;
        this.zone = zone;
    }

    public HeightmapBlock findBlock(int sectorY, int sectorX) {
        if (blocks == null) {
            return null;
        }
        for (HeightmapBlock block : blocks) {
            if (block == null) {
                continue;
            }
            if ((int) block.mapOffset.y == sectorY && (int) block.mapOffset.x == sectorX) {
                return block;
            }
        }
        return null;
    }

    public float getHeight(int cellX, int cellY) {
        boolean[] found = new boolean[1];
        return getHeight(cellX, cellY, found);
    }

    public float getHeight(int cellX, int cellY, boolean[] foundHeight) {
        foundHeight[0] = false;
        int sectorY = cellX / CELLS_PER_BLOCK;
        int sectorX = (int) ((WORLD_HEIGHT - cellY) / CELLS_PER_BLOCK);
        int blockX = cellX - sectorY * CELLS_PER_BLOCK;
        int blockY = (int) ((WORLD_HEIGHT - cellY) - sectorX * CELLS_PER_BLOCK);

        HeightmapBlock block = findBlock(sectorY, sectorX);
        if (block == null || block.heightFile == null || block.heightFile.position == null) {
            return 0f;
        }
        if (blockY < 0 || blockY >= block.heightFile.position.length
            || blockX < 0 || blockX >= block.heightFile.position[0].length) {
            return 0f;
        }
        foundHeight[0] = true;
        return block.heightFile.position[blockY][blockX];
    }

    /** Bilinear height sample at libGDX ground coordinates (returns elevation for GDX Y). */
    public float sampleHeightGdx(float gdxX, float gdxZ) {
        float fx = gdxX / 2.5f;
        float fy = gdxZ / 2.5f;
        int ix = (int) Math.floor(fx);
        int iy = (int) Math.floor(fy);
        float tx = fx - ix;
        float ty = fy - iy;

        float h00 = getHeight(ix, iy);
        float h10 = getHeight(ix + 1, iy);
        float h01 = getHeight(ix, iy + 1);
        float h11 = getHeight(ix + 1, iy + 1);

        float h0 = h00 + (h10 - h00) * tx;
        float h1 = h01 + (h11 - h01) * tx;
        return h0 + (h1 - h0) * ty;
    }

    public void setHeight(int cellX, int cellY, float height, Set<HeightmapBlock> touched) {
        int sectorY = cellX / CELLS_PER_BLOCK;
        int sectorX = (int) ((WORLD_HEIGHT - cellY) / CELLS_PER_BLOCK);
        int blockX = cellX - sectorY * CELLS_PER_BLOCK;
        int blockY = (int) ((WORLD_HEIGHT - cellY) - sectorX * CELLS_PER_BLOCK);

        HeightmapBlock block = findBlock(sectorY, sectorX);
        if (block != null && block.heightFile != null && block.heightFile.position != null) {
            if (blockY >= 0 && blockY < block.heightFile.position.length
                && blockX >= 0 && blockX < block.heightFile.position[0].length) {
                block.heightFile.position[blockY][blockX] = height;
                touched.add(block);
            }
        }

        if (blockX == 0) {
            HeightmapBlock west = findBlock(sectorY - 1, sectorX);
            if (west != null && west.heightFile != null) {
                west.heightFile.position[blockY][CELLS_PER_BLOCK] = height;
                touched.add(west);
            }
        }

        if (blockY == 0) {
            HeightmapBlock north = findBlock(sectorY, sectorX - 1);
            if (north != null && north.heightFile != null) {
                north.heightFile.position[CELLS_PER_BLOCK][blockX] = height;
                touched.add(north);
            }
            if (blockX == 0) {
                HeightmapBlock northWest = findBlock(sectorY - 1, sectorX - 1);
                if (northWest != null && northWest.heightFile != null) {
                    northWest.heightFile.position[CELLS_PER_BLOCK][CELLS_PER_BLOCK] = height;
                    touched.add(northWest);
                }
            }
        }
    }

    public void rebuild(Set<HeightmapBlock> touched) {
        if (zone == null || touched == null) {
            return;
        }
        for (HeightmapBlock block : touched) {
            if (block != null) {
                block.rebuild(zone);
            }
        }
    }

    public int saveAllModified() throws java.io.IOException {
        if (blocks == null) {
            return 0;
        }
        Set<String> saved = new HashSet<>();
        int count = 0;
        for (HeightmapBlock block : blocks) {
            if (block == null || block.heightFile == null || block.heightFile.filePath == null) {
                continue;
            }
            if (saved.add(block.heightFile.filePath)) {
                block.heightFile.save();
                count++;
            }
        }
        return count;
    }
}
