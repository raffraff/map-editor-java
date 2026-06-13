package com.rose.mapeditor.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.rose.mapeditor.scene.MapScene;
import com.rose.mapeditor.scene.WaterSurface;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Animated water surfaces from IFO water blocks. */
public final class WaterRenderer implements Disposable {

    private static final int FRAME_COUNT = 24;
    private static final String FRAME_PATH = "3DDATA/JUNON/WATER/OCEAN01_%02d.DDS";

    private final ShaderProgram shader;
    private final Map<Integer, Mesh> meshCache = new HashMap<>();
    private final Texture[] frames = new Texture[FRAME_COUNT];
    private final Matrix4 identity = new Matrix4();

    private boolean framesLoaded;
    private float animationTime;

    public WaterRenderer() {
        shader = new ShaderProgram(
            Gdx.files.internal("shaders/simple_gdx.vert"),
            Gdx.files.internal("shaders/simple.frag")
        );
        if (!shader.isCompiled()) {
            throw new IllegalStateException("Water shader compile error: " + shader.getLog());
        }
        identity.idt();
    }

    public void update(float delta) {
        animationTime += delta;
    }

    public void render(MapScene scene, Matrix4 projView) {
        if (scene == null || scene.waterSurfaces.isEmpty()) {
            return;
        }

        ensureFramesLoaded();
        Texture frame = currentFrame();
        if (frame == null) {
            return;
        }

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);

        shader.bind();
        shader.setUniformMatrix("u_projView", projView);
        shader.setUniformf("u_alpha", 0.5f);
        shader.setUniformf("u_addition", 0f, 0f, 0f);
        shader.setUniformi("u_texture", 0);
        frame.bind(0);

        for (int i = 0; i < scene.waterSurfaces.size(); i++) {
            meshFor(scene.waterSurfaces.get(i)).render(shader, GL20.GL_TRIANGLES);
        }

        Gdx.gl.glDepthMask(true);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private Texture currentFrame() {
        int index = ((int) (animationTime * 10f)) % FRAME_COUNT;
        return frames[index];
    }

    private Mesh meshFor(WaterSurface surface) {
        int key = System.identityHashCode(surface);
        return meshCache.computeIfAbsent(key, k -> {
            Mesh mesh = new Mesh(true, 4, WaterSurface.QUAD_INDICES.length, WaterSurface.ATTRIBUTES);
            mesh.setVertices(surface.vertices);
            mesh.setIndices(WaterSurface.QUAD_INDICES);
            return mesh;
        });
    }

    private void ensureFramesLoaded() {
        if (framesLoaded) {
            return;
        }
        Path gameRoot = com.rose.mapeditor.GameData.get().getGameRoot();
        for (int i = 0; i < FRAME_COUNT; i++) {
            String relative = String.format(FRAME_PATH, i + 1);
            Path path = gameRoot.resolve(relative.replace('/', java.io.File.separatorChar));
            if (!Files.isRegularFile(path)) {
                Path alt = gameRoot.resolve(relative.toLowerCase().replace('/', java.io.File.separatorChar));
                if (Files.isRegularFile(alt)) {
                    path = alt;
                }
            }
            if (Files.isRegularFile(path)) {
                try {
                    frames[i] = new Texture(com.github.terefang.gdx.ddsdxt.load.DDSLoader.fromDdsToTexture(
                        Gdx.files.absolute(path.toString())));
                    frames[i].setWrap(Texture.TextureWrap.MirroredRepeat, Texture.TextureWrap.MirroredRepeat);
                } catch (Exception e) {
                    Gdx.app.error("WaterRenderer", "Failed loading " + path, e);
                }
            }
        }
        framesLoaded = true;
    }

    public void clearMapResources() {
        for (Mesh mesh : meshCache.values()) {
            mesh.dispose();
        }
        meshCache.clear();
    }

    public void disposeScene(MapScene scene) {
        if (scene == null) {
            return;
        }
        for (WaterSurface surface : scene.waterSurfaces) {
            Mesh mesh = meshCache.remove(System.identityHashCode(surface));
            if (mesh != null) {
                mesh.dispose();
            }
        }
    }

    @Override
    public void dispose() {
        shader.dispose();
        for (Mesh mesh : meshCache.values()) {
            mesh.dispose();
        }
        meshCache.clear();
        for (int i = 0; i < FRAME_COUNT; i++) {
            if (frames[i] != null) {
                frames[i].dispose();
                frames[i] = null;
            }
        }
        framesLoaded = false;
    }
}
