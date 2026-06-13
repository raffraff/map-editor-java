package com.rose.mapeditor.ui;

/** Optional platform hook for updating the window title with FPS. */
public final class WindowTitle {

    public interface Updater {
        void setTitleWithFps(int fps);
    }

    private static Updater updater;
    private static float elapsed;

    /** Minimum seconds between title bar updates (OS title changes are expensive on Windows). */
    private static final float UPDATE_INTERVAL = 0.5f;

    private WindowTitle() {
    }

    public static void setUpdater(Updater updater) {
        WindowTitle.updater = updater;
    }

    public static void updateFps(float delta, int fps) {
        if (updater == null) {
            return;
        }
        elapsed += delta;
        if (elapsed < UPDATE_INTERVAL) {
            return;
        }
        elapsed = 0f;
        updater.setTitleWithFps(fps);
    }
}
