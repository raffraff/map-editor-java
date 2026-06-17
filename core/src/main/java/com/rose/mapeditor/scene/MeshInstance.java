package com.rose.mapeditor.scene;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.model.Zms;

/** One ZMS mesh instance placed in the map. */
public final class MeshInstance {

    public MapObjectKind kind;
    public Zms mesh;
    public String diffusePath;
    public final Matrix4 world = new Matrix4();
    public boolean lightmapEnabled;
    public String lightmapPath;
    public final Vector3 lightmapAdd = new Vector3();
    public final Vector3 lightmapMul = new Vector3(1, 1, 1);

    /** From ZSC texture block - enables alpha blending when true. */
    public boolean alphaBlend;
    /** Rose/Znzin blend factors from EFT mesh entries (0 = use default alpha blend). */
    public int blendSrc;
    public int blendDst;
    public int blendOp;
    /** From EFT mesh entries; defaults to true for regular ZSC objects. */
    public boolean depthWriteEnabled = true;
    /** From ZSC - render both faces (foliage, fences, morph objects). */
    public boolean twoSided;
    /** From ZSC - discard fragments below {@link #alphaReference}. */
    public boolean alphaTest;
    public int alphaReference;
    public float alpha = 1f;

    /** IFO object transform - used with object-level ZMO part motion. */
    public final Matrix4 ifoWorld = new Matrix4();

    /** Present for ZMO-driven animation (morph or object motion). */
    public AnimatedMeshRuntime animation;

    /** Source IFO entry for click-to-inspect. */
    public SceneObjectRef pickRef;
}
