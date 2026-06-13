package com.rose.mapeditor.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

/** Resets OpenGL state after custom 3D rendering so libGDX UI can draw correctly. */
public final class GlState {

    private GlState() {
    }

    /**
     * Restores blend/depth/cull state and unbinds the active 3D shader program.
     * Without this, Scene2D labels can pick up terrain/object textures and look like
     * corrupted texture swatches instead of text.
     */
    public static void resetForUi() {
        WireframeGl.disable();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glUseProgram(0);
    }
}
