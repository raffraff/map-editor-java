package com.rose.mapeditor.model;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.io.FileHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes Rose Online mesh files (.ZMS) in the v7 layout */
public final class ZmsWriter {

    public static final int FORMAT_POSITION = 0x02;
    public static final int FORMAT_NORMAL = 0x04;
    public static final int FORMAT_UV0 = 0x80;

    private ZmsWriter() {
    }

    public static void write(Path destination, MeshData mesh) throws IOException {
        if (mesh.vertices.length == 0 || mesh.triangleCount == 0) {
            throw new IllegalArgumentException("Cannot write empty ZMS: " + destination);
        }
        if (mesh.vertices.length > Short.MAX_VALUE) {
            throw new IOException("Vertex count exceeds ZMS limit: " + mesh.vertices.length);
        }
        if (mesh.triangleCount > Short.MAX_VALUE) {
            throw new IOException("Triangle count exceeds ZMS limit: " + mesh.triangleCount);
        }

        Files.createDirectories(destination.getParent());
        int format = FORMAT_POSITION | FORMAT_NORMAL | FORMAT_UV0;
        Vector3 boundsMin = mesh.boundsMin == null ? computeBoundsMin(mesh.vertices) : mesh.boundsMin;
        Vector3 boundsMax = mesh.boundsMax == null ? computeBoundsMax(mesh.vertices) : mesh.boundsMax;

        try (FileHandler fh = new FileHandler(destination.toString(), FileHandler.FileOpenMode.WRITING, null)) {
            fh.writeBytes("ZMS0007".getBytes(StandardCharsets.US_ASCII));
            fh.writeByte((byte) 0);
            fh.writeInt(format);
            fh.writeVector3(boundsMin);
            fh.writeVector3(boundsMax);
            fh.writeShort((short) 0);
            fh.writeShort((short) mesh.vertices.length);

            for (VertexData vertex : mesh.vertices) {
                fh.writeVector3(vertex.position);
            }
            for (VertexData vertex : mesh.vertices) {
                fh.writeVector3(vertex.normal);
            }
            for (VertexData vertex : mesh.vertices) {
                fh.writeVector2(vertex.texCoord);
            }

            fh.writeShort((short) mesh.triangleCount);
            for (int i = 0; i < mesh.indices.length; i++) {
                fh.writeShort(mesh.indices[i]);
            }
        }
    }

    public static final class MeshData {
        public VertexData[] vertices = new VertexData[0];
        public short[] indices = new short[0];
        public int triangleCount;
        public Vector3 boundsMin;
        public Vector3 boundsMax;
    }

    public static final class VertexData {
        public final Vector3 position = new Vector3();
        public final Vector3 normal = new Vector3();
        public final Vector2 texCoord = new Vector2();
    }

    private static Vector3 computeBoundsMin(VertexData[] vertices) {
        Vector3 min = new Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        for (VertexData vertex : vertices) {
            min.x = Math.min(min.x, vertex.position.x);
            min.y = Math.min(min.y, vertex.position.y);
            min.z = Math.min(min.z, vertex.position.z);
        }
        return min;
    }

    private static Vector3 computeBoundsMax(VertexData[] vertices) {
        Vector3 max = new Vector3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        for (VertexData vertex : vertices) {
            max.x = Math.max(max.x, vertex.position.x);
            max.y = Math.max(max.y, vertex.position.y);
            max.z = Math.max(max.z, vertex.position.z);
        }
        return max;
    }
}
