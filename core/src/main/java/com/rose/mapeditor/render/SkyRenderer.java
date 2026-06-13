package com.rose.mapeditor.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.rose.mapeditor.model.Zms;
import com.rose.mapeditor.scene.MapScene;

import java.util.HashMap;
import java.util.Map;

/** Renders the LIST_SKY dome mesh (clears background, depth off). */
public final class SkyRenderer implements Disposable {

    private final ShaderProgram shader;
    private final TextureCache textures = new TextureCache();
    private final Map<Zms, Mesh> meshCache = new HashMap<>();

    private String loadedModelPath;

    public SkyRenderer() {
        shader = new ShaderProgram(
            Gdx.files.internal("shaders/simple_rose.vert"),
            Gdx.files.internal("shaders/simple.frag")
        );
        if (!shader.isCompiled()) {
            throw new IllegalStateException("Sky shader compile error: " + shader.getLog());
        }
    }

    /**
     * Draws the sky and clears the colour buffer
     *
     * @return {@code true} if a sky was rendered
     */
    public boolean render(MapScene scene, Matrix4 skyCombined) {
        if (scene == null || scene.skyMesh == null || scene.skyTexturePath == null) {
            return false;
        }

        ensureGpuMesh(scene.skyMesh, scene.skyModelPath);

        Gdx.gl.glClearColor(127f / 255f, 127f / 255f, 127f / 255f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        Texture skyTexture = textures.get(scene.skyTexturePath);
        Mesh mesh = meshFor(scene.skyMesh);

        shader.bind();
        shader.setUniformMatrix("u_projView", skyCombined);
        shader.setUniformf("u_alpha", 1f);
        shader.setUniformf("u_addition", 0f, 0f, 0f);
        shader.setUniformi("u_texture", 0);
        skyTexture.bind(0);

        mesh.render(shader, GL20.GL_TRIANGLES);

        Gdx.gl.glDepthMask(true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        return true;
    }

    private Mesh meshFor(Zms zms) {
        return meshCache.computeIfAbsent(zms, z -> {
            Mesh mesh = new Mesh(true, z.vertexCount, z.indices.length, Zms.ATTRIBUTES);
            mesh.setVertices(z.toFloatArray());
            mesh.setIndices(z.indices);
            return mesh;
        });
    }

    private void ensureGpuMesh(Zms zms, String modelPath) {
        if (modelPath != null && modelPath.equals(loadedModelPath)) {
            return;
        }
        for (Mesh mesh : meshCache.values()) {
            mesh.dispose();
        }
        meshCache.clear();
        loadedModelPath = modelPath;
        if (zms != null) {
            meshFor(zms);
        }
    }

    public void clearMapResources() {
        textures.clear();
        for (Mesh mesh : meshCache.values()) {
            mesh.dispose();
        }
        meshCache.clear();
        loadedModelPath = null;
    }

    public void disposeScene(MapScene scene) {
        if (scene != null && scene.skyModelPath != null && scene.skyModelPath.equals(loadedModelPath)) {
            clearMapResources();
        }
    }

    @Override
    public void dispose() {
        clearMapResources();
        textures.dispose();
        shader.dispose();
    }
}
