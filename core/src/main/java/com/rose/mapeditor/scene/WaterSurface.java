package com.rose.mapeditor.scene;

import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.RoseCoords;
import com.rose.mapeditor.map.Ifo;

/** Horizontal water quad in libGDX space */
public final class WaterSurface {

    public static final int FLOATS_PER_VERTEX = 5;
    public static final VertexAttributes ATTRIBUTES = new VertexAttributes(
        VertexAttribute.Position(),
        VertexAttribute.TexCoords(0)
    );

    public static final short[] QUAD_INDICES = {0, 3, 2, 2, 1, 0};

    public final float[] vertices = new float[4 * FLOATS_PER_VERTEX];

    public static WaterSurface fromVolume(Ifo.WaterVolume volume) {
        WaterSurface surface = new WaterSurface();

        float minX = volume.minimum.x;
        float minY = volume.minimum.y;
        float maxX = volume.maximum.x;
        float maxY = volume.maximum.y;
        float height = volume.minimum.z;

        writeCorner(surface, 0, minX, minY, height, minX / 16f, minY / 16f);
        writeCorner(surface, 1, maxX, minY, height, maxX / 16f, minY / 16f);
        writeCorner(surface, 2, maxX, maxY, height, maxX / 16f, maxY / 16f);
        writeCorner(surface, 3, minX, maxY, height, minX / 16f, maxY / 16f);
        return surface;
    }

    private static void writeCorner(WaterSurface surface, int index, float roseX, float roseY, float roseZ,
                                    float texU, float texV) {
        Vector3 gdx = RoseCoords.toGdx(roseX, roseY, roseZ);
        int offset = index * FLOATS_PER_VERTEX;
        surface.vertices[offset] = gdx.x;
        surface.vertices[offset + 1] = gdx.y;
        surface.vertices[offset + 2] = gdx.z;
        surface.vertices[offset + 3] = texU;
        surface.vertices[offset + 4] = texV;
    }
}
