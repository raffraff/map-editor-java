package com.rose.mapeditor.tool;

/** Configurable height brush parameters. */
public final class HeightBrushSettings {

    public HeightBrushMode mode = HeightBrushMode.RAISE;
    public HeightBrushShape shape = HeightBrushShape.SQUARE;
    public int innerRadius = 3;
    public int outerRadius = 7;
    /** UI power 0–100; effective delta uses {@code power / 100f} per frame */
    public int power = 5;

    public float deltaPerFrame() {
        return power / 100f;
    }
}
