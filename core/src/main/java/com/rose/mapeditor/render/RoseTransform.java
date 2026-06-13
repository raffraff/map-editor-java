package com.rose.mapeditor.render;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/**
 * Rose object transforms - matches XNA row-vector order {@code v * R * S * T}.
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

    /**
     * Combines part + object for column-vector shaders with a Rose→GDX axis swap in the vertex shader.
     * Matches XNA row order {@code v * part * object}: part is applied to the mesh first.
     */
    public static void combinePartObject(Matrix4 out, Matrix4 partLocal, Matrix4 objectWorld) {
        out.set(objectWorld).mul(partLocal);
    }
}
