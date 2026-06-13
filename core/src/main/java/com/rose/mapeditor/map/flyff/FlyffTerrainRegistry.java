package com.rose.mapeditor.map.flyff;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Parses Flyff / terrain registry scripts (terrain entries in .inc files). */
public final class FlyffTerrainRegistry {

    private final Map<Integer, String> textureFiles = new HashMap<>();

    public Map<Integer, String> entries() {
        return textureFiles;
    }

    public String filenameFor(int textureId) {
        return textureFiles.get(textureId);
    }

    public static FlyffTerrainRegistry load(Path terrainIncPath) throws IOException {
        if (!Files.isRegularFile(terrainIncPath)) {
            throw new IOException("Terrain registry not found: " + terrainIncPath);
        }

        FlyffTerrainRegistry registry = new FlyffTerrainRegistry();
        String text = Files.readString(terrainIncPath, StandardCharsets.UTF_8);
        String[] tokens = tokenize(text);

        for (int i = 0; i + 2 < tokens.length; i++) {
            Integer id = tryParseInt(tokens[i]);
            if (id == null) {
                continue;
            }
            Integer frameCount = tryParseInt(tokens[i + 1]);
            if (frameCount == null) {
                continue;
            }
            String filename = unquote(tokens[i + 2]);
            if (!looksLikeTextureFile(filename)) {
                continue;
            }
            registry.textureFiles.put(id, filename);
            i += 2;
        }

        if (registry.textureFiles.isEmpty()) {
            throw new IOException("No terrain texture entries found in: " + terrainIncPath);
        }
        return registry;
    }

    public static Path findTerrainInc(Path flyffRoot) throws IOException {
        Path projectFile = flyffRoot.resolve("Masquerade.prj");
        if (!Files.isRegularFile(projectFile)) {
            projectFile = flyffRoot.resolve("masquerade.prj");
        }
        if (!Files.isRegularFile(projectFile)) {
            throw new IOException("Masquerade.prj not found under Flyff root: " + flyffRoot);
        }

        String text = Files.readString(projectFile, StandardCharsets.UTF_8);
        String[] tokens = tokenize(text);
        for (int i = 0; i < tokens.length - 1; i++) {
            if ("terrain".equalsIgnoreCase(tokens[i])) {
                String path = unquote(tokens[i + 1]);
                Path resolved = flyffRoot.resolve(path.replace('\\', '/'));
                if (Files.isRegularFile(resolved)) {
                    return resolved;
                }
                Path cwdRelative = Path.of(path.replace('\\', '/'));
                if (Files.isRegularFile(cwdRelative)) {
                    return cwdRelative;
                }
            }
        }
        throw new IOException("No terrain registry referenced in Masquerade.prj under " + flyffRoot);
    }

    private static boolean looksLikeTextureFile(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".tga") || lower.endsWith(".dds") || lower.endsWith(".bmp")
            || lower.endsWith(".png");
    }

    private static Integer tryParseInt(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            if (token.startsWith("0x") || token.startsWith("0X")) {
                return (int) Long.parseLong(token.substring(2), 16);
            }
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String unquote(String token) {
        if (token == null) {
            return "";
        }
        if (token.length() >= 2 && token.startsWith("\"") && token.endsWith("\"")) {
            return token.substring(1, token.length() - 1);
        }
        return token;
    }

    private static String[] tokenize(String text) {
        StringBuilder normalized = new StringBuilder(text.length());
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                inString = !inString;
                normalized.append(c);
                continue;
            }
            if (!inString && c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                while (i < text.length() && text.charAt(i) != '\n') {
                    i++;
                }
                normalized.append(' ');
                continue;
            }
            if (!inString && (c == '{' || c == '}')) {
                normalized.append(' ');
                continue;
            }
            if (!inString && (c == ',' || c == '\n' || c == '\r' || c == '\t')) {
                normalized.append(' ');
            } else {
                normalized.append(c);
            }
        }
        String trimmed = normalized.toString().trim();
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        return trimmed.split("\\s+");
    }
}
