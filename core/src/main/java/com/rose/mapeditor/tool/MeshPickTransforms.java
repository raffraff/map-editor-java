package com.rose.mapeditor.tool;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.RoseCoords;
import com.rose.mapeditor.model.Zms;
import com.rose.mapeditor.render.RoseTransform;
import com.rose.mapeditor.scene.AnimatedMeshRuntime;
import com.rose.mapeditor.scene.MeshInstance;

/** World transforms for mesh picking - matches {@code object.vert} Rose→GDX swap. */
final class MeshPickTransforms {

    private static final Vector3 motionScaleScratch = new Vector3();
    private static final Vector3 roseScratch = new Vector3();

    private MeshPickTransforms() {
    }

    static void worldMatrix(MeshInstance instance, Matrix4 out) {
        AnimatedMeshRuntime runtime = instance.animation;
        if (runtime != null && !runtime.morphVertices) {
            Vector3 position = runtime.hasMotionPosition ? runtime.motionPosition : runtime.basePosition;
            Quaternion rotation = runtime.hasMotionRotation ? runtime.motionRotation : runtime.baseRotation;
            if (runtime.hasMotionScale) {
                motionScaleScratch.set(runtime.motionScale, runtime.motionScale, runtime.motionScale);
            } else {
                motionScaleScratch.set(runtime.baseScale);
            }
            Matrix4 partWorld = RoseTransform.fromRose(position, rotation, motionScaleScratch);
            RoseTransform.combinePartObject(out, partWorld, instance.ifoWorld);
            return;
        }
        out.set(instance.world);
    }

    static void vertexInGdx(Zms zms, short index, Matrix4 world, Vector3 out) {
        Zms.Vertex vertex = zms.vertices[index & 0xFFFF];
        roseScratch.set(vertex.position).mul(world);
        out.set(RoseCoords.toGdx(roseScratch));
    }
}
