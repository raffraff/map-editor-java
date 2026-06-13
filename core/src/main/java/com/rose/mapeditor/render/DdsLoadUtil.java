package com.rose.mapeditor.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.github.terefang.gdx.ddsdxt.load.DDSLoader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads Rose .DDS files - uncompressed BGRA8888 or DXT via {@link DDSLoader}. */
public final class DdsLoadUtil {

    private static final int DDS_HEADER = 128;
    /** Older baked files wrote a 112-byte header (one reserved dword short). */
    private static final int LEGACY_BROKEN_HEADER = 112;
    /** Baked files missing the 20-byte caps block placed pixels at offset 108. */
    private static final int LEGACY_CAPS_HEADER = 108;
    private static final int DDPF_RGB = 0x40;
    private static final int DDPF_ALPHAPIXELS = 0x1;
    private static final int DDPF_FOURCC = 0x4;

    public static final class BgraImage {
        public final int width;
        public final int height;
        public final byte[] pixels;

        public BgraImage(int width, int height, byte[] pixels) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }
    }

    private DdsLoadUtil() {
    }

    public static Texture load(Path ddsPath) {
        if (ddsPath == null || !Files.isRegularFile(ddsPath)) {
            return null;
        }

        byte[] file;
        try {
            file = Files.readAllBytes(ddsPath);
        } catch (IOException e) {
            Gdx.app.error("DdsLoadUtil", "Failed reading DDS: " + ddsPath, e);
            return null;
        }

        String label = ddsPath.toString();
        BgraImage bgra = readUncompressedBgra8888(file);
        if (bgra != null) {
            Texture texture = textureFromBgra(bgra);
            if (texture != null) {
                return texture;
            }
        }

        try {
            String absolute = ddsPath.toAbsolutePath().toString();
            Texture texture = new Texture(DDSLoader.fromDdsToTexture(Gdx.files.absolute(absolute)));
            configureWrap(texture);
            return texture;
        } catch (Exception compressedFailure) {
            Gdx.app.debug("DdsLoadUtil",
                "DDSLoader failed for " + ddsPath + ": " + compressedFailure.getMessage());
        }

        try {
            return textureFromBgra(decodeBgra8888(file, label));
        } catch (IOException fallbackFailure) {
            Gdx.app.error("DdsLoadUtil", "Failed decoding DDS: " + ddsPath, fallbackFailure);
        }

        return null;
    }

    public static Texture textureFromBgra(BgraImage image) {
        if (image == null || image.width <= 0 || image.height <= 0 || image.pixels == null) {
            return null;
        }
        int expected = image.width * image.height * 4;
        if (image.pixels.length != expected) {
            return null;
        }

        Pixmap pixmap = new Pixmap(image.width, image.height, Pixmap.Format.RGBA8888);
        byte[] bgra = image.pixels;
        int width = image.width;
        for (int y = 0; y < image.height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (y * width + x) * 4;
                int b = bgra[i] & 0xFF;
                int g = bgra[i + 1] & 0xFF;
                int r = bgra[i + 2] & 0xFF;
                int a = bgra[i + 3] & 0xFF;
                pixmap.drawPixel(x, y, (r << 24) | (g << 16) | (b << 8) | a);
            }
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        configureWrap(texture);
        return texture;
    }

    /** Reads an uncompressed BGRA8888 DDS (any square or rectangular size). */
    public static BgraImage readUncompressedBgra8888(Path path) throws IOException {
        return readUncompressedBgra8888(Files.readAllBytes(path));
    }

    /**
     * Reads a DDS file into BGRA8888 for headless tooling (Flyff import, texture baking).
     * Supports uncompressed BGRA8888 and DXT1/DXT3/DXT5 block compression.
     */
    public static BgraImage readBgra8888(Path path) throws IOException {
        return decodeBgra8888(Files.readAllBytes(path), path.toString());
    }

    private static BgraImage decodeBgra8888(byte[] file, String label) throws IOException {
        BgraImage image = readUncompressedBgra8888(file);
        if (image != null) {
            return image;
        }
        if (file.length < DDS_HEADER || file[0] != 'D' || file[1] != 'D' || file[2] != 'S' || file[3] != ' ') {
            throw new IOException("Not a DDS file: " + label);
        }
        if (!isFourCcCompressed(file)) {
            throw new IOException("Unsupported uncompressed DDS pixel format: " + label);
        }
        return readFourCcBgra8888(file, label);
    }

    static BgraImage readUncompressedBgra8888(byte[] file) {
        if (file.length < 20) {
            return null;
        }
        if (file[0] != 'D' || file[1] != 'D' || file[2] != 'S' || file[3] != ' ') {
            return null;
        }

        ByteBuffer header = ByteBuffer.wrap(file, 0, Math.min(DDS_HEADER, file.length)).order(ByteOrder.LITTLE_ENDIAN);
        header.position(12);
        int height = header.getInt();
        int width = header.getInt();
        if (width <= 0 || height <= 0) {
            return null;
        }

        int pixelBytes = width * height * 4;
        if (isFourCcCompressed(file)) {
            return null;
        }

        BgraImage image = readBgraAtOffset(file, width, height, pixelBytes, DDS_HEADER);
        if (image != null && (hasValidPixelFormat(file) || file.length == DDS_HEADER + pixelBytes)) {
            return image;
        }

        for (int legacyOffset : new int[] { LEGACY_CAPS_HEADER, LEGACY_BROKEN_HEADER }) {
            image = readBgraAtOffset(file, width, height, pixelBytes, legacyOffset);
            if (image != null && (hasValidPixelFormat(file) || file.length == legacyOffset + pixelBytes)) {
                return image;
            }
        }

        return null;
    }

    private static boolean isFourCcCompressed(byte[] file) {
        if (file.length < 88) {
            return false;
        }
        ByteBuffer header = ByteBuffer.wrap(file, 76, 12).order(ByteOrder.LITTLE_ENDIAN);
        header.getInt(); // pixel format size
        int pixelFormatFlags = header.getInt();
        return (pixelFormatFlags & DDPF_FOURCC) != 0;
    }

    private static BgraImage readBgraAtOffset(byte[] file, int width, int height, int pixelBytes, int headerSize) {
        if (file.length < headerSize + pixelBytes) {
            return null;
        }
        byte[] pixels = new byte[pixelBytes];
        System.arraycopy(file, headerSize, pixels, 0, pixelBytes);
        fixRgbOnlyAlpha(file, pixels);
        return new BgraImage(width, height, pixels);
    }

    /**
     * Uncompressed RGB8888 DDS (DDPF_RGB without DDPF_ALPHAPIXELS) often stores zero in the
     * fourth byte. Treat those texels as fully opaque so the object shader does not discard them.
     */
    private static void fixRgbOnlyAlpha(byte[] file, byte[] pixels) {
        if (isFourCcCompressed(file) || (readPixelFormatFlags(file) & DDPF_ALPHAPIXELS) != 0) {
            return;
        }
        for (int i = 3; i < pixels.length; i += 4) {
            pixels[i] = (byte) 255;
        }
    }

    private static int readPixelFormatFlags(byte[] file) {
        if (file.length < 88) {
            return 0;
        }
        return ByteBuffer.wrap(file, 80, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static boolean hasValidPixelFormat(byte[] file) {
        if (file.length < 88) {
            return false;
        }
        ByteBuffer header = ByteBuffer.wrap(file, 76, 12).order(ByteOrder.LITTLE_ENDIAN);
        int pixelFormatSize = header.getInt();
        if (pixelFormatSize != 32) {
            return false;
        }
        int pixelFormatFlags = header.getInt();
        if ((pixelFormatFlags & DDPF_FOURCC) != 0) {
            return false;
        }
        return (pixelFormatFlags & (DDPF_RGB | DDPF_ALPHAPIXELS)) != 0;
    }

    public static void configureWrap(Texture texture) {
        texture.setWrap(TextureWrap.MirroredRepeat, TextureWrap.MirroredRepeat);
    }

    private static BgraImage readFourCcBgra8888(byte[] file, String label) throws IOException {
        ByteBuffer header = ByteBuffer.wrap(file, 0, DDS_HEADER).order(ByteOrder.LITTLE_ENDIAN);
        header.position(12);
        int height = header.getInt();
        int width = header.getInt();
        if (width <= 0 || height <= 0) {
            throw new IOException("Invalid DDS dimensions in " + label);
        }

        String fourCc = new String(file, 84, 4, java.nio.charset.StandardCharsets.US_ASCII);
        int blocksX = Math.max(1, (width + 3) / 4);
        int blocksY = Math.max(1, (height + 3) / 4);
        byte[] pixels = new byte[width * height * 4];
        int dataOffset = DDS_HEADER;

        switch (fourCc) {
            case "DXT1":
                if (file.length < dataOffset + blocksX * blocksY * 8L) {
                    throw new IOException("Truncated DXT1 DDS: " + label);
                }
                decodeDxt1Blocks(file, dataOffset, blocksX, blocksY, width, height, pixels, null);
                break;
            case "DXT3":
                if (file.length < dataOffset + blocksX * blocksY * 16L) {
                    throw new IOException("Truncated DXT3 DDS: " + label);
                }
                decodeDxt3Blocks(file, dataOffset, blocksX, blocksY, width, height, pixels);
                break;
            case "DXT5":
                if (file.length < dataOffset + blocksX * blocksY * 16L) {
                    throw new IOException("Truncated DXT5 DDS: " + label);
                }
                decodeDxt5Blocks(file, dataOffset, blocksX, blocksY, width, height, pixels);
                break;
            default:
                throw new IOException("Unsupported compressed DDS fourCC '" + fourCc + "': " + label);
        }
        return new BgraImage(width, height, pixels);
    }

    private static void decodeDxt1Blocks(
        byte[] file,
        int dataOffset,
        int blocksX,
        int blocksY,
        int width,
        int height,
        byte[] pixels,
        int[] alphaBlock
    ) {
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                int blockOffset = dataOffset + (by * blocksX + bx) * 8;
                decodeDxt1ColorBlock(file, blockOffset, bx * 4, by * 4, width, height, pixels, alphaBlock);
            }
        }
    }

    private static void decodeDxt3Blocks(
        byte[] file,
        int dataOffset,
        int blocksX,
        int blocksY,
        int width,
        int height,
        byte[] pixels
    ) {
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                int blockOffset = dataOffset + (by * blocksX + bx) * 16;
                int[] alphaBlock = new int[16];
                for (int row = 0; row < 4; row++) {
                    int rowAlpha = readU16Le(file, blockOffset + row * 2);
                    for (int col = 0; col < 4; col++) {
                        int nibble = (rowAlpha >> (col * 4)) & 0xF;
                        alphaBlock[row * 4 + col] = (nibble * 255) / 15;
                    }
                }
                decodeDxt1ColorBlock(file, blockOffset + 8, bx * 4, by * 4, width, height, pixels, alphaBlock);
            }
        }
    }

    private static void decodeDxt5Blocks(
        byte[] file,
        int dataOffset,
        int blocksX,
        int blocksY,
        int width,
        int height,
        byte[] pixels
    ) {
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                int blockOffset = dataOffset + (by * blocksX + bx) * 16;
                int[] alphaBlock = decodeDxt5AlphaBlock(file, blockOffset);
                decodeDxt1ColorBlock(file, blockOffset + 8, bx * 4, by * 4, width, height, pixels, alphaBlock);
            }
        }
    }

    private static int[] decodeDxt5AlphaBlock(byte[] file, int offset) {
        int a0 = file[offset] & 0xFF;
        int a1 = file[offset + 1] & 0xFF;
        long bits = 0;
        for (int i = 0; i < 6; i++) {
            bits |= (long) (file[offset + 2 + i] & 0xFF) << (8 * i);
        }

        int[] palette = new int[8];
        palette[0] = a0;
        palette[1] = a1;
        if (a0 > a1) {
            for (int i = 2; i < 8; i++) {
                palette[i] = ((a0 * (8 - i)) + (a1 * (i - 1))) / 7;
            }
        } else {
            for (int i = 2; i < 6; i++) {
                palette[i] = ((a0 * (6 - i)) + (a1 * (i - 1))) / 5;
            }
            palette[6] = 0;
            palette[7] = 255;
        }

        int[] alphaBlock = new int[16];
        for (int i = 0; i < 16; i++) {
            int index = (int) ((bits >> (3 * i)) & 7);
            alphaBlock[i] = palette[index];
        }
        return alphaBlock;
    }

    private static void decodeDxt1ColorBlock(
        byte[] file,
        int offset,
        int blockX,
        int blockY,
        int width,
        int height,
        byte[] pixels,
        int[] alphaBlock
    ) {
        int c0 = readU16Le(file, offset);
        int c1 = readU16Le(file, offset + 2);
        int indices = readU32Le(file, offset + 4);

        int[] rgb0 = expand565(c0);
        int[] rgb1 = expand565(c1);
        int[][] colors = new int[4][3];
        int[] alphas = new int[4];
        colors[0] = rgb0;
        colors[1] = rgb1;
        if (c0 > c1) {
            colors[2] = lerpRgb(rgb0, rgb1, 2, 1);
            colors[3] = lerpRgb(rgb0, rgb1, 1, 2);
            alphas[0] = alphas[1] = alphas[2] = alphas[3] = 255;
        } else {
            colors[2] = lerpRgb(rgb0, rgb1, 1, 1);
            colors[3] = new int[] { 0, 0, 0 };
            alphas[0] = alphas[1] = alphas[2] = 255;
            alphas[3] = 0;
        }

        for (int py = 0; py < 4; py++) {
            for (int px = 0; px < 4; px++) {
                int x = blockX + px;
                int y = blockY + py;
                if (x >= width || y >= height) {
                    continue;
                }
                int pixelIndex = py * 4 + px;
                int colorIndex = (indices >> (pixelIndex * 2)) & 3;
                int a = alphaBlock != null ? alphaBlock[pixelIndex] : alphas[colorIndex];
                putBgraPixel(pixels, width, x, y, colors[colorIndex][0], colors[colorIndex][1], colors[colorIndex][2], a);
            }
        }
    }

    private static int[] expand565(int color) {
        int r = (color >> 11) & 0x1F;
        int g = (color >> 5) & 0x3F;
        int b = color & 0x1F;
        r = (r << 3) | (r >> 2);
        g = (g << 2) | (g >> 4);
        b = (b << 3) | (b >> 2);
        return new int[] { r, g, b };
    }

    private static int[] lerpRgb(int[] a, int[] b, int weightA, int weightB) {
        int denom = weightA + weightB;
        return new int[] {
            (a[0] * weightA + b[0] * weightB) / denom,
            (a[1] * weightA + b[1] * weightB) / denom,
            (a[2] * weightA + b[2] * weightB) / denom
        };
    }

    private static void putBgraPixel(byte[] pixels, int width, int x, int y, int r, int g, int b, int a) {
        int index = (y * width + x) * 4;
        pixels[index] = (byte) b;
        pixels[index + 1] = (byte) g;
        pixels[index + 2] = (byte) r;
        pixels[index + 3] = (byte) a;
    }

    private static int readU16Le(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readU32Le(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }
}
