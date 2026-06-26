package com.rose.mapeditor;

import com.badlogic.gdx.math.Vector3;

/**
 * Rose Online uses X/Y as the ground plane and Z as elevation.
 * libGDX / OpenGL uses X/Z as the ground plane and Y as elevation.
 */
public final class RoseCoords {

    private RoseCoords() {
    }

    /**
     * Rose (x, y, z) → libGDX (x, z, -y).
     * Negating Y avoids the reflection from a plain Y/Z swap (det must stay +1).
     */
    public static Vector3 toGdx(float roseX, float roseY, float roseZ) {
        return new Vector3(roseX, roseZ, -roseY);
    }

    public static Vector3 toGdx(Vector3 rose) {
        return new Vector3(rose.x, rose.z, -rose.y);
    }

    /** libGDX (x, y, z) → Rose (x, y, z). */
    public static Vector3 toRose(float gdxX, float gdxY, float gdxZ) {
        return new Vector3(gdxX, -gdxZ, gdxY);
    }

    public static Vector3 toRose(Vector3 gdx) {
        return new Vector3(gdx.x, -gdx.z, gdx.y);
    }
}
