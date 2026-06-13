package com.rose.mapeditor.scene;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;

/** Simple gizmo for non-mesh or placeholder map objects. */
public final class MapMarker {

    public MapObjectKind kind;
    public final Vector3 position = new Vector3();
    public float size = 2f;
    public float range;
    public final Color color = new Color(Color.WHITE);
    public String label = "";

    /** When set, the marker draws the merged mesh bounds instead of a cube at {@link #position}. */
    public boolean useWorldBounds;
    public final Vector3 boundsMin = new Vector3();
    public final Vector3 boundsMax = new Vector3();

    /** Source IFO entry for click-to-inspect. */
    public SceneObjectRef pickRef;
}
