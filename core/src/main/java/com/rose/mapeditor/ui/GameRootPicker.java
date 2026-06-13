package com.rose.mapeditor.ui;

/** Optional platform hook for choosing the Rose client root folder. */
public final class GameRootPicker {

    public interface Prompter {
        /** Returns a validated, normalized path, or null if cancelled. */
        String prompt();
    }

    private static Prompter prompter;

    private GameRootPicker() {
    }

    public static void setPrompter(Prompter prompter) {
        GameRootPicker.prompter = prompter;
    }

    public static boolean isAvailable() {
        return prompter != null;
    }

    public static String prompt() {
        if (prompter == null) {
            return null;
        }
        return prompter.prompt();
    }
}
