package com.rose.mapeditor.tool;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Disposable;
import com.rose.mapeditor.render.HeightmapBlock;

/**
 * libGDX height brush tool: {@link InputProcessor} for painting, {@link TerrainPicker} for
 * ray picking, and {@link HeightBrushPreview} for on-terrain ring overlay.
 */
public final class HeightBrushTool implements InputProcessor, Disposable {

    /** Supplies camera rays and position for terrain picking. */
    public interface PickContext {
        Ray pickRay(int screenX, int screenY);
    }

    private final TerrainPicker picker = new TerrainPicker();
    private final TerrainPicker.PickResult pickResult = new TerrainPicker.PickResult();
    private final HeightBrushPreview preview = new HeightBrushPreview();
    private final Vector3 previewCenterGdx = new Vector3();
    private final HeightBrushSettings fallbackSettings = new HeightBrushSettings();

    private TerrainHeightGrid grid;
    private HeightBrushPainter painter;
    private HeightmapBlock[] blocks;
    private int blockCount;
    private PickContext pickContext;

    private boolean active;
    private boolean painting;
    private boolean hasPreviewCenter;

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        if (!active) {
            painting = false;
            hasPreviewCenter = false;
            if (painter != null) {
                painter.endStroke();
                painter.resetPick();
            }
        }
    }

    public void toggleActive() {
        setActive(!active);
    }

    public HeightBrushSettings settings() {
        return painter != null ? painter.settings() : fallbackSettings;
    }

    public void bindTerrain(TerrainHeightGrid grid) {
        this.grid = grid;
        if (grid == null) {
            painter = null;
            return;
        }
        painter = new HeightBrushPainter(grid);
    }

    public int saveAll() throws java.io.IOException {
        if (grid == null) {
            return 0;
        }
        return grid.saveAllModified();
    }

    public void setTerrain(HeightmapBlock[] blocks, int blockCount, PickContext pickContext) {
        this.blocks = blocks;
        this.blockCount = blockCount;
        this.pickContext = pickContext;
        if (blocks == null || blockCount <= 0) {
            if (painter != null) {
                painter.resetPick();
            }
        }
    }

    public void clearTerrain() {
        blocks = null;
        blockCount = 0;
        pickContext = null;
        grid = null;
        painter = null;
        painting = false;
        hasPreviewCenter = false;
    }

    /** Updates pick position from the current pointer (call each frame while active). */
    public void updatePointer(int screenX, int screenY) {
        if (!active || painter == null || pickContext == null || blocks == null || blockCount <= 0) {
            return;
        }

        // libGDX Camera.getPickRay expects screen coords with origin top-left (same as Input).
        Ray ray = pickContext.pickRay(screenX, screenY);
        TerrainPicker.PickResult picked = picker.pick(ray, blocks, blockCount, pickResult);
        if (picked == null) {
            return;
        }
        painter.setPickRose(picked.rose);
        previewCenterGdx.set(picked.gdx);
        hasPreviewCenter = true;
    }

    public void renderPreview(Matrix4 projection) {
        if (!active || !hasPreviewCenter || grid == null) {
            return;
        }
        preview.render(projection, previewCenterGdx, settings(), grid);
    }

    private void paintAt(int screenX, int screenY) {
        updatePointer(screenX, screenY);
        if (painter != null && hasPreviewCenter) {
            painter.applyStroke();
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (!active || button != Input.Buttons.LEFT || painter == null) {
            return false;
        }
        painting = true;
        paintAt(screenX, screenY);
        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (!active || button != Input.Buttons.LEFT) {
            return false;
        }
        boolean handled = painting;
        if (painting && painter != null) {
            painter.endStroke();
        }
        painting = false;
        return handled;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        if (painting && painter != null) {
            painter.endStroke();
        }
        painting = false;
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (!active || !painting || painter == null) {
            return false;
        }
        paintAt(screenX, screenY);
        return true;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return false;
    }

    @Override
    public void dispose() {
        preview.dispose();
    }
}
