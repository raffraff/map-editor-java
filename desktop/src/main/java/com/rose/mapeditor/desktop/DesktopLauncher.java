package com.rose.mapeditor.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.rose.mapeditor.MapEditorGame;

/** Desktop entry point. */
public final class DesktopLauncher {

    public static void main(String[] args) {
        String gameRoot = GameRootResolver.resolve(args);
        if (gameRoot == null) {
            GameRootChooser.showCancelledMessage();
            System.exit(0);
        }

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        String windowTitle = "Rose Map Editor (Java + libGDX)";
        config.setTitle(windowTitle);
        config.setWindowedMode(1280, 768);
        config.useVsync(true);
        config.setForegroundFPS(120);

        DesktopWindowTitle.install(windowTitle);
        DesktopGameRootPicker.install();
        new Lwjgl3Application(new MapEditorGame(gameRoot), config);
    }

    private DesktopLauncher() {
    }
}
