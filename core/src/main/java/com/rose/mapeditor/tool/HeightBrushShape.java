package com.rose.mapeditor.tool;

/** Height brush footprint */
public enum HeightBrushShape {
    SQUARE,
    CIRCLE;

    @Override
    public String toString() {
        return this == SQUARE ? "Square" : "Circle";
    }
}
