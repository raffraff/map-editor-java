package com.rose.mapeditor.render;

/** Shared viewport rendering toggles. */
public final class RenderOptions {

    /** World-space directional light (libGDX Y-up). */
    public static final float LIGHT_DIR_X = 0.35f;
    public static final float LIGHT_DIR_Y = 0.85f;
    public static final float LIGHT_DIR_Z = 0.4f;

    /** Soft diffuse shading range for objects without baked lightmaps. */
    public static final float SOFT_DIFFUSE_MIN = 0.55f;
    public static final float SOFT_DIFFUSE_MAX = 1.0f;

    private boolean softDiffuse;
    private boolean npcMarkers;
    private boolean monsterMarkers;
    private boolean terrainLightmap = true;
    private boolean wireframe;
    private boolean terrainTextures = true;
    private boolean objectTextures = true;

    public boolean isSoftDiffuse() {
        return softDiffuse;
    }

    public void toggleSoftDiffuse() {
        softDiffuse = !softDiffuse;
    }

    public boolean isNpcMarkers() {
        return npcMarkers;
    }

    public void toggleNpcMarkers() {
        npcMarkers = !npcMarkers;
    }

    public boolean isMonsterMarkers() {
        return monsterMarkers;
    }

    public void toggleMonsterMarkers() {
        monsterMarkers = !monsterMarkers;
    }

    public boolean isTerrainLightmap() {
        return terrainLightmap;
    }

    public void toggleTerrainLightmap() {
        terrainLightmap = !terrainLightmap;
    }

    public void setTerrainLightmap(boolean enabled) {
        terrainLightmap = enabled;
    }

    public boolean isWireframe() {
        return wireframe;
    }

    public void toggleWireframe() {
        wireframe = !wireframe;
    }

    public boolean isTerrainTextures() {
        return terrainTextures;
    }

    public void toggleTerrainTextures() {
        terrainTextures = !terrainTextures;
    }

    public boolean isObjectTextures() {
        return objectTextures;
    }

    public void toggleObjectTextures() {
        objectTextures = !objectTextures;
    }
}
