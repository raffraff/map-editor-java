package com.rose.mapeditor.render;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/**
 * Rose object transforms - row-vector order {@code v * R * S * T}.
 * libGDX column matrices must be built as {@code T * S * R} so rotation is applied first.
 */
public final class RoseTransform {

    private RoseTransform() {
    }

    public static Matrix4 fromRose(Vector3 position, Quaternion rotation, Vector3 scale) {
        Matrix4 m = new Matrix4();
        m.idt();
        m.translate(position);
        m.scale(scale.x, scale.y, scale.z);
        m.rotate(rotation);
        return m;
    }

    public static Matrix4 fromRose(Vector3 position, Quaternion rotation) {
        return fromRose(position, rotation, new Vector3(1, 1, 1));
    }

    /** D3DXQuaternionRotationYawPitchRoll(yaw, pitch, roll) in radians. */
    public static Quaternion fromYawPitchRoll(float yaw, float pitch, float roll) {
        float cy = (float) Math.cos(yaw * 0.5f);
        float sy = (float) Math.sin(yaw * 0.5f);
        float cp = (float) Math.cos(pitch * 0.5f);
        float sp = (float) Math.sin(pitch * 0.5f);
        float cr = (float) Math.cos(roll * 0.5f);
        float sr = (float) Math.sin(roll * 0.5f);

        float x = sy * cp * sr + cy * sp * cr;
        float y = sy * cp * cr - cy * sp * sr;
        float z = cy * cp * sr - sy * sp * cr;
        float w = cy * cp * cr + sy * sp * sr;
        return new Quaternion(x, y, z, w);
    }

    /** ZSC effect-slot quaternion remap for editor axis swap (x,y,z,w) -> (x,z,y,w). */
    public static Quaternion remapQuaternionForEditorAxisSwap(Quaternion rotation) {
        if (rotation == null) {
            return new Quaternion();
        }
        return new Quaternion(rotation.x, rotation.z, rotation.y, rotation.w);
    }

    /** ZSC effect-slot scale remap for editor axis swap (x,y,z) -> (x,z,y). */
    public static Vector3 remapScaleForEditorAxisSwap(Vector3 scale) {
        if (scale == null) {
            return new Vector3(1f, 1f, 1f);
        }
        return new Vector3(scale.x, scale.z, scale.y);
    }

    /**
     * ZSC effect-slot rotation for the editor's Rose Z-up + shader Y/Z swap.
     * A file quaternion of (0,0,0,0) is 180° in XYZW form; after the axis swap
     * that reads as an upside-down flip, so map it to 180° instead.
     */
    public static Quaternion zscEffectRotationForEditor(Quaternion rotation) {
        if (rotation == null || is180DegreeAboutRoseX(rotation)) {
            return new Quaternion();
        }
        return remapQuaternionForEditorAxisSwap(rotation);
    }

    /**
     * EFT mesh yaw/pitch/roll in degrees.
     * {@code Ry(yaw) * Rx(pitch) * Rz(roll)} axis rotations.
     */
    public static Quaternion fromEftMeshRotationDegrees(float yawDegrees, float pitchDegrees, float rollDegrees) {
        float yaw = yawDegrees * MathUtils.degreesToRadians;
        float pitch = pitchDegrees * MathUtils.degreesToRadians;
        float roll = rollDegrees * MathUtils.degreesToRadians;

        Quaternion result = new Quaternion();
        result.idt();
        Quaternion axis = new Quaternion();
        axis.set(Vector3.Y, yaw);
        result.mul(axis);
        axis.set(Vector3.X, pitch);
        result.mul(axis);
        axis.set(Vector3.Z, roll);
        result.mul(axis);
        return result;
    }

    private static boolean is180DegreeAboutRoseX(Quaternion rotation) {
        return Math.abs(Math.abs(rotation.x) - 1f) < 0.001f
            && Math.abs(rotation.y) < 0.001f
            && Math.abs(rotation.z) < 0.001f
            && Math.abs(rotation.w) < 0.001f;
    }

    /**
     * Combines part + object for column-vector shaders with a Rose→GDX axis swap in the vertex shader.
     * Row order {@code v * part * object}: part is applied to the mesh first.
     */
    public static void combinePartObject(Matrix4 out, Matrix4 partLocal, Matrix4 objectWorld) {
        out.set(objectWorld).mul(partLocal);
    }
}
