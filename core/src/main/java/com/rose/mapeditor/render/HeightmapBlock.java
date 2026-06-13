package com.rose.mapeditor.render;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Disposable;
import com.rose.mapeditor.TerrainUtil;
import com.rose.mapeditor.map.Him;
import com.rose.mapeditor.map.PlaneLightmapPaths;
import com.rose.mapeditor.map.Til;
import com.rose.mapeditor.map.Zone;

import java.io.File;
import java.nio.file.Path;

/** Single 16x16 terrain chunk (one .HIM / .TIL pair). */
public final class HeightmapBlock implements Disposable {

    public static final int FLOATS_PER_VERTEX = 9;
    public static final VertexAttributes ATTRIBUTES = new VertexAttributes(
        VertexAttribute.Position(),
        VertexAttribute.TexCoords(0),
        VertexAttribute.TexCoords(1),
        VertexAttribute.TexCoords(2)
    );

    public static final short[] STRIP_INDICES = {
        5, 0, 6, 1, 7, 2, 8, 3, 9, 4, 4, 10,
        10, 5, 11, 6, 12, 7, 13, 8, 14, 9,
        9, 15, 15, 10, 16, 11, 17, 12, 18,
        13, 19, 14, 14, 20, 20, 15, 21, 16,
        22, 17, 23, 18, 24, 19
    };

    /** Triangle-list index count when expanding {@link #STRIP_INDICES}. */
    public static final int TRIANGLE_INDICES_PER_TILE = (STRIP_INDICES.length - 2) * 3;

    /** Writes one tile strip as separate triangles (safe to concatenate across tiles). */
    public static void appendStripAsTriangles(int vertexOffset, short[] out, int outOffset) {
        for (int i = 0; i + 2 < STRIP_INDICES.length; i++) {
            if ((i & 1) == 0) {
                out[outOffset++] = (short) (STRIP_INDICES[i] + vertexOffset);
                out[outOffset++] = (short) (STRIP_INDICES[i + 1] + vertexOffset);
                out[outOffset++] = (short) (STRIP_INDICES[i + 2] + vertexOffset);
            } else {
                out[outOffset++] = (short) (STRIP_INDICES[i + 1] + vertexOffset);
                out[outOffset++] = (short) (STRIP_INDICES[i] + vertexOffset);
                out[outOffset++] = (short) (STRIP_INDICES[i + 2] + vertexOffset);
            }
        }
    }

    public static final class TerrainTile {
        public int tileId1;
        public int tileId2;
        public BoundingBox bounds = new BoundingBox();
    }

    public Him heightFile;
    public Til tileFile;
    public final Vector2 mapOffset = new Vector2();
    public final TerrainTile[][] tiles = new TerrainTile[16][16];
    public final BoundingBox bounds = new BoundingBox();

    public float[] vertices;
    public Texture shadowMap;
    /** Bumped when height/tile mesh data changes so render batches can rebuild. */
    public int renderGeneration;

    /** CPU-only preparation (safe on a background thread). */
    public static HeightmapBlock prepare(Him heightFile, Til tileFile, Zone zone) {
        HeightmapBlock block = new HeightmapBlock();
        block.heightFile = heightFile;
        block.tileFile = tileFile;
        block.initMapOffset();
        block.initTiles();
        block.buildVertices(zone);
        block.buildTileMetadata(zone);
        return block;
    }

    /** Uploads OpenGL resources; must run on the libGDX render thread. */
    public void finishGpuLoad() {
        if (shadowMap != null) {
            return;
        }
        shadowMap = loadShadowMap(heightFile.filePath);
    }

    private HeightmapBlock() {
    }

    /** Full load on the render thread (legacy synchronous path). */
    public HeightmapBlock(Him heightFile, Til tileFile, Zone zone, Path gameRoot) {
        this.heightFile = heightFile;
        this.tileFile = tileFile;
        initMapOffset();
        initTiles();
        buildVertices(zone);
        buildTileMetadata(zone);
        shadowMap = loadShadowMap(heightFile.filePath);
    }

    private void initMapOffset() {
        String fileName = new File(heightFile.filePath).getName();
        String[] position = fileName.substring(0, 5).split("_");
        mapOffset.set(Integer.parseInt(position[1]), Integer.parseInt(position[0]));
    }

