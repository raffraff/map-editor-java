package com.rose.mapeditor.map.flyff;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Little-endian binary reader for Flyff */
final class FlyffBinaryReader implements AutoCloseable {

    private final ByteBuffer buffer;

    FlyffBinaryReader(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    int readInt() {
        return buffer.getInt();
    }

    int readUnsignedShort() {
        return Short.toUnsignedInt(buffer.getShort());
    }

    byte readByte() {
        return buffer.get();
    }

    float readFloat() {
        return buffer.getFloat();
    }

    void skip(int bytes) {
        buffer.position(buffer.position() + bytes);
    }

    int remaining() {
        return buffer.remaining();
    }

    @Override
    public void close() {
        // no-op
    }
}
