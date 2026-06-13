package com.rose.mapeditor.render;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.rose.mapeditor.model.Zms;

/** Builds lightweight position+UV meshes from ZMS data. */
public final class SimpleMeshFactory {

    public static final int FLOATS_PER_VERTEX = 5;
    public static final VertexAttributes ATTRIBUTES = new VertexAttributes(
        VertexAttribute.Position(),
        VertexAttribute.TexCoords(0)
    );

    private SimpleMeshFactory() {
    }

    public static Mesh fromZms(Zms zms) {
        float[] vertices = new float[zms.vertexCount * FLOATS_PER_VERTEX];
        for (int i = 0; i < zms.vertexCount; i++) {
            Zms.Vertex vertex = zms.vertices[i];
            int offset = i * FLOATS_PER_VERTEX;
            vertices[offset] = vertex.position.x;
            vertices[offset + 1] = vertex.position.y;
            vertices[offset + 2] = vertex.position.z;
            vertices[offset + 3] = vertex.texCoord.x;
            vertices[offset + 4] = vertex.texCoord.y;
        }

        Mesh mesh = new Mesh(true, zms.vertexCount, zms.indices.length, ATTRIBUTES);
        mesh.setVertices(vertices);
        mesh.setIndices(zms.indices);
        return mesh;
    }
}
