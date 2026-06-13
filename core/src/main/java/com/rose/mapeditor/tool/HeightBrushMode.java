package com.rose.mapeditor.tool;

/** Height brush operation */
public enum HeightBrushMode {
    RAISE,
    LOWER,
    FLATTEN,
    SMOOTH;

    @Override
    public String toString() {
        switch (this) {
            case RAISE: return "Raise";
            case LOWER: return "Lower";
            case FLATTEN: return "Flatten";
            case SMOOTH:
            default: return "Smooth";
        }
    }
}
