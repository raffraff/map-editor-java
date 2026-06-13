package com.rose.mapeditor.map;

import com.rose.mapeditor.io.FileHandler;

import java.io.IOException;

/** Rose Online tile layer (.TIL). */
public final class Til {

    public static final class Tile {
        public byte brushId;
        public byte tileIndex;
        public byte tileSetNumber;
        public int tileId;
    }

    public Tile[][] tiles;
    public String filePath;

    public Til() {
    }

    public Til(String filePath) throws IOException {
        load(filePath);
    }

    public void load(String filePath) throws IOException {
        this.filePath = filePath;
        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.READING, null)) {
            int width = fh.readInt();
            int height = fh.readInt();
            tiles = new Tile[height][width];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    Tile tile = new Tile();
                    tile.brushId = fh.readByte();
                    tile.tileIndex = fh.readByte();
                    tile.tileSetNumber = fh.readByte();
                    tile.tileId = fh.readInt();
                    tiles[y][x] = tile;
                }
            }
        }
    }

    public void save(String filePath) throws IOException {
        this.filePath = filePath;
        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.WRITING, null)) {
            fh.writeInt(16);
            fh.writeInt(16);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    Tile tile = tiles != null ? tiles[y][x] : null;
                    fh.writeByte(tile != null ? tile.brushId : (byte) 0);
                    fh.writeByte(tile != null ? tile.tileIndex : (byte) 0);
                    fh.writeByte(tile != null ? tile.tileSetNumber : (byte) 0);
                    fh.writeInt(tile != null ? tile.tileId : 0);
                }
            }
        }
    }

    public void save() throws IOException {
        save(filePath);
    }
}
