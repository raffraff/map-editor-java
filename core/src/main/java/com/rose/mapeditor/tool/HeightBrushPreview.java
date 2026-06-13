package com.rose.mapeditor.tool;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.rose.mapeditor.render.GlState;

/**
 * Draws height brush inner/outer rings draped on the terrain surface.
 */
public final class HeightBrushPreview implements Disposable {

    private static final int CIRCLE_SEGMENTS = 64;
    private static final float LIFT = 0.35f;
    private static final float CELL_SIZE = 2.5f;
    private static final float LINE_HALF_WIDTH = 0.45f;
    private static final int MAX_VERTICES = 8192;
    private static final int FLOATS_PER_VERTEX = 3 + 4;

    private final ShaderProgram shader;
    private final Mesh mesh;
    private final float[] vertices = new float[MAX_VERTICES * FLOATS_PER_VERTEX];

    private int vertexFloatCount;

    public HeightBrushPreview() {
        shader = new ShaderProgram(
            Gdx.files.internal("shaders/brush.vert"),
            Gdx.files.internal("shaders/brush.frag")
        );
        if (!shader.isCompiled()) {
            throw new IllegalStateException("Brush overlay shader error: " + shader.getLog());
        }
        mesh = new Mesh(false, MAX_VERTICES, 0,
            VertexAttribute.Position(),
            VertexAttribute.ColorUnpacked());
    }

    public void render(Matrix4 projection, Vector3 centerGdx, HeightBrushSettings settings,
                       TerrainHeightGrid grid) {
        if (centerGdx == null || settings == null || grid == null) {
            return;
        }

        float inner = settings.innerRadius * CELL_SIZE;
        float outer = settings.outerRadius * CELL_SIZE;

        vertexFloatCount = 0;

        if (settings.shape == HeightBrushShape.SQUARE) {
            drawSquareRing(grid, centerGdx.x, centerGdx.z, inner, 0.2f, 0.55f, 1f, 1f);
            drawSquareRing(grid, centerGdx.x, centerGdx.z, outer, 1f, 0.2f, 0.2f, 1f);
        } else {
            drawCircleRing(grid, centerGdx.x, centerGdx.z, inner, 0.2f, 0.55f, 1f, 1f);
            drawCircleRing(grid, centerGdx.x, centerGdx.z, outer, 1f, 0.2f, 0.2f, 1f);
        }

        if (vertexFloatCount == 0) {
            return;
        }

        mesh.setVertices(vertices, 0, vertexFloatCount);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shader.bind();
        shader.setUniformMatrix("u_projViewMatrix", projection);
        mesh.render(shader, GL20.GL_TRIANGLES);

        GlState.resetForUi();
    }

    private float heightAt(TerrainHeightGrid grid, float gdxX, float gdxZ) {
        return grid.sampleHeightGdx(gdxX, gdxZ) + LIFT;
    }

    private void drawCircleRing(TerrainHeightGrid grid, float cx, float cz, float radius,
                                float r, float g, float b, float a) {
        float prevX = cx + radius;
        float prevZ = cz;
        float prevY = heightAt(grid, prevX, prevZ);
        for (int i = 1; i <= CIRCLE_SEGMENTS; i++) {
            float angle = (float) (Math.PI * 2.0 * i / CIRCLE_SEGMENTS);
            float x = cx + (float) Math.cos(angle) * radius;
            float z = cz + (float) Math.sin(angle) * radius;
            float y = heightAt(grid, x, z);
            addLine(prevX, prevY, prevZ, x, y, z, r, g, b, a);
            prevX = x;
            prevZ = z;
            prevY = y;
        }
    }

    private void drawSquareRing(TerrainHeightGrid grid, float cx, float cz, float half,
                                float r, float g, float b, float a) {
        float minX = cx - half;
        float maxX = cx + half;
        float minZ = cz - half;
        float maxZ = cz + half;
        drawGroundEdge(grid, minX, minZ, maxX, minZ, r, g, b, a);
        drawGroundEdge(grid, maxX, minZ, maxX, maxZ, r, g, b, a);
        drawGroundEdge(grid, maxX, maxZ, minX, maxZ, r, g, b, a);
        drawGroundEdge(grid, minX, maxZ, minX, minZ, r, g, b, a);
    }

    private void drawGroundEdge(TerrainHeightGrid grid,
                                float x0, float z0, float x1, float z1,
                                float r, float g, float b, float a) {
        float dx = x1 - x0;
        float dz = z1 - z0;
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        int steps = Math.max(1, (int) (length / CELL_SIZE));
        float prevX = x0;
        float prevZ = z0;
        float prevY = heightAt(grid, prevX, prevZ);
        for (int step = 1; step <= steps; step++) {
            float t = step / (float) steps;
            float x = x0 + dx * t;
            float z = z0 + dz * t;
            float y = heightAt(grid, x, z);
            addLine(prevX, prevY, prevZ, x, y, z, r, g, b, a);
            prevX = x;
            prevZ = z;
            prevY = y;
        }
    }

    private void addLine(float x0, float y0, float z0, float x1, float y1, float z1,
                         float r, float g, float b, float a) {
        float dx = x1 - x0;
        float dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4f) {
            return;
        }
        float px = -dz / len * LINE_HALF_WIDTH;
        float pz = dx / len * LINE_HALF_WIDTH;

        addVertex(x0 + px, y0, z0 + pz, r, g, b, a);
        addVertex(x1 + px, y1, z1 + pz, r, g, b, a);
        addVertex(x0 - px, y0, z0 - pz, r, g, b, a);

        addVertex(x1 + px, y1, z1 + pz, r, g, b, a);
        addVertex(x1 - px, y1, z1 - pz, r, g, b, a);
        addVertex(x0 - px, y0, z0 - pz, r, g, b, a);
    }

    private void addVertex(float x, float y, float z, float r, float g, float b, float a) {
        if (vertexFloatCount + FLOATS_PER_VERTEX > vertices.length) {
            return;
        }
        int i = vertexFloatCount;
        vertices[i++] = x;
        vertices[i++] = y;
        vertices[i++] = z;
        vertices[i++] = r;
        vertices[i++] = g;
        vertices[i++] = b;
        vertices[i++] = a;
        vertexFloatCount = i;
    }

    @Override
    public void dispose() {
        mesh.dispose();
        shader.dispose();
    }
}