    private void initTiles() {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                tiles[y][x] = new TerrainTile();
            }
        }
    }

    private Texture loadShadowMap(String himPath) {
        Path him = Path.of(himPath);
        int cellY = (int) mapOffset.y;
        int cellX = (int) mapOffset.x;
        for (Path candidate : PlaneLightmapPaths.listCandidates(him, cellY, cellX)) {
            if (candidate.toFile().exists()) {
                return PlaneLightingMapUtil.load(candidate);
            }
        }
        return PlaneLightingMapUtil.createNeutralTexture();
    }

    /** Rebuilds CPU mesh data after height edits. */
    public void rebuild(Zone zone) {
        buildVertices(zone);
        buildTileMetadata(zone);
        renderGeneration++;
    }

    /** Uploads freshly baked BGRA pixels to the GPU shadow texture (render thread). */
    public void applyShadowMapPixels(byte[] bgra) {
        if (shadowMap != null) {
            shadowMap.dispose();
        }
        shadowMap = PlaneLightingMapUtil.textureFromBgra(bgra);
    }

    /** Reloads the plane lighting DDS after it was regenerated on disk. Must run on the render thread. */
    public void reloadShadowMap() {
        if (shadowMap != null) {
            shadowMap.dispose();
            shadowMap = null;
        }
        shadowMap = loadShadowMap(heightFile.filePath);
    }

    private void buildVertices(Zone zone) {
        vertices = new float[6400 * FLOATS_PER_VERTEX];

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                Zone.RotationType rotation = zone.tiles.get(tileFile.tiles[y][x].tileId).rotation;
                for (int vy = 0; vy < 5; vy++) {
                    for (int vx = 0; vx < 5; vx++) {
                        int dx4 = y * 4 + vx;
                        int dy4 = x * 4 + vy;
                        int vertex = ((y * 16 + x) * 25 + vy * 5 + vx);
                        int o = vertex * FLOATS_PER_VERTEX;

                        float roseX = dy4 * 2.5f + mapOffset.y * 160.0f;
                        float roseY = 10400.0f - (dx4 * 2.5f + mapOffset.x * 160.0f);
                        float roseZ = heightFile.position[dx4][dy4];
                        // Store libGDX coordinates (same mapping as object.vert / RoseCoords.toGdx)
                        vertices[o] = roseX;
                        vertices[o + 1] = roseZ;
                        vertices[o + 2] = roseY;

                        // Rose Y is inverted when mapped to libGDX Z; flip V so tile UVs align on the ground plane.
                        float texU = vy / 4.0f;
                        float texV = 1.0f - vx / 4.0f;
                        Vector2 bottom = new Vector2(texU, texV);
                        Vector2 top = TerrainUtil.rotationToCoordinates(rotation, new Vector2(texU, texV));
                        Vector2 shadow = new Vector2(dy4 / 64.0f, dx4 / 64.0f);

                        vertices[o + 3] = bottom.x;
                        vertices[o + 4] = bottom.y;
                        vertices[o + 5] = top.x;
                        vertices[o + 6] = top.y;
                        vertices[o + 7] = shadow.x;
                        vertices[o + 8] = shadow.y;
                    }
                }
            }
        }
    }

    private void buildTileMetadata(Zone zone) {
        Vector3 min = new Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        Vector3 max = new Vector3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int[] numbers = new int[25];
                for (int j = 0; j < 25; j++) {
                    numbers[j] = (y * 16 + x) * 25 + j;
                }

                Vector3 tileMin = new Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
                Vector3 tileMax = new Vector3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
                for (int n : numbers) {
                    int o = n * FLOATS_PER_VERTEX;
                    tileMin.x = Math.min(tileMin.x, vertices[o]);
                    tileMin.y = Math.min(tileMin.y, vertices[o + 1]);
                    tileMin.z = Math.min(tileMin.z, vertices[o + 2]);
                    tileMax.x = Math.max(tileMax.x, vertices[o]);
                    tileMax.y = Math.max(tileMax.y, vertices[o + 1]);
                    tileMax.z = Math.max(tileMax.z, vertices[o + 2]);
                }

                TerrainTile tile = tiles[y][x];
                tile.bounds.set(tileMin, tileMax);
                tile.tileId1 = zone.tiles.get(tileFile.tiles[y][x].tileId).id1();
                tile.tileId2 = zone.tiles.get(tileFile.tiles[y][x].tileId).id2();

                min.x = Math.min(min.x, tileMin.x);
                min.y = Math.min(min.y, tileMin.y);
                min.z = Math.min(min.z, tileMin.z);
                max.x = Math.max(max.x, tileMax.x);
                max.y = Math.max(max.y, tileMax.y);
                max.z = Math.max(max.z, tileMax.z);
            }
        }

        bounds.set(min, max);
    }

    @Override
    public void dispose() {
        if (shadowMap != null) {
            shadowMap.dispose();
            shadowMap = null;
        }
    }
}
