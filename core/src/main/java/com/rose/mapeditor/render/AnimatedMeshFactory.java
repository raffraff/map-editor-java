package com.rose.mapeditor.render;

import com.badlogic.gdx.graphics.Mesh;
import com.rose.mapeditor.model.Zmo;
import com.rose.mapeditor.model.ZmoPlayer;
import com.rose.mapeditor.model.Zms;
import com.rose.mapeditor.model.Zsc;
import com.rose.mapeditor.scene.AnimatedMeshRuntime;
import com.rose.mapeditor.scene.MeshInstance;

/** Creates and updates ZMO animation GPU / transform state. */
public final class AnimatedMeshFactory {

    private AnimatedMeshFactory() {
    }

    /** CPU-only vertex morph setup; safe on background loader threads. */
    public static AnimatedMeshRuntime prepareMorphCpu(Zms zms, Zmo zmo) {
        AnimatedMeshRuntime runtime = new AnimatedMeshRuntime();
        runtime.morphVertices = true;
        runtime.motion = zmo;
        runtime.baseVertices = zms.copyVertices();
        runtime.animatedVertices = zms.copyVertices();
        runtime.frame = 0;
        runtime.lastUpdateMs = System.currentTimeMillis();
        ZmoPlayer.applyFrame(runtime.baseVertices, runtime.animatedVertices, zmo, 0);
        runtime.vertexData = zms.toFloatArray(runtime.animatedVertices);
        return runtime;
    }

    /** CPU-only object motion setup (ZSC part Motion); safe on background threads. */
    public static AnimatedMeshRuntime prepareObjectMotionCpu(Zmo zmo, Zsc.PartModel part) {
        AnimatedMeshRuntime runtime = new AnimatedMeshRuntime();
        runtime.morphVertices = false;
        runtime.motion = zmo;
        runtime.frame = 0;
        runtime.lastUpdateMs = System.currentTimeMillis();
        runtime.basePosition.set(part.position);
        runtime.baseScale.set(part.scale);
        runtime.baseRotation.set(part.rotation);
        ZmoPlayer.applyObjectMotionFrame(zmo, 0, runtime);
        return runtime;
    }

    /** Uploads OpenGL resources for morph animations; render thread only. */
    public static void finishGpuLoad(AnimatedMeshRuntime runtime, Zms zms) {
        if (runtime == null || !runtime.morphVertices || runtime.gpuMesh != null || zms == null) {
            return;
        }
        runtime.gpuMesh = new Mesh(true, zms.vertexCount, zms.indices.length, Zms.ATTRIBUTES);
        runtime.gpuMesh.setVertices(runtime.vertexData);
        runtime.gpuMesh.setIndices(zms.indices);
    }

    public static void update(MeshInstance instance) {
        AnimatedMeshRuntime runtime = instance.animation;
        if (runtime == null || runtime.motion == null || instance.mesh == null) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        int nextFrame = ZmoPlayer.advanceFrame(runtime.motion, runtime.frame, runtime.lastUpdateMs, nowMs);
        if (nextFrame == runtime.frame) {
            return;
        }

        runtime.frame = nextFrame;
        runtime.lastUpdateMs = nowMs;

        if (runtime.morphVertices) {
            finishGpuLoad(runtime, instance.mesh);
            if (runtime.gpuMesh == null) {
                return;
            }
            ZmoPlayer.applyFrame(runtime.baseVertices, runtime.animatedVertices, runtime.motion, runtime.frame);
            Zms.writeVertexData(instance.mesh, runtime.animatedVertices, runtime.vertexData);
            runtime.gpuMesh.setVertices(runtime.vertexData);
        } else {
            ZmoPlayer.applyObjectMotionFrame(runtime.motion, runtime.frame, runtime);
        }
    }

    public static void dispose(AnimatedMeshRuntime runtime) {
        if (runtime != null && runtime.gpuMesh != null) {
            runtime.gpuMesh.dispose();
            runtime.gpuMesh = null;
        }
    }
}
