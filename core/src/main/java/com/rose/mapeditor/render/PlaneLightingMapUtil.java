package com.rose.mapeditor.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads Rose terrain plane lighting maps into OpenGL textures. */
public final class PlaneLightingMapUtil {

    public static final int SIZE = 512;

    private PlaneLightingMapUtil() {
    }

    public static Texture load(Path ddsPath) {
        if (ddsPath == null || !Files.isRegularFile(ddsPath)) {
            return createNeutralTexture();
        }

        Texture texture = DdsLoadUtil.load(ddsPath);
        return texture != null ? texture : createNeutralTexture();
    }

    public static Texture textureFromBgra(byte[] bgra) {
        if (bgra == null || bgra.length != SIZE * SIZE * 4) {
            return createNeutralTexture();
        }
        Texture texture = DdsLoadUtil.textureFromBgra(new DdsLoadUtil.BgraImage(SIZE, SIZE, bgra));
        return texture != null ? texture : createNeutralTexture();
    }

    /** Reads an uncompressed 512×512 BGRA8888 DDS written by {@link com.rose.mapeditor.map.PlaneLightingMapWriter}. */
    public static byte[] readUncompressedBgra8888(Path path) throws IOException {
        DdsLoadUtil.BgraImage image = DdsLoadUtil.readUncompressedBgra8888(path);
        if (image == null || image.width != SIZE || image.height != SIZE) {
            return null;
        }
        return image.pixels;
    }

    public static Texture createNeutralTexture() {
        Pixmap pixmap = new Pixmap(SIZE, SIZE, Pixmap.Format.RGB888);
        pixmap.setColor(136f / 255f, 136f / 255f, 153f / 255f, 1f);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        DdsLoadUtil.configureWrap(texture);
        return texture;
    }
}
