package com.rose.mapeditor.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.rose.mapeditor.model.Zms;
import com.rose.mapeditor.scene.AnimatedMeshRuntime;
import com.rose.mapeditor.scene.MapScene;
import com.rose.mapeditor.scene.MeshInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Renders ZMS mesh instances with the object shader (lightmap optional). */
public final class ObjectRenderer implements Disposable {

    private final ShaderProgram shader;
    private final Map<Zms, Mesh> meshCache = new HashMap<>();
    private final TextureCache textures = new TextureCache();
    private final Vector3 sortPosition = new Vector3();
    private final Matrix4 renderWorld = new Matrix4();
    private final Vector3 motionScaleScratch = new Vector3();

    private boolean masterLightmapEnabled = true;
    private RenderOptions renderOptions = new RenderOptions();

    private final List<Zms> preloadMeshes = new ArrayList<>();
    private final List<String> preloadTextures = new ArrayList<>();
    private int preloadMeshIndex;
    private int preloadTextureIndex;

    public ObjectRenderer() {
        shader = new ShaderProgram(
            Gdx.files.internal("shaders/object.vert"),
            Gdx.files.internal("shaders/object.frag")
        );
        if (!shader.isCompiled()) {
            throw new IllegalStateException("Object shader compile error: " + shader.getLog());
        }
    }

    public Mesh meshFor(MeshInstance instance) {
        if (instance.animation != null && instance.animation.morphVertices && instance.animation.gpuMesh != null) {
            return instance.animation.gpuMesh;
        }
        return meshFor(instance.mesh);
    }

    public Mesh meshFor(Zms zms) {
        return meshCache.computeIfAbsent(zms, z -> {
            Mesh mesh = new Mesh(true, z.vertexCount, z.indices.length, Zms.ATTRIBUTES);
            mesh.setVertices(z.toFloatArray());
            mesh.setIndices(z.indices);
            return mesh;
        });
    }

    public void setRenderOptions(RenderOptions renderOptions) {
        this.renderOptions = renderOptions != null ? renderOptions : new RenderOptions();
    }

    public boolean usesTransparency(MeshInstance instance) {
        if (instance == null) {
            return false;
        }
        // Only ZSC AlphaEnabled materials use the sorted transparent pass.
        // Alpha-test cutouts stay in the opaque pass with depth write.
        return instance.alphaBlend;
    }

    public float sortDepthSq(MeshInstance instance, Vector3 cameraGdx) {
        if (instance.animation != null && !instance.animation.morphVertices) {
            buildObjectMotionWorld(instance, renderWorld);
            renderWorld.getTranslation(sortPosition);
        } else {
            instance.world.getTranslation(sortPosition);
        }
        float dx = sortPosition.x - cameraGdx.x;
        float dy = sortPosition.y - cameraGdx.y;
        float dz = sortPosition.z - cameraGdx.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public void beginPass() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    }

