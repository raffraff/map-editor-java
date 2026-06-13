package com.rose.mapeditor.io;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Binary file reader/writer for Rose Online formats.
 */
public final class FileHandler implements Closeable {

    public enum FileOpenMode {
        READING,
        WRITING
    }

    private static final Charset EUC_KR = Charset.forName("EUC-KR");

    private final RandomAccessFile file;
    private final FileOpenMode mode;
    private final Charset encoding;

    public FileHandler(String filePath, FileOpenMode mode, Charset encoding) throws IOException {
        this.mode = mode;
        this.encoding = encoding != null ? encoding : StandardCharsets.UTF_8;
        if (mode == FileOpenMode.READING) {
            file = new RandomAccessFile(filePath, "r");
        } else {
            file = new RandomAccessFile(filePath, "rw");
            file.setLength(0);
        }
    }

    public void seek(long offset) throws IOException {
        file.seek(offset);
    }

    public long tell() throws IOException {
        return file.getFilePointer();
    }

    public byte readByte() throws IOException {
        return file.readByte();
    }

    public short readShort() throws IOException {
        return readShortLE();
    }

    public int readInt() throws IOException {
        return readIntLE();
    }

    public float readFloat() throws IOException {
        return readFloatLE();
    }

    public Vector3 readVector3() throws IOException {
        return new Vector3(readFloatLE(), readFloatLE(), readFloatLE());
    }

    public Vector2 readVector2Floats() throws IOException {
        return new Vector2(readFloatLE(), readFloatLE());
    }

    public Vector2 readVector2Ints() throws IOException {
        return new Vector2(readIntLE(), readIntLE());
    }

    /** Reads 16 floats in XNA Matrix order (M11..M44, row-major). */
    public float[] readMatrix16() throws IOException {
        float[] m = new float[16];
        for (int i = 0; i < 16; i++) {
            m[i] = readFloatLE();
        }
        return m;
    }

    public Quaternion readQuaternion() throws IOException {
        return new Quaternion(readFloatLE(), readFloatLE(), readFloatLE(), readFloatLE());
    }

    public String readLengthString(int length) throws IOException {
        byte[] data = new byte[length];
        file.readFully(data);
        return new String(data, encoding);
    }

    public String readBaseString(int length) throws IOException {
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = (char) file.readByte();
        }
        return new String(chars);
    }

    public String readNString(int length) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(length);
        for (int i = 0; i < length; i++) {
            int b = file.readUnsignedByte();
            if (b != 0 && b != 0xCD) {
                buffer.write(b);
            }
        }
        return new String(buffer.toByteArray(), encoding);
    }

    /** Null-terminated string (ZString). */
    public String readZString() throws IOException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = (char) file.readByte();
            if (c == 0) {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public String readBString() throws IOException {
        int length = read7BitEncodedInt();
        byte[] data = new byte[length];
        file.readFully(data);
        return new String(data, encoding);
    }

    public void writeInt(int value) throws IOException {
        writeIntLE(value);
    }

    public void writeShort(short value) throws IOException {
        writeShortLE(value);
    }

    public void writeFloat(float value) throws IOException {
        writeFloatLE(value);
    }

    public void writeByte(byte value) throws IOException {
        file.writeByte(value);
    }

    public void writeBaseString(String value) throws IOException {
        file.writeBytes(value);
    }

    public void writeBString(String value) throws IOException {
        byte[] data = value.getBytes(encoding);
        write7BitEncodedInt(data.length);
        file.write(data);
    }

    public void writeQuaternion(Quaternion q) throws IOException {
        writeFloatLE(q.x);
        writeFloatLE(q.y);
        writeFloatLE(q.z);
        writeFloatLE(q.w);
    }

    public void writeVector3(Vector3 v) throws IOException {
        writeFloatLE(v.x);
        writeFloatLE(v.y);
        writeFloatLE(v.z);
    }

    public void writeVector2(Vector2 v) throws IOException {
        writeFloatLE(v.x);
        writeFloatLE(v.y);
    }

    public void writeVector2Ints(Vector2 v) throws IOException {
        writeIntLE((int) v.x);
        writeIntLE((int) v.y);
    }

    public void writeMatrix16(float[] m) throws IOException {
        for (int i = 0; i < 16; i++) {
            writeFloatLE(m[i]);
        }
    }

    public void writeLengthString(String value) throws IOException {
        byte[] data = value.getBytes(encoding);
        writeShortLE((short) data.length);
        file.write(data);
    }

    public void writeBytes(byte[] data) throws IOException {
        file.write(data);
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    public static Charset eucKr() {
        return EUC_KR;
    }

    private short readShortLE() throws IOException {
        byte[] b = new byte[2];
        file.readFully(b);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    private int readIntLE() throws IOException {
        byte[] b = new byte[4];
        file.readFully(b);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private float readFloatLE() throws IOException {
        byte[] b = new byte[4];
        file.readFully(b);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    private void writeIntLE(int value) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(value);
        file.write(bb.array());
    }

    private void writeShortLE(short value) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort(value);
        file.write(bb.array());
    }

    private void writeFloatLE(float value) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        bb.putFloat(value);
        file.write(bb.array());
    }

    private int read7BitEncodedInt() throws IOException {
        int result = 0;
        int shift = 0;
        int b;
        do {
            b = file.readUnsignedByte();
            result |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return result;
    }

    private void write7BitEncodedInt(int value) throws IOException {
        int v = value;
        while (v >= 0x80) {
            file.writeByte((byte) (v | 0x80));
            v >>>= 7;
        }
        file.writeByte((byte) v);
    }
}
