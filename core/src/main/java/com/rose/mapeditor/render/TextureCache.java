package com.rose.mapeditor.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.rose.mapeditor.GameData;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/** Loads and caches terrain/object textures (DDS/TGA paths relative to game root). */
public final class TextureCache implements Disposable {

    private static final int DDPF_ALPHAPIXELS = 0x1;

    private final Map<String, Texture> cache = new HashMap<>();
    private final Map<String, Boolean> alphaChannel = new HashMap<>();

    public Texture get(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return placeholder();
        }
        String key = normalize(relativePath);
        Texture cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Texture loaded = loadTexture(key);
        if (loaded != null) {
            cache.put(key, loaded);
            return loaded;
        }
        Texture fallback = placeholder();
        cache.put(key, fallback);
        return fallback;
    }

    /** Returns true when a texture path has been resolved (loaded or fallback cached). */
    public boolean isCached(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        return cache.containsKey(normalize(relativePath));
    }

    /** Returns true when the texture file exists on disk (absolute or relative to game root). */
    public boolean fileExists(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String key = normalize(path);
        Path keyPath = Paths.get(key);
        Path file = keyPath.isAbsolute() ? keyPath : GameData.get().resolve(key);
        return Files.isRegularFile(file);
    }

    public boolean hasAlphaChannel(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        String key = normalize(relativePath);
        get(relativePath);
        return alphaChannel.getOrDefault(key, false);
    }

    public Texture placeholder() {
        return cache.computeIfAbsent("__placeholder__", k -> {
            alphaChannel.put("__placeholder__", false);
            return createPlaceholder();
        });
    }

    private Texture loadTexture(String key) {
        Path keyPath = Paths.get(key);
        String path = keyPath.isAbsolute() ? keyPath.toString() : GameData.get().resolve(key).toString();
        alphaChannel.put(key, detectAlphaChannel(path, key));
        try {
            Texture texture;
            if (key.toLowerCase().endsWith(".dds")) {
                texture = DdsLoadUtil.load(Path.of(path));
                if (texture == null) {
                    Gdx.app.error("TextureCache", "Unsupported or unreadable DDS: " + path);
                    alphaChannel.put(key, false);
                    return null;
                }
            } else {
                texture = new Texture(Gdx.files.absolute(path));
            }
            if (Files.exists(Paths.get(path))) {
                DdsLoadUtil.configureWrap(texture);
            }
            return texture;
        } catch (Exception e) {
            Gdx.app.error("TextureCache", "Failed to load: " + path, e);
            alphaChannel.put(key, false);
            return null;
        }
    }

    private static boolean detectAlphaChannel(String path, String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".dds")) {
            return ddsFileHasAlpha(path);
        }
        if (lower.endsWith(".tga") || lower.endsWith(".png")) {
            return true;
        }
        return false;
    }

    private static boolean ddsFileHasAlpha(String path) {
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            if (file.length() < 128) {
                return false;
            }
            byte[] header = new byte[128];
            file.readFully(header);
            int pixelFormatFlags = readIntLe(header, 80);
            int fourCc = readIntLe(header, 84);
            if ((pixelFormatFlags & DDPF_ALPHAPIXELS) != 0) {
                return true;
            }
            return fourCc == fourCc("DXT3") || fourCc == fourCc("DXT5");
        } catch (IOException e) {
            return false;
        }
    }

    private static int readIntLe(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static int fourCc(String text) {
        return ((text.charAt(0)) | (text.charAt(1) << 8) | (text.charAt(2) << 16) | (text.charAt(3) << 24));
    }

    private static Texture createPlaceholder() {
        com.badlogic.gdx.graphics.Pixmap pixmap = new com.badlogic.gdx.graphics.Pixmap(4, 4,
            com.badlogic.gdx.graphics.Pixmap.Format.RGB888);
        pixmap.setColor(0.8f, 0.2f, 0.8f, 1f);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    public static String normalize(String path) {
        return path.replace('\\', '/');
    }

    /** Releases all cached textures (call when switching maps). */
    public void clear() {
        for (Texture texture : cache.values()) {
            texture.dispose();
        }
        cache.clear();
        alphaChannel.clear();
    }

    @Override
    public void dispose() {
        for (Texture texture : cache.values()) {
            texture.dispose();
        }
        cache.clear();
        alphaChannel.clear();
    }
}
