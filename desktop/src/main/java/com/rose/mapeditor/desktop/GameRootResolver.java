package com.rose.mapeditor.desktop;

import com.rose.mapeditor.config.EditorConfig;

import java.io.IOException;

/** Resolves the Rose client root from saved settings, env/args, or a first-run prompt. */
public final class GameRootResolver {

    private GameRootResolver() {
    }

    public static String resolve(String[] args) {
        if (args != null && args.length > 0 && EditorConfig.isValidGameRoot(args[0])) {
            return EditorConfig.normalizeGameRoot(args[0]);
        }

        String saved = EditorConfig.loadGameRoot();
        if (EditorConfig.isValidGameRoot(saved)) {
            return EditorConfig.normalizeGameRoot(saved);
        }

        String env = System.getenv("ROSE_GAME_ROOT");
        if (EditorConfig.isValidGameRoot(env)) {
            return EditorConfig.normalizeGameRoot(env);
        }

        return chooseGameRoot();
    }

    /** Prompts until the user picks a valid folder or cancels. Saves the choice on success. */
    public static String chooseGameRoot() {
        while (true) {
            String chosen = GameRootChooser.promptForGameRoot();
            if (chosen == null) {
                return null;
            }
            if (!EditorConfig.isValidGameRoot(chosen)) {
                GameRootChooser.showInvalidFolderMessage();
                continue;
            }

            String normalized = EditorConfig.normalizeGameRoot(chosen);
            try {
                EditorConfig.saveGameRoot(normalized);
            } catch (IOException e) {
                System.err.println("Failed saving game root setting: " + e.getMessage());
            }
            return normalized;
        }
    }
}
