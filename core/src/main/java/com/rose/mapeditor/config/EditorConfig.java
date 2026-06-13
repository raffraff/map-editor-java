package com.rose.mapeditor.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Persists editor settings outside the game client folder. */
public final class EditorConfig {

    private static final String KEY_GAME_ROOT = "gameRoot";

    private EditorConfig() {
    }

    public static Path configFile() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path configDir = localAppData != null && !localAppData.isBlank()
            ? Path.of(localAppData, "RoseMapEditor")
            : Path.of(System.getProperty("user.home"), ".rose-map-editor");
        return configDir.resolve("editor.properties");
    }

    public static String loadGameRoot() {
        Path file = configFile();
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException e) {
            return null;
        }
        String value = properties.getProperty(KEY_GAME_ROOT);
        return value != null ? value.trim() : null;
    }

    public static void saveGameRoot(String gameRoot) throws IOException {
        if (gameRoot == null || gameRoot.isBlank()) {
            throw new IOException("Game root path is empty");
        }
        Path file = configFile();
        Files.createDirectories(file.getParent());

        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            }
        }

        properties.setProperty(KEY_GAME_ROOT, normalizeGameRoot(gameRoot));
        try (OutputStream output = Files.newOutputStream(file)) {
            properties.store(output, "Rose Map Editor settings");
        }
    }

    public static boolean isValidGameRoot(String gameRoot) {
        if (gameRoot == null || gameRoot.isBlank()) {
            return false;
        }
        try {
            return Files.isDirectory(Path.of(gameRoot.trim()).resolve("3DDATA"));
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String normalizeGameRoot(String gameRoot) {
        try {
            return Path.of(gameRoot.trim()).toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid game root path: " + gameRoot, e);
        }
    }
}
