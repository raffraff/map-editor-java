package com.rose.mapeditor.scene;

import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.map.Ifo;

import java.util.ArrayList;
import java.util.List;

/** All renderable map objects built from IFO data. */
public final class MapScene {

    public final List<MeshInstance> meshes = new ArrayList<>();
    public final List<MapMarker> markers = new ArrayList<>();
    public final List<Ifo.WaterVolume> waterVolumes = new ArrayList<>();
    public final List<WaterSurface> waterSurfaces = new ArrayList<>();

    /** Sky dome from LIST_SKY (null when unavailable). */
    public com.rose.mapeditor.model.Zms skyMesh;
    public String skyModelPath;
    public String skyTexturePath;

    public int meshCount() {
        return meshes.size();
    }

    public int markerCount() {
        return markers.size();
    }

    public String summary() {
        int npcMeshes = 0;
        for (int i = 0; i < meshes.size(); i++) {
            if (meshes.get(i).kind == MapObjectKind.NPC) {
                npcMeshes++;
            }
        }
        return String.format(
            "meshes=%d (npc=%d) markers=%d water=%d sky=%s",
            meshes.size(), npcMeshes, markers.size(), waterSurfaces.size(),
            skyMesh != null ? "yes" : "no"
        );
    }
}
