package com.rose.mapeditor.desktop;

import com.rose.mapeditor.config.EditorConfig;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import java.io.File;

/** Native folder picker for the Rose Online client root. */
public final class GameRootChooser {

    private GameRootChooser() {
    }

    public static String promptForGameRoot() {
        try {
            JFileChooser chooser = createChooser();
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
                return null;
            }

            File selected = chooser.getSelectedFile();
            return selected != null ? selected.getAbsolutePath() : null;
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(
                null,
                "Failed to open the folder chooser.\n\n" + e.getMessage(),
                "Rose Map Editor",
                JOptionPane.ERROR_MESSAGE
            );
            return null;
        }
    }

    private static JFileChooser createChooser() {
        File startDir = resolveStartDirectory();
        JFileChooser chooser = startDir != null ? new JFileChooser(startDir) : new JFileChooser();
        chooser.setDialogTitle("Select Rose Online game folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        return chooser;
    }

    private static File resolveStartDirectory() {
        String saved = EditorConfig.loadGameRoot();
        if (EditorConfig.isValidGameRoot(saved)) {
            return new File(saved);
        }

        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            return null;
        }

        File home = new File(userHome);
        return home.isDirectory() ? home : null;
    }

    public static void showInvalidFolderMessage() {
        JOptionPane.showMessageDialog(
            null,
            "The selected folder is not a valid Rose Online client.\n\n"
                + "Choose the folder that contains the 3DDATA directory",
            "Invalid game folder",
            JOptionPane.ERROR_MESSAGE
        );
    }

    public static void showCancelledMessage() {
        JOptionPane.showMessageDialog(
            null,
            "A game folder is required to run the map editor.",
            "Rose Map Editor",
            JOptionPane.INFORMATION_MESSAGE
        );
    }
}
