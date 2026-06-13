package com.rose.mapeditor.model;

import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.io.FileHandler;

import java.io.IOException;

/** Rose Online mesh (.ZMS). */
public final class Zms {

    public static final int FLOATS_PER_VERTEX = 11;
    public static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4;

    public static final VertexAttributes ATTRIBUTES = new VertexAttributes(
        new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
        new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
        new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0"),
        new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord1"),
        new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_vertexAlpha")
    );

    /** Default vertex alpha */
    private static final float DEFAULT_VERTEX_ALPHA = 1f;

    public static void copyVertex(Vertex from, Vertex to) {
        to.position.set(from.position);
        to.normal.set(from.normal);
        to.texCoord.set(from.texCoord);
        to.lightmapCoord.set(from.lightmapCoord);
        to.vertexAlpha = from.vertexAlpha;
    }

    public Vertex[] copyVertices() {
        Vertex[] copy = new Vertex[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            copy[i] = new Vertex();
            copyVertex(vertices[i], copy[i]);
        }
        return copy;
    }

    public static final class Vertex {
        public final Vector3 position = new Vector3();
        public final Vector3 normal = new Vector3();
        public final Vector2 texCoord = new Vector2();
        public final Vector2 lightmapCoord = new Vector2();
        public float vertexAlpha = 1f;
    }

    public String filePath;
    public Vertex[] vertices;
    public short[] indices;
    public short vertexCount;
    public short indexCount;

    public Zms() {
    }

    public Zms(String filePath) throws IOException {
        load(filePath);
    }

    public void load(String filePath) throws IOException {
        this.filePath = filePath;
        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.READING, null)) {
            fh.seek(8);
            int format = fh.readInt();
            fh.seek(fh.tell() + 24);

            short boneCount = fh.readShort();
            fh.seek(fh.tell() + boneCount * 2);

            vertexCount = fh.readShort();
            vertices = new Vertex[vertexCount];
            for (int i = 0; i < vertexCount; i++) {
                vertices[i] = new Vertex();
            }

            if ((format & 2) != 0) {
                for (int i = 0; i < vertexCount; i++) {
                    vertices[i].position.set(fh.readVector3());
                }
            }

            if ((format & 4) != 0) {
                for (int i = 0; i < vertexCount; i++) {
                    vertices[i].normal.set(fh.readVector3());
                }
            }

            if ((format & 8) != 0) {
                fh.seek(fh.tell() + 4L * vertexCount);
            }

            if ((format & 16) != 0 && (format & 32) != 0) {
                fh.seek(fh.tell() + 24L * vertexCount);
            }

            if ((format & 64) != 0) {
                fh.seek(fh.tell() + 12L * vertexCount);
            }

            int uvMaps = 0;
            if ((format & 128) != 0) uvMaps++;
            if ((format & 256) != 0) uvMaps++;
            if ((format & 512) != 0) uvMaps++;
            if ((format & 1024) != 0) uvMaps++;

            if (uvMaps >= 1) {
                for (int i = 0; i < vertexCount; i++) {
                    vertices[i].texCoord.set(fh.readFloat(), fh.readFloat());
                }
            }

            if (uvMaps >= 2) {
                for (int i = 0; i < vertexCount; i++) {
                    vertices[i].lightmapCoord.set(fh.readFloat(), fh.readFloat());
                }
            }

            indexCount = fh.readShort();
            indices = new short[indexCount * 3];
            for (int i = 0; i < indexCount; i++) {
                indices[i * 3] = fh.readShort();
                indices[i * 3 + 1] = fh.readShort();
                indices[i * 3 + 2] = fh.readShort();
            }
        }
    }

    public float[] toFloatArray() {
        return toFloatArray(vertices);
    }

    public float[] toFloatArray(Vertex[] source) {
        float[] data = new float[vertexCount * FLOATS_PER_VERTEX];
        for (int i = 0; i < vertexCount; i++) {
            writeVertex(source[i], data, i * FLOATS_PER_VERTEX);
        }
        return data;
    }

    public static void writeVertex(Vertex v, float[] data, int offset) {
        data[offset] = v.position.x;
        data[offset + 1] = v.position.y;
        data[offset + 2] = v.position.z;
        data[offset + 3] = v.normal.x;
        data[offset + 4] = v.normal.y;
        data[offset + 5] = v.normal.z;
        data[offset + 6] = v.texCoord.x;
        data[offset + 7] = v.texCoord.y;
        data[offset + 8] = v.lightmapCoord.x;
        data[offset + 9] = v.lightmapCoord.y;
        data[offset + 10] = v.vertexAlpha > 0f ? v.vertexAlpha : DEFAULT_VERTEX_ALPHA;
    }

    public static void writeVertexData(Zms zms, Vertex[] source, float[] data) {
        for (int i = 0; i < zms.vertexCount; i++) {
            writeVertex(source[i], data, i * FLOATS_PER_VERTEX);
        }
    }
}
