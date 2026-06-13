package com.rose.mapeditor.map.flyff;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Parses Flyff world header scripts (.wld). */
public final class FlyffWldReader {

    public static final class WorldHeader {
        public int width;
        public int height;
        public int mpu = 4;
        public Path worldDirectory;
        public String worldBaseName;
    }

    private FlyffWldReader() {
    }

    public static WorldHeader read(Path wldPath) throws IOException {
        if (!Files.isRegularFile(wldPath)) {
            throw new IOException("Flyff world file not found: " + wldPath);
        }

        WorldHeader header = new WorldHeader();
        String text = Files.readString(wldPath, StandardCharsets.UTF_8);
        String[] tokens = tokenize(text);

        for (int i = 0; i < tokens.length; i++) {
            String key = tokens[i].toLowerCase(Locale.ROOT);
            switch (key) {
                case "size":
                    int widthIndex = ++i;
                    header.width = parseInt(nextToken(tokens, widthIndex, "size width"));
                    header.height = parseInt(nextToken(tokens, widthIndex + 1, "size height"));
                    i = widthIndex + 1;
                    break;
                case "mpu":
                    header.mpu = parseInt(nextToken(tokens, ++i, "mpu"));
                    break;
                default:
                    break;
            }
        }

        if (header.width <= 0 || header.height <= 0) {
            throw new IOException("Invalid or missing size in .wld (expected: size <width>, <height>)");
        }

        String fileName = wldPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        header.worldBaseName = dot > 0 ? fileName.substring(0, dot) : fileName;
        header.worldDirectory = wldPath.getParent() == null ? Path.of(".") : wldPath.getParent();
        return header;
    }

    /** Flyff tiles are named {@code {worldBase}{col}-{row}.lnd} beside the .wld file. */
    public static Path landscapePath(WorldHeader header, int tileCol, int tileRow) {
        String fileName = String.format(Locale.ROOT, "%s%02d-%02d.lnd",
            header.worldBaseName, tileCol, tileRow);
        return header.worldDirectory.resolve(fileName);
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
            if (!inString && (c == ',' || c == '\n' || c == '\r' || c == '\t')) {
                normalized.append(' ');
            } else {
                normalized.append(c);
            }
        }
        return normalized.toString().trim().split("\\s+");
    }

    private static String nextToken(String[] tokens, int index, String context) throws IOException {
        if (index >= tokens.length) {
            throw new IOException("Unexpected end of .wld while reading " + context);
        }
        String token = tokens[index];
        if (token.startsWith(",")) {
            token = token.substring(1);
        }
        if (token.isEmpty()) {
            throw new IOException("Missing value for " + context);
        }
        return token;
    }

    private static int parseInt(String token) throws IOException {
        try {
            if (token.startsWith("0x") || token.startsWith("0X")) {
                return (int) Long.parseLong(token.substring(2), 16);
            }
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid integer in .wld: " + token, e);
        }
    }
}
