package com.rose.mapeditor.desktop;

import com.rose.mapeditor.ui.GameRootPicker;

import javax.swing.SwingUtilities;

/** Wires the native folder picker into the core editor UI. */
public final class DesktopGameRootPicker {

    private DesktopGameRootPicker() {
    }

    public static void install() {
        GameRootPicker.setPrompter(() -> {
            try {
                final String[] chosen = new String[1];
                SwingUtilities.invokeAndWait(() -> chosen[0] = GameRootResolver.chooseGameRoot());
                return chosen[0];
            } catch (Exception e) {
                throw new RuntimeException("Failed to open folder chooser", e);
            }
        });
    }
}
