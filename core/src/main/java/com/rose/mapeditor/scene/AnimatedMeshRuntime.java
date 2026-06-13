package com.rose.mapeditor.scene;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.model.Zmo;
import com.rose.mapeditor.model.Zms;

/** GPU + playback state for ZMO-driven animation. */
public final class AnimatedMeshRuntime {

    /** When true, ZMO deforms mesh vertices (IFO morph / LIST_MORPH_OBJECT). */
    public boolean morphVertices = true;

    public Zmo motion;
    public Zms.Vertex[] baseVertices;
    public Zms.Vertex[] animatedVertices;
    public float[] vertexData;
    public Mesh gpuMesh;
    public int frame;
    public long lastUpdateMs;

    /** Object-level motion (ZSC part {@code Motion} path on decorations/constructions). */
    public final Vector3 basePosition = new Vector3();
    public final Vector3 baseScale = new Vector3(1, 1, 1);
    public final Quaternion baseRotation = new Quaternion();
    public final Vector3 motionPosition = new Vector3();
    public final Quaternion motionRotation = new Quaternion();
    public float motionScale = 1f;
    public boolean hasMotionPosition;
    public boolean hasMotionRotation;
    public boolean hasMotionScale;
}
