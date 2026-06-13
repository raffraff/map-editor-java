package com.rose.mapeditor.tool;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.collision.Ray;
import com.rose.mapeditor.scene.MapScene;
import com.rose.mapeditor.scene.SceneObjectRef;

/** Left-click in the viewport to inspect a map object. */
public final class SceneInspectInput implements InputProcessor {

    public interface Listener {
        boolean canInspect();

        Ray pickRay(int screenX, int screenY);

        MapScene scene();

        void onObjectPicked(SceneObjectRef ref);
    }

    private final SceneObjectPicker picker = new SceneObjectPicker();
    private final SceneObjectPicker.PickResult pickResult = new SceneObjectPicker.PickResult();
    private final Listener listener;

    public SceneInspectInput(Listener listener) {
        this.listener = listener;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button != Input.Buttons.LEFT || listener == null || !listener.canInspect()) {
            return false;
        }

        MapScene scene = listener.scene();
        if (scene == null) {
            return false;
        }

        Ray ray = listener.pickRay(screenX, screenY);
        SceneObjectPicker.PickResult picked = picker.pick(scene, ray, pickResult);
        if (picked == null || picked.ref == null) {
            return false;
        }

        listener.onObjectPicked(picked.ref);
        return true;
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
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return false;
    }
}
