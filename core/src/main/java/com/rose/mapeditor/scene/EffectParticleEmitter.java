package com.rose.mapeditor.scene;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.render.PtlParticleSimulator;

/** A simulated PTL particle emitter placed in the map scene. */
public final class EffectParticleEmitter {

    /** Rose-space transform from effect root to world (includes parent part + ZSC/IFO placement). */
    public final Matrix4 rootWorld = new Matrix4();
    public final PtlParticleSimulator simulator;
    public String texturePath = "";
    public SceneObjectRef pickRef;

    private final Vector3 origin = new Vector3();
    private final Vector3 scaledLocal = new Vector3();

    public EffectParticleEmitter(PtlParticleSimulator simulator, String texturePath) {
        this.simulator = simulator;
        this.texturePath = texturePath != null ? texturePath : "";
        rootWorld.idt();
        simulator.setRootWorld(rootWorld);
    }

    public Vector3 origin(Vector3 out) {
        rootWorld.getTranslation(out);
        return out;
    }

    /** Maps simulated particle coordinates to Rose world space */
    public Vector3 worldPosition(Vector3 localRose, Vector3 out) {
        switch (simulator.sequence().coordType) {
            case WORLD:
                return out.set(localRose);
            case LOCAL_POSITION:
                rootWorld.getTranslation(origin);
                scaledLocal.set(localRose);
                scaledLocal.scl(rootWorld.getScaleX(), rootWorld.getScaleY(), rootWorld.getScaleZ());
                return out.set(origin).add(scaledLocal);
            case LOCAL:
            default:
                return out.set(localRose).mul(rootWorld);
        }
    }
}
