package com.rose.mapeditor.map;

import com.rose.mapeditor.io.FileHandler;

import java.io.IOException;

/** Rose Online walkability grid (.MOV). */
public final class Mov {

    public byte[][] isWalkable;
    public String filePath;

    public Mov() {
    }

    public Mov(String filePath) throws IOException {
        load(filePath);
    }

    public void load(String filePath) throws IOException {
        this.filePath = filePath;
        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.READING, FileHandler.eucKr())) {
            int height = fh.readInt();
            int width = fh.readInt();
            isWalkable = new byte[height][width];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    isWalkable[y][x] = fh.readByte();
                }
            }
        }
    }

    public void save(String filePath) throws IOException {
        this.filePath = filePath;
        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.WRITING, FileHandler.eucKr())) {
            fh.writeInt(32);
            fh.writeInt(32);
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    byte value = 0;
                    if (isWalkable != null && y < isWalkable.length && x < isWalkable[y].length) {
                        value = isWalkable[y][x];
                    }
                    fh.writeByte(value);
                }
            }
        }
    }

    public void save() throws IOException {
        save(filePath);
    }
}
