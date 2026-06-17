package com.rose.mapeditor.render;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.rose.mapeditor.scene.MapObjectKind;
import com.rose.mapeditor.scene.MapScene;
import com.rose.mapeditor.scene.MeshInstance;

/** Renders decorations, constructions, and other map mesh instances. */
public final class MapSceneRenderer implements Disposable {

    private final ObjectRenderer objectRenderer = new ObjectRenderer();
    private final WaterRenderer waterRenderer = new WaterRenderer();
    private final EffectRenderer effectRenderer = new EffectRenderer();
    private final MarkerRenderer markerRenderer = new MarkerRenderer();
    private final Array<MeshInstance> transparentMeshes = new Array<>();
    private RenderOptions renderOptions = new RenderOptions();

    public void setRenderOptions(RenderOptions renderOptions) {
        this.renderOptions = renderOptions != null ? renderOptions : new RenderOptions();
        objectRenderer.setRenderOptions(this.renderOptions);
        effectRenderer.setRenderOptions(this.renderOptions);
    }

    public void update(MapScene scene, float delta) {
        waterRenderer.update(delta);
        effectRenderer.update(scene, delta);
    }

    public void render(MapScene scene, Matrix4 projView, Vector3 cameraGdx, EditorCamera camera) {
        if (scene == null) {
            return;
        }

        objectRenderer.updateAnimations(scene.meshes);
        objectRenderer.beginPass();

        for (int i = 0; i < scene.meshes.size(); i++) {
            MeshInstance mesh = scene.meshes.get(i);
            if (!renderOptions.isEffectsEnabled() && mesh.kind == MapObjectKind.EFFECT) {
                continue;
            }
            if (!objectRenderer.usesTransparency(mesh)) {
                objectRenderer.render(mesh, projView);
            }
        }

        transparentMeshes.clear();
        for (int i = 0; i < scene.meshes.size(); i++) {
            MeshInstance mesh = scene.meshes.get(i);
            if (!renderOptions.isEffectsEnabled() && mesh.kind == MapObjectKind.EFFECT) {
                continue;
            }
            if (objectRenderer.usesTransparency(mesh)) {
                transparentMeshes.add(mesh);
            }
        }

        transparentMeshes.sort((a, b) -> Float.compare(
            objectRenderer.sortDepthSq(b, cameraGdx),
            objectRenderer.sortDepthSq(a, cameraGdx)
        ));

        for (int i = 0; i < transparentMeshes.size; i++) {
            objectRenderer.render(transparentMeshes.get(i), projView);
        }

        objectRenderer.endPass();

        WireframeGl.disable();
        effectRenderer.render(scene, projView, camera);
        waterRenderer.render(scene, projView);

        if (scene.waterSurfaces.isEmpty()) {
            markerRenderer.renderWater(scene.waterVolumes, projView);
        }
        markerRenderer.render(scene.markers, projView, renderOptions.isNpcMarkers(),
            renderOptions.isMonsterMarkers(), camera);
    }

    @Override
    public void dispose() {
        objectRenderer.dispose();
        waterRenderer.dispose();
        effectRenderer.dispose();
        markerRenderer.dispose();
    }

    public void clearMapResources() {
        objectRenderer.clearMapResources();
        waterRenderer.clearMapResources();
        effectRenderer.clearMapResources();
    }

    public void beginScenePreload(MapScene scene) {
        objectRenderer.beginScenePreload(scene);
    }

    public int preloadSceneResources(int maxMeshes, int maxTextures) {
        return objectRenderer.preloadSceneResources(maxMeshes, maxTextures);
    }

    public boolean isScenePreloadComplete() {
        return objectRenderer.isScenePreloadComplete();
    }

    public int countScenePreloadRemaining() {
        return objectRenderer.countScenePreloadRemaining();
    }

    public int countScenePreloadTotal() {
        return objectRenderer.countScenePreloadTotal();
    }

    public void disposeScene(MapScene scene) {
        if (scene == null) {
            return;
        }
        waterRenderer.disposeScene(scene);
        for (int i = 0; i < scene.meshes.size(); i++) {
            objectRenderer.disposeAnimated(scene.meshes.get(i));
        }
    }
}
