package com.rose.mapeditor.map;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes uncompressed BGRA8888 .DDS textures with a standard 128-byte header. */
public final class DdsBgraWriter {

    private static final int BPP = 32;

    private DdsBgraWriter() {
    }

    public static void write(Path destination, int width, int height, byte[] pixels) throws IOException {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid dimensions: " + width + "x" + height);
        }
        int expected = width * height * 4;
        if (pixels.length != expected) {
            throw new IllegalArgumentException("Expected " + expected + " bytes, got " + pixels.length);
        }

        Files.createDirectories(destination.getParent());

        ByteBuffer buffer = ByteBuffer.allocate(128 + expected).order(ByteOrder.LITTLE_ENDIAN);
        writeHeader(buffer, width, height);
        if (buffer.position() != 128) {
            throw new IllegalStateException("DDS header must be 128 bytes, was " + buffer.position());
        }
        buffer.put(pixels);

        buffer.flip();
        try (OutputStream out = Files.newOutputStream(destination)) {
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            out.write(data);
        }
    }

    static void writeHeader(ByteBuffer buffer, int width, int height) {
        buffer.put((byte) 'D');
        buffer.put((byte) 'D');
        buffer.put((byte) 'S');
        buffer.put((byte) ' ');
        buffer.putInt(124);
        buffer.putInt(0x0000100F);
        buffer.putInt(height);
        buffer.putInt(width);
        buffer.putInt(width * 4);
        buffer.putInt(0); // depth
        buffer.putInt(0); // mip map count
        for (int i = 0; i < 11; i++) {
            buffer.putInt(0); // reserved
        }
        buffer.putInt(32); // pixel format size
        buffer.putInt(0x41); // RGB | ALPHAPIXELS
        buffer.putInt(0); // fourCC
        buffer.putInt(BPP);
        buffer.putInt(0x00ff0000);
        buffer.putInt(0x0000ff00);
        buffer.putInt(0x000000ff);
        buffer.putInt(0xff000000);
        buffer.putInt(0); // dwCaps
        buffer.putInt(0); // dwCaps2
        buffer.putInt(0); // dwCaps3
        buffer.putInt(0); // dwReserved2
        buffer.putInt(0); // dwReserved3
    }

    public static byte[] neutralPixels(int width, int height) {
        byte[] pixels = new byte[width * height * 4];
        for (int i = 0; i < width * height; i++) {
            int offset = i * 4;
            pixels[offset] = (byte) 153;
            pixels[offset + 1] = (byte) 136;
            pixels[offset + 2] = (byte) 136;
            pixels[offset + 3] = (byte) 255;
        }
        return pixels;
    }
}
