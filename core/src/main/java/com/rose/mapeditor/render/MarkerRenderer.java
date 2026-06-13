package com.rose.mapeditor.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

import com.rose.mapeditor.RoseCoords;
import com.rose.mapeditor.map.Ifo;
import com.rose.mapeditor.scene.MapMarker;
import com.rose.mapeditor.scene.MapObjectKind;

/** Draws gizmo markers (Rose → GDX for libGDX ShapeRenderer). */
public final class MarkerRenderer implements Disposable {

    private static final float LABEL_PAD_PX = 6f;
    private static final float LABEL_FONT_SCALE = 0.8f;

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch spriteBatch = new SpriteBatch();
    private final BitmapFont font = createLabelFont();
    private final GlyphLayout glyphLayout = new GlyphLayout();
    private final Vector3 gdx = new Vector3();
    private final Vector3 labelAnchor = new Vector3();
    private final Vector3 screenPos = new Vector3();

    public MarkerRenderer() {
    }

    private static BitmapFont createLabelFont() {
        BitmapFont labelFont = new BitmapFont();
        labelFont.getData().setScale(LABEL_FONT_SCALE);
        labelFont.setUseIntegerPositions(false);
        labelFont.getRegion().getTexture().setFilter(
            Texture.TextureFilter.Linear,
            Texture.TextureFilter.Linear
        );
        return labelFont;
    }

    public void render(Iterable<MapMarker> markers, Matrix4 projView, boolean showNpcMarkers,
                       boolean showMonsterMarkers, EditorCamera camera) {
        shapes.setProjectionMatrix(projView);
        shapes.begin(ShapeRenderer.ShapeType.Line);

        for (MapMarker marker : markers) {
            if (marker.kind == MapObjectKind.NPC && !showNpcMarkers) {
                continue;
            }
            if (marker.kind == MapObjectKind.MONSTER && !showMonsterMarkers) {
                continue;
            }

            gdx.set(RoseCoords.toGdx(marker.position));

            Color c = marker.color;
            shapes.setColor(c.r, c.g, c.b, 1f);

            if (marker.useWorldBounds) {
                Vector3 minGdx = RoseCoords.toGdx(marker.boundsMin);
                Vector3 maxGdx = RoseCoords.toGdx(marker.boundsMax);
                float x0 = Math.min(minGdx.x, maxGdx.x);
                float y0 = Math.min(minGdx.y, maxGdx.y);
                float z0 = Math.min(minGdx.z, maxGdx.z);
                shapes.box(x0, y0, z0,
                    Math.abs(maxGdx.x - minGdx.x),
                    Math.abs(maxGdx.y - minGdx.y),
                    Math.abs(maxGdx.z - minGdx.z));
            } else {
                shapes.box(gdx.x - marker.size * 0.5f, gdx.y, gdx.z - marker.size * 0.5f,
                    marker.size, marker.size, marker.size);
            }

            if (marker.range > 0f) {
                shapes.setColor(c.r, c.g, c.b, 0.35f);
                float d = marker.range * 2f;
                shapes.box(gdx.x - marker.range, gdx.y, gdx.z - marker.range, d, 0.5f, d);
            }
        }

        shapes.end();

        if (camera == null || (!showNpcMarkers && !showMonsterMarkers)) {
            return;
        }

        GlState.resetForUi();
        spriteBatch.begin();

        for (MapMarker marker : markers) {
            if (marker.label == null || marker.label.isBlank()) {
                continue;
            }
            if (marker.kind == MapObjectKind.NPC && !showNpcMarkers) {
                continue;
            }
            if (marker.kind == MapObjectKind.MONSTER && !showMonsterMarkers) {
                continue;
            }
            if (marker.kind != MapObjectKind.NPC && marker.kind != MapObjectKind.MONSTER) {
                continue;
            }

            labelAnchor.set(topCenterGdx(marker));
            camera.projectGdx(labelAnchor, screenPos);
            if (screenPos.z < 0f || screenPos.z > 1f) {
                continue;
            }

            glyphLayout.setText(font, marker.label);
            float x = screenPos.x - glyphLayout.width * 0.5f;
            float y = screenPos.y + LABEL_PAD_PX;
            if (marker.kind == MapObjectKind.MONSTER) {
                drawOutlinedLabel(spriteBatch, marker.label, x, y, 0.75f, 0.85f, 1f);
            } else {
                drawOutlinedLabel(spriteBatch, marker.label, x, y, 0.85f, 1f, 0.85f);
            }
        }

        spriteBatch.end();
    }

    private Vector3 topCenterGdx(MapMarker marker) {
        if (marker.useWorldBounds) {
            Vector3 minGdx = RoseCoords.toGdx(marker.boundsMin);
            Vector3 maxGdx = RoseCoords.toGdx(marker.boundsMax);
            labelAnchor.set(
                (minGdx.x + maxGdx.x) * 0.5f,
                Math.max(minGdx.y, maxGdx.y),
                (minGdx.z + maxGdx.z) * 0.5f
            );
        } else {
            gdx.set(RoseCoords.toGdx(marker.position));
            labelAnchor.set(gdx.x, gdx.y + marker.size, gdx.z);
        }
        return labelAnchor;
    }

    private void drawOutlinedLabel(SpriteBatch batch, String text, float x, float y,
                                   float r, float g, float b) {
        font.setColor(0f, 0f, 0f, 0.9f);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                font.draw(batch, text, x + dx, y + dy);
            }
        }
        font.setColor(r, g, b, 1f);
        font.draw(batch, text, x, y);
    }

    public void renderWater(Iterable<Ifo.WaterVolume> volumes, Matrix4 projView) {
        shapes.setProjectionMatrix(projView);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.2f, 0.5f, 0.9f, 0.8f);

        for (Ifo.WaterVolume vol : volumes) {
            Vector3 gdxMin = RoseCoords.toGdx(vol.minimum);
            Vector3 gdxMax = RoseCoords.toGdx(vol.maximum);
            float x0 = Math.min(gdxMin.x, gdxMax.x);
            float y0 = Math.min(gdxMin.y, gdxMax.y);
            float z0 = Math.min(gdxMin.z, gdxMax.z);
            shapes.box(x0, y0, z0,
                Math.abs(gdxMax.x - gdxMin.x),
                Math.abs(gdxMax.y - gdxMin.y),
                Math.abs(gdxMax.z - gdxMin.z));
        }

        shapes.end();
    }

    @Override
    public void dispose() {
        shapes.dispose();
        spriteBatch.dispose();
        font.dispose();
    }
}
