package com.rose.mapeditor.tool;

import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.RoseCoords;

import java.util.HashSet;
import java.util.Set;

/** Applies height brush strokes to the terrain grid. */
public final class HeightBrushPainter {

    private final HeightBrushSettings settings = new HeightBrushSettings();
    private final TerrainHeightGrid grid;
    private final Vector3 pickRose = new Vector3();
    private final Vector3 pickGdx = new Vector3();

    private boolean strokeActive;
    private float strokeMiddleHeight;
    private boolean hasPick;

    public HeightBrushPainter(TerrainHeightGrid grid) {
        this.grid = grid;
    }

    public HeightBrushSettings settings() {
        return settings;
    }

    public void resetPick() {
        hasPick = false;
        pickRose.setZero();
    }

    public void setPickRose(Vector3 rosePosition) {
        if (rosePosition == null) {
            resetPick();
            return;
        }
        hasPick = true;
        pickRose.set(rosePosition);
        pickGdx.set(RoseCoords.toGdx(pickRose));
    }

    public boolean hasPick() {
        return hasPick;
    }

    public Vector3 pickGdx() {
        return pickGdx;
    }

    public void endStroke() {
        strokeActive = false;
    }

    public void applyStroke() {
        if (!hasPick || grid == null) {
            return;
        }

        int cellX = Math.round(pickRose.x / 2.5f);
        int cellY = Math.round(pickRose.y / 2.5f);
        Set<com.rose.mapeditor.render.HeightmapBlock> touched = new HashSet<>();

        if (!strokeActive) {
            strokeMiddleHeight = grid.getHeight(cellX, cellY);
        }

        if (settings.shape == HeightBrushShape.SQUARE) {
            applySquare(cellX, cellY, touched);
        } else {
            applyCircle(cellX, cellY, touched);
        }

        grid.rebuild(touched);
        strokeActive = true;
    }

    private void applySquare(int cellX, int cellY, Set<com.rose.mapeditor.render.HeightmapBlock> touched) {
        if (settings.mode == HeightBrushMode.SMOOTH) {
            paintSquareSmooth(cellX, cellY, touched);
        } else {
            paintSquareRaiseLowerFlatten(cellX, cellY, touched);
        }
    }

    private void applyCircle(int cellX, int cellY, Set<com.rose.mapeditor.render.HeightmapBlock> touched) {
        if (settings.mode == HeightBrushMode.SMOOTH) {
            paintCircleSmooth(cellX, cellY, touched);
        } else {
            paintCircleRaiseLowerFlatten(cellX, cellY, touched);
        }
    }

    private void paintSquareRaiseLowerFlatten(int cellX, int cellY,
                                              Set<com.rose.mapeditor.render.HeightmapBlock> touched) {
        int originalY = cellY;
        int maximumX = cellX + settings.outerRadius;
        int maximumY = cellY + settings.outerRadius;
        int innerMinX = cellX - settings.innerRadius;
        int innerMinY = cellY - settings.innerRadius;
        int innerMaxX = cellX + settings.innerRadius;
        int innerMaxY = cellY + settings.innerRadius;

        for (int cx = cellX - settings.outerRadius; cx <= maximumX; cx++) {
            for (int cy = originalY - settings.outerRadius; cy <= maximumY; cy++) {
                float currentHeight = grid.getHeight(cx, cy);
                float raiseHeight = settings.deltaPerFrame();

                if (cx < innerMinX || cx > innerMaxX || cy < innerMinY || cy > innerMaxY) {
                    int distance;
                    int distance2;
                    if (cx <= innerMinX) {
                        distance = innerMinX - cx;
                    } else {
                        distance = cx - innerMaxX;
                    }
                    if (cy <= innerMinY) {
                        distance2 = innerMinY - cy;
                    } else {
                        distance2 = cy - innerMaxY;
                    }
                    distance = Math.max(distance2, distance);
                    if (distance > 0) {
                        raiseHeight = raiseHeight / distance;
                    }
                }

                currentHeight = applyMode(currentHeight, raiseHeight);
                grid.setHeight(cx, cy, currentHeight, touched);
            }
        }
    }

