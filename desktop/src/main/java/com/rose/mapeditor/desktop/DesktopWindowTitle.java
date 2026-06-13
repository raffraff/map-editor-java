package com.rose.mapeditor.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.rose.mapeditor.ui.WindowTitle;

/** Sets the LWJGL3 window title to include the current frame rate. */
public final class DesktopWindowTitle {

    private DesktopWindowTitle() {
    }

    public static void install(String baseTitle) {
        WindowTitle.setUpdater(fps -> {
            if (Gdx.graphics instanceof Lwjgl3Graphics) {
                ((Lwjgl3Graphics) Gdx.graphics).setTitle(baseTitle + " - " + fps + " FPS");
            }
        });
    }
}
