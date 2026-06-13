package com.rose.mapeditor.map.flyff;

import com.rose.mapeditor.map.DdsBgraWriter;
import com.rose.mapeditor.render.DdsLoadUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Converts uncompressed Flyff terrain images (TGA/BMP) into Rose-compatible BGRA8888 DDS. */
public final class FlyffTgaToDds {

    private FlyffTgaToDds() {
    }

    public static void convertIfNeeded(Path sourceImage, Path destinationDds) throws IOException {
        if (Files.isRegularFile(destinationDds)
            && Files.getLastModifiedTime(destinationDds).compareTo(Files.getLastModifiedTime(sourceImage)) >= 0) {
            return;
        }

        String lower = sourceImage.getFileName().toString().toLowerCase();
        byte[] bgra;
        int width;
        int height;
        if (lower.endsWith(".tga")) {
            DecodedImage image = decodeUncompressedTga(sourceImage);
            width = image.width;
            height = image.height;
            bgra = image.bgra;
        } else if (lower.endsWith(".bmp")) {
            DecodedImage image = decodeBmp(sourceImage);
            width = image.width;
            height = image.height;
            bgra = image.bgra;
        } else if (lower.endsWith(".dds")) {
            Files.createDirectories(destinationDds.getParent());
            Files.copy(sourceImage, destinationDds, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return;
        } else {
            throw new IOException("Unsupported Flyff terrain texture format: " + sourceImage);
        }

        DdsBgraWriter.write(destinationDds, width, height, bgra);
    }

    /** Loads a Flyff terrain image into memory as BGRA8888. */
    public static FlyffTextureImage loadBgra(Path sourceImage) throws IOException {
        String lower = sourceImage.getFileName().toString().toLowerCase();
        DecodedImage image;
        if (lower.endsWith(".tga")) {
            image = decodeUncompressedTga(sourceImage);
        } else if (lower.endsWith(".bmp")) {
            image = decodeBmp(sourceImage);
        } else if (lower.endsWith(".dds")) {
            DdsLoadUtil.BgraImage ddsImage = DdsLoadUtil.readBgra8888(sourceImage);
            return new FlyffTextureImage(ddsImage.width, ddsImage.height, ddsImage.pixels);
        } else {
            throw new IOException("Unsupported Flyff terrain texture format: " + sourceImage);
        }
        return new FlyffTextureImage(image.width, image.height, image.bgra);
    }

    /** Writes raw BGRA8888 pixels to an uncompressed DDS file. */
    public static void writeBgra(Path destinationDds, int width, int height, byte[] bgra) throws IOException {
        DdsBgraWriter.write(destinationDds, width, height, bgra);
    }

    private static final class DecodedImage {
        final int width;
        final int height;
        final byte[] bgra;

        DecodedImage(int width, int height, byte[] bgra) {
            this.width = width;
            this.height = height;
            this.bgra = bgra;
        }
    }

    private static DecodedImage decodeUncompressedTga(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        if (data.length < 18) {
            throw new IOException("TGA too small: " + path);
        }

        int imageType = Byte.toUnsignedInt(data[2]);
        if (imageType != 2 && imageType != 10) {
            throw new IOException("Only uncompressed/RLE true-color TGA supported: " + path);
        }

        int width = readShortLE(data, 12);
        int height = readShortLE(data, 14);
        int bpp = Byte.toUnsignedInt(data[16]);
        if (width <= 0 || height <= 0) {
            throw new IOException("Invalid TGA dimensions in " + path);
        }
        if (bpp != 24 && bpp != 32) {
            throw new IOException("Unsupported TGA bpp (" + bpp + ") in " + path);
        }

        int idLength = Byte.toUnsignedInt(data[0]);
        int offset = 18 + idLength;
        byte[] bgra = new byte[width * height * 4];
        int index = 0;

        if (imageType == 2) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (offset + bpp / 8 > data.length) {
                        throw new IOException("Truncated TGA pixel data: " + path);
                    }
                    int b = Byte.toUnsignedInt(data[offset++]);
                    int g = Byte.toUnsignedInt(data[offset++]);
                    int r = Byte.toUnsignedInt(data[offset++]);
                    int a = bpp == 32 ? Byte.toUnsignedInt(data[offset++]) : 255;
                    bgra[index++] = (byte) b;
                    bgra[index++] = (byte) g;
                    bgra[index++] = (byte) r;
                    bgra[index++] = (byte) a;
                }
            }
        } else {
            while (index < bgra.length) {
                if (offset >= data.length) {
                    throw new IOException("Truncated RLE TGA data: " + path);
                }
                int packet = Byte.toUnsignedInt(data[offset++]);
                int count = (packet & 0x7F) + 1;
                if ((packet & 0x80) != 0) {
                    if (offset + bpp / 8 > data.length) {
                        throw new IOException("Truncated RLE TGA packet: " + path);
                    }
                    int b = Byte.toUnsignedInt(data[offset++]);
                    int g = Byte.toUnsignedInt(data[offset++]);
                    int r = Byte.toUnsignedInt(data[offset++]);
                    int a = bpp == 32 ? Byte.toUnsignedInt(data[offset++]) : 255;
                    for (int i = 0; i < count && index < bgra.length; i++) {
                        bgra[index++] = (byte) b;
                        bgra[index++] = (byte) g;
                        bgra[index++] = (byte) r;
                        bgra[index++] = (byte) a;
                    }
                } else {
                    for (int i = 0; i < count && index < bgra.length; i++) {
                        if (offset + bpp / 8 > data.length) {
                            throw new IOException("Truncated RLE TGA raw packet: " + path);
                        }
                        int b = Byte.toUnsignedInt(data[offset++]);
                        int g = Byte.toUnsignedInt(data[offset++]);
                        int r = Byte.toUnsignedInt(data[offset++]);
                        int a = bpp == 32 ? Byte.toUnsignedInt(data[offset++]) : 255;
                        bgra[index++] = (byte) b;
                        bgra[index++] = (byte) g;
                        bgra[index++] = (byte) r;
                        bgra[index++] = (byte) a;
                    }
                }
            }
        }

        return new DecodedImage(width, height, bgra);
    }

    private static DecodedImage decodeBmp(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        if (data.length < 54) {
            throw new IOException("BMP too small: " + path);
        }
        int pixelOffset = readIntLE(data, 10);
        int width = readIntLE(data, 18);
        int height = Math.abs(readIntLE(data, 22));
        int bpp = readShortLE(data, 28);
        if (bpp != 24 && bpp != 32) {
            throw new IOException("Unsupported BMP bpp (" + bpp + "): " + path);
        }

        byte[] bgra = new byte[width * height * 4];
        int rowBytes = ((bpp * width + 31) / 32) * 4;
        int index = 0;
        for (int y = 0; y < height; y++) {
            int row = height - 1 - y;
            int rowStart = pixelOffset + row * rowBytes;
            for (int x = 0; x < width; x++) {
                int p = rowStart + x * (bpp / 8);
                int b = Byte.toUnsignedInt(data[p]);
                int g = Byte.toUnsignedInt(data[p + 1]);
                int r = Byte.toUnsignedInt(data[p + 2]);
                int a = bpp == 32 ? Byte.toUnsignedInt(data[p + 3]) : 255;
                bgra[index++] = (byte) b;
                bgra[index++] = (byte) g;
                bgra[index++] = (byte) r;
                bgra[index++] = (byte) a;
            }
        }
        return new DecodedImage(width, height, bgra);
    }

    private static int readShortLE(byte[] data, int offset) {
        return Byte.toUnsignedInt(data[offset]) | (Byte.toUnsignedInt(data[offset + 1]) << 8);
    }

    private static int readIntLE(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }
}