    private void paintSquareSmooth(int cellX, int cellY, Set<com.rose.mapeditor.render.HeightmapBlock> touched) {
        int xStart = cellX;
        int yStart = cellY;
        int xEnd = cellX + settings.outerRadius;
        int yEnd = cellY + settings.outerRadius;

        for (int cx = xStart; cx <= xEnd; cx++) {
            for (int cy = yStart; cy <= yEnd; cy++) {
                float value = smoothAverage(cx, cy);
                grid.setHeight(cx, cy, value, touched);
            }
        }
    }

    private void paintCircleRaiseLowerFlatten(int cellX, int cellY,
                                              Set<com.rose.mapeditor.render.HeightmapBlock> touched) {
        int centerX = cellX;
        int centerY = cellY;
        int endX = cellX + settings.outerRadius;
        int endY = cellY + settings.outerRadius;
        int startX = cellX - settings.outerRadius;
        int startY = cellY - settings.outerRadius;

        float radiusDifference = settings.outerRadius - settings.innerRadius;
        float rSquared = radiusDifference != 0 ? radiusDifference * radiusDifference : 0f;

        for (int cx = startX; cx <= endX; cx++) {
            for (int cy = startY; cy <= endY; cy++) {
                float dX = cx - centerX;
                float dY = cy - centerY;
                float distance = (float) Math.sqrt(dY * dY + dX * dX);
                if (distance > settings.outerRadius) {
                    continue;
                }

                float currentHeight = grid.getHeight(cx, cy);
                float raiseHeight = settings.deltaPerFrame();

                if (radiusDifference != 0 && distance > settings.innerRadius) {
                    float falloff = distance - settings.innerRadius;
                    falloff *= falloff;
                    falloff = (float) Math.sqrt(rSquared - falloff);
                    raiseHeight *= falloff / radiusDifference;
                }

                currentHeight = applyMode(currentHeight, raiseHeight);
                grid.setHeight(cx, cy, currentHeight, touched);
            }
        }
    }

    private void paintCircleSmooth(int cellX, int cellY, Set<com.rose.mapeditor.render.HeightmapBlock> touched) {
        int xStart = cellX - settings.outerRadius;
        int yStart = cellY - settings.outerRadius;
        int xEnd = xStart + settings.outerRadius * 2;
        int yEnd = yStart + settings.outerRadius * 2;
        int xCenter = xStart + settings.outerRadius;
        int yCenter = yStart + settings.outerRadius;

        for (int cx = xStart; cx <= xEnd; cx++) {
            for (int cy = yStart; cy <= yEnd; cy++) {
                float dX = cx - xCenter;
                float dY = cy - yCenter;
                float distance = (float) Math.sqrt(dY * dY + dX * dX);
                if (distance > settings.outerRadius) {
                    continue;
                }
                float value = smoothAverage(cx, cy);
                grid.setHeight(cx, cy, value, touched);
            }
        }
    }

    private float applyMode(float currentHeight, float raiseHeight) {
        switch (settings.mode) {
            case RAISE:
                return currentHeight + raiseHeight;
            case LOWER:
                return currentHeight - raiseHeight;
            case FLATTEN:
                if (currentHeight > strokeMiddleHeight) {
                    currentHeight -= raiseHeight;
                    if (currentHeight < strokeMiddleHeight) {
                        currentHeight = strokeMiddleHeight;
                    }
                } else {
                    currentHeight += raiseHeight;
                    if (currentHeight > strokeMiddleHeight) {
                        currentHeight = strokeMiddleHeight;
                    }
                }
                return currentHeight;
            case SMOOTH:
            default:
                return currentHeight;
        }
    }

    private float smoothAverage(int cellX, int cellY) {
        float valueCount = 0f;
        float value = 0f;
        for (int myX = cellX - 1; myX <= cellX + 1; myX++) {
            for (int myY = cellY - 1; myY <= cellY + 1; myY++) {
                boolean[] found = new boolean[1];
                value += grid.getHeight(myX, myY, found);
                if (found[0]) {
                    valueCount += 1f;
                }
            }
        }
        if (valueCount < 1f) {
            valueCount = 1f;
        }
        if (value != 0f) {
            value /= valueCount;
        }
        return value;
    }
}
