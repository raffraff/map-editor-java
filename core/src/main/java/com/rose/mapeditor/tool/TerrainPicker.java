package com.rose.mapeditor.tool;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.rose.mapeditor.RoseCoords;
import com.rose.mapeditor.render.HeightmapBlock;

/** Raycasts against terrain tiles and returns the hit under the cursor. */
public final class TerrainPicker {

    private final Vector3 hitPoint = new Vector3();
    private final Vector3 v0 = new Vector3();
    private final Vector3 v1 = new Vector3();
    private final Vector3 v2 = new Vector3();
    private final Vector3 roseHit = new Vector3();

    /** Result of a successful terrain pick. */
    public static final class PickResult {
        public final Vector3 rose = new Vector3();
        public final Vector3 gdx = new Vector3();
    }

    /**
     * Returns the closest hit along the pick ray (first surface under the cursor).
     * {@code null} when nothing is hit.
     */
    public PickResult pick(Ray ray, HeightmapBlock[] blocks, int blockCount, PickResult out) {
        if (ray == null || blocks == null || blockCount <= 0 || out == null) {
            return null;
        }

        float bestT = Float.MAX_VALUE;
        Vector3 bestGdx = null;

        for (int b = 0; b < blockCount; b++) {
            HeightmapBlock block = blocks[b];
            if (block == null || block.vertices == null) {
                continue;
            }
            if (!Intersector.intersectRayBounds(ray, block.bounds, null)) {
                continue;
            }

            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    HeightmapBlock.TerrainTile tile = block.tiles[y][x];
                    if (!Intersector.intersectRayBounds(ray, tile.bounds, null)) {
                        continue;
                    }

                    int baseVertex = (y * 16 + x) * 25;
                    short[] strip = HeightmapBlock.STRIP_INDICES;
                    for (int i = 0; i < strip.length - 2; i++) {
                        readVertex(block.vertices, baseVertex + strip[i], v0);
                        readVertex(block.vertices, baseVertex + strip[i + 1], v1);
                        readVertex(block.vertices, baseVertex + strip[i + 2], v2);
                        if (!Intersector.intersectRayTriangle(ray, v0, v1, v2, hitPoint)) {
                            continue;
                        }

                        float t = (hitPoint.x - ray.origin.x) * ray.direction.x
                            + (hitPoint.y - ray.origin.y) * ray.direction.y
                            + (hitPoint.z - ray.origin.z) * ray.direction.z;
                        if (t <= 0f || t >= bestT) {
                            continue;
                        }

                        bestT = t;
                        bestGdx = hitPoint.cpy();
                    }
                }
            }
        }

        if (bestGdx == null) {
            return null;
        }

        out.gdx.set(bestGdx);
        out.rose.set(RoseCoords.toRose(bestGdx));
        return out;
    }

    private static void readVertex(float[] vertices, int vertexIndex, Vector3 out) {
        int o = vertexIndex * HeightmapBlock.FLOATS_PER_VERTEX;
        out.set(vertices[o], vertices[o + 1], vertices[o + 2]);
    }
}