    public void endPass() {
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDepthMask(true);
        GlState.resetForUi();
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    public void render(MeshInstance instance, Matrix4 projView) {
        if (instance.mesh == null) {
            return;
        }
        if (instance.animation != null && instance.animation.morphVertices) {
            AnimatedMeshFactory.finishGpuLoad(instance.animation, instance.mesh);
            if (instance.animation.gpuMesh == null) {
                return;
            }
        }
        if (!isGpuReady(instance)) {
            return;
        }

        Texture diffuse = textures.get(instance.diffusePath);
        boolean alphaTest = instance.alphaTest;

        RoseBlend.applyMeshBlend(instance);

        shader.bind();
        if (instance.animation != null && !instance.animation.morphVertices) {
            buildObjectMotionWorld(instance, renderWorld);
        } else {
            renderWorld.set(instance.world);
        }
        shader.setUniformMatrix("u_world", renderWorld);
        shader.setUniformMatrix("u_projView", projView);
        shader.setUniformi("u_masterLightmapEnabled", masterLightmapEnabled ? 1 : 0);
        shader.setUniformi("u_lightmapEnabled", instance.lightmapEnabled ? 1 : 0);
        shader.setUniformi("u_softDiffuseEnabled", renderOptions.isSoftDiffuse() ? 1 : 0);
        shader.setUniformi("u_texturesEnabled", renderOptions.isObjectTextures() ? 1 : 0);
        shader.setUniformi("u_alphaTestEnabled", alphaTest ? 1 : 0);
        shader.setUniformf("u_softDiffuseMin", RenderOptions.SOFT_DIFFUSE_MIN);
        shader.setUniformf("u_softDiffuseMax", RenderOptions.SOFT_DIFFUSE_MAX);
        shader.setUniformf("u_alpha", instance.alpha);
        shader.setUniformf("u_alphaReference", instance.alphaReference);
        shader.setUniformf("u_lightDir", RenderOptions.LIGHT_DIR_X, RenderOptions.LIGHT_DIR_Y, RenderOptions.LIGHT_DIR_Z);
        shader.setUniformf("u_textureAdd", instance.lightmapAdd.x, instance.lightmapAdd.y);
        shader.setUniformf("u_textureMultiply", instance.lightmapMul.x, instance.lightmapMul.y);
        shader.setUniformi("u_texture", 0);
        shader.setUniformi("u_lightmap", 1);

        diffuse.bind(0);
        if (instance.lightmapEnabled && instance.lightmapPath != null) {
            textures.get(instance.lightmapPath).bind(1);
        }

        meshFor(instance).render(shader, GL20.GL_TRIANGLES);
    }

    private void buildObjectMotionWorld(MeshInstance instance, Matrix4 out) {
        AnimatedMeshRuntime runtime = instance.animation;
        Vector3 position = runtime.hasMotionPosition ? runtime.motionPosition : runtime.basePosition;
        Quaternion rotation = runtime.hasMotionRotation ? runtime.motionRotation : runtime.baseRotation;
        if (runtime.hasMotionScale) {
            motionScaleScratch.set(runtime.motionScale, runtime.motionScale, runtime.motionScale);
        } else {
            motionScaleScratch.set(runtime.baseScale);
        }
        Matrix4 partWorld = RoseTransform.fromRose(position, rotation, motionScaleScratch);
        RoseTransform.combinePartObject(out, partWorld, instance.ifoWorld);
    }

    public void updateAnimations(Iterable<MeshInstance> meshes) {
        for (MeshInstance instance : meshes) {
            if (instance.animation == null) {
                continue;
            }
            AnimatedMeshFactory.update(instance);
        }
    }

    /** Queues unique meshes and textures for incremental GPU upload. */
    public void beginScenePreload(MapScene scene) {
        preloadMeshes.clear();
        preloadTextures.clear();
        preloadMeshIndex = 0;
        preloadTextureIndex = 0;
        if (scene == null) {
            return;
        }

        LinkedHashSet<Zms> meshSet = new LinkedHashSet<>();
        LinkedHashSet<String> textureSet = new LinkedHashSet<>();
        for (int i = 0; i < scene.meshes.size(); i++) {
            MeshInstance instance = scene.meshes.get(i);
            if (instance.mesh != null) {
                meshSet.add(instance.mesh);
            }
            addTexturePath(textureSet, instance.diffusePath);
            if (instance.lightmapEnabled) {
                addTexturePath(textureSet, instance.lightmapPath);
            }
        }
        preloadMeshes.addAll(meshSet);
        preloadTextures.addAll(textureSet);
    }

    /** Uploads a limited number of queued meshes/textures per call (render thread). */
    public int preloadSceneResources(int maxMeshes, int maxTextures) {
        int uploaded = 0;
        while (preloadMeshIndex < preloadMeshes.size() && uploaded < maxMeshes) {
            meshFor(preloadMeshes.get(preloadMeshIndex++));
            uploaded++;
        }
        int texturesUploaded = 0;
        while (preloadTextureIndex < preloadTextures.size() && texturesUploaded < maxTextures) {
            textures.get(preloadTextures.get(preloadTextureIndex++));
            texturesUploaded++;
        }
        return uploaded + texturesUploaded;
    }

    public boolean isScenePreloadComplete() {
        return preloadMeshIndex >= preloadMeshes.size() && preloadTextureIndex >= preloadTextures.size();
    }

    public int countScenePreloadRemaining() {
        return (preloadMeshes.size() - preloadMeshIndex) + (preloadTextures.size() - preloadTextureIndex);
    }

    public int countScenePreloadTotal() {
        return preloadMeshes.size() + preloadTextures.size();
    }

    private static void addTexturePath(LinkedHashSet<String> textureSet, String path) {
        if (path != null && !path.isBlank()) {
            textureSet.add(path);
        }
    }

    private boolean isGpuReady(MeshInstance instance) {
        if (instance.mesh != null && !meshCache.containsKey(instance.mesh)) {
            return false;
        }
        if (!textures.isCached(instance.diffusePath)) {
            return false;
        }
        if (instance.lightmapEnabled && instance.lightmapPath != null && !textures.isCached(instance.lightmapPath)) {
            return false;
        }
        return true;
    }

    /** Drops GPU mesh/texture caches when loading a different map. */
    public void clearMapResources() {
        preloadMeshes.clear();
        preloadTextures.clear();
        preloadMeshIndex = 0;
        preloadTextureIndex = 0;
        for (Mesh mesh : meshCache.values()) {
            mesh.dispose();
        }
        meshCache.clear();
        textures.clear();
    }

    public void disposeAnimated(MeshInstance instance) {
        if (instance != null && instance.animation != null) {
            AnimatedMeshFactory.dispose(instance.animation);
            instance.animation = null;
        }
    }

    @Override
    public void dispose() {
        shader.dispose();
        textures.dispose();
        for (Mesh mesh : meshCache.values()) {
            mesh.dispose();
        }
        meshCache.clear();
    }
}
