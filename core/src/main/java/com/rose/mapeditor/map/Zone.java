package com.rose.mapeditor.map;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.io.FileHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Rose Online zone definition (.ZON). */
public final class Zone {

    public enum BlockType {
        BLOCK0,
        SPAWN_POINTS,
        TEXTURES,
        TILES,
        ECONOMY
    }

    public enum RotationType {
        NORMAL(1),
        LEFT_RIGHT(2),
        TOP_BOTTOM(3),
        LEFT_RIGHT_TOP_BOTTOM(4),
        ROTATE_90_CLOCKWISE(5),
        ROTATE_90_COUNTER_CLOCKWISE(6);

        public final int id;

        RotationType(int id) {
            this.id = id;
        }

        public static RotationType fromId(int id) {
            for (RotationType r : values()) {
                if (r.id == id) {
                    return r;
                }
            }
            return NORMAL;
        }
    }

    public static final class Block {
        public BlockType type;
        public int offset;
    }

    public static final class ZonePart {
        public byte useMap;
        public Vector2 position = new Vector2();
    }

    public static final class Block0 {
        public int zoneType;
        public int zoneWidth;
        public int zoneHeight;
        public int gridCount;
        public float gridSize;
        public int xCount;
        public int yCount;
        public ZonePart[][] zoneParts;
    }

    public static final class SpawnPoint {
        public Vector3 position = new Vector3();
        public String name;
    }

    public static final class TileTexture {
        public String path;
        public com.badlogic.gdx.graphics.Texture loadedTexture;
    }

    public static final class Tile {
        public int baseId1;
        public int baseId2;
        public int offset1;
        public int offset2;
        public boolean blending;
        public RotationType rotation = RotationType.NORMAL;
        public int tileType;

        public int id1() {
            return baseId1 + offset1;
        }

        public int id2() {
            return baseId2 + offset2;
        }
    }

    public static final class Economy {
        public String areaName = "0";
        public int isUnderground;
        public String buttonBgm = "button1";
        public String buttonBack = "button2";
        public int checkCount = 35;
        public int standardPopulation = 500;
        public int standardGrowthRate = 30;
        public int metalConsumption = 10;
        public int stoneConsumption = 20;
        public int woodConsumption = 20;
        public int leatherConsumption = 10;
        public int clothConsumption = 10;
        public int alchemyConsumption = 10;
        public int chemicalConsumption = 10;
        public int industrialConsumption = 10;
        public int medicineConsumption = 30;
        public int foodConsumption = 10;
    }

    public String filePath;
    public List<Block> blocks = new ArrayList<>();
    public Block0 zoneInfo;
    public List<SpawnPoint> spawnPoints = new ArrayList<>();
    public List<TileTexture> textures = new ArrayList<>();
    public List<Tile> tiles = new ArrayList<>();
    public Economy economyInfo = new Economy();

    public Zone() {
    }

    public Zone(String filePath) throws IOException {
        load(filePath);
    }

    public void load(String filePath) throws IOException {
        this.filePath = filePath;
        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.READING, FileHandler.eucKr())) {
            int blockCount = fh.readInt();
            blocks = new ArrayList<>(blockCount);
            for (int i = 0; i < blockCount; i++) {
                Block block = new Block();
                block.type = BlockType.values()[fh.readInt()];
                block.offset = fh.readInt();
                blocks.add(block);
            }

            for (Block block : blocks) {
                fh.seek(block.offset);
                switch (block.type) {
                    case BLOCK0:
                        zoneInfo = new Block0();
                        zoneInfo.zoneType = fh.readInt();
                        zoneInfo.zoneWidth = fh.readInt();
                        zoneInfo.zoneHeight = fh.readInt();
                        zoneInfo.gridCount = fh.readInt();
                        zoneInfo.gridSize = fh.readFloat();
                        zoneInfo.xCount = fh.readInt();
                        zoneInfo.yCount = fh.readInt();
                        zoneInfo.zoneParts = new ZonePart[zoneInfo.zoneWidth][zoneInfo.zoneHeight];
                        for (int j = 0; j < zoneInfo.zoneWidth; j++) {
                            for (int k = 0; k < zoneInfo.zoneHeight; k++) {
                                ZonePart part = new ZonePart();
                                part.useMap = fh.readByte();
                                part.position = new Vector2(fh.readFloat(), fh.readFloat());
                                zoneInfo.zoneParts[j][k] = part;
                            }
                        }
                        break;
                    case SPAWN_POINTS:
                        int spawnCount = fh.readInt();
                        spawnPoints = new ArrayList<>(spawnCount);
                        for (int j = 0; j < spawnCount; j++) {
                            SpawnPoint sp = new SpawnPoint();
                            sp.position.x = (fh.readFloat() + 520000.0f) / 100.0f;
                            sp.position.z = fh.readFloat() / 100.0f;
                            sp.position.y = (fh.readFloat() + 520000.0f) / 100.0f;
                            sp.name = fh.readBString();
                            spawnPoints.add(sp);
                        }
                        break;
                    case TEXTURES:
                        int textureCount = fh.readInt();
                        textures = new ArrayList<>(textureCount);
                        for (int j = 0; j < textureCount; j++) {
                            TileTexture tex = new TileTexture();
                            tex.path = fh.readBString();
                            textures.add(tex);
                        }
                        break;
                    case TILES:
                        int tileCount = fh.readInt();
                        tiles = new ArrayList<>(tileCount);
                        for (int j = 0; j < tileCount; j++) {
                            Tile tile = new Tile();
                            tile.baseId1 = fh.readInt();
                            tile.baseId2 = fh.readInt();
                            tile.offset1 = fh.readInt();
                            tile.offset2 = fh.readInt();
                            tile.blending = fh.readInt() > 0;
                            tile.rotation = RotationType.fromId(fh.readInt());
                            tile.tileType = fh.readInt();
                            tiles.add(tile);
                        }
                        break;
                    case ECONOMY:
                        // Optional block; skip remaining bytes if present
                        break;
                    default:
                        break;
                }
            }
        }
    }

    public void save(String filePath) throws IOException {
        this.filePath = filePath;
        if (blocks.isEmpty()) {
            blocks = new ArrayList<>(5);
            for (BlockType type : BlockType.values()) {
                Block block = new Block();
                block.type = type;
                blocks.add(block);
            }
        }

        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.WRITING, FileHandler.eucKr())) {
            fh.writeInt(blocks.size());
            for (int i = 0; i < blocks.size(); i++) {
                fh.writeInt(0);
                fh.writeInt(0);
            }

            for (Block block : blocks) {
                block.offset = (int) fh.tell();
                switch (block.type) {
                    case BLOCK0:
                        fh.writeInt(zoneInfo.zoneType);
                        fh.writeInt(zoneInfo.zoneWidth);
                        fh.writeInt(zoneInfo.zoneHeight);
                        fh.writeInt(zoneInfo.gridCount);
                        fh.writeFloat(zoneInfo.gridSize);
                        fh.writeInt(zoneInfo.xCount);
                        fh.writeInt(zoneInfo.yCount);
                        for (int x = 0; x < zoneInfo.zoneWidth; x++) {
                            for (int y = 0; y < zoneInfo.zoneHeight; y++) {
                                ZonePart part = zoneInfo.zoneParts[x][y];
                                fh.writeByte(part.useMap);
                                fh.writeVector2(part.position);
                            }
                        }
                        break;
                    case SPAWN_POINTS:
                        fh.writeInt(spawnPoints.size());
                        for (SpawnPoint sp : spawnPoints) {
                            fh.writeFloat(-520000.0f + sp.position.x * 100.0f);
                            fh.writeFloat(sp.position.z * 100.0f);
                            fh.writeFloat(-520000.0f + sp.position.y * 100.0f);
                            fh.writeBString(sp.name != null ? sp.name : "");
                        }
                        break;
                    case TEXTURES:
                        fh.writeInt(textures.size());
                        for (TileTexture tex : textures) {
                            fh.writeBString(tex.path != null ? tex.path : "");
                        }
                        break;
                    case TILES:
                        fh.writeInt(tiles.size());
                        for (Tile tile : tiles) {
                            fh.writeInt(tile.baseId1);
                            fh.writeInt(tile.baseId2);
                            fh.writeInt(tile.offset1);
                            fh.writeInt(tile.offset2);
                            fh.writeInt(tile.blending ? 1 : 0);
                            fh.writeInt(tile.rotation.id);
                            fh.writeInt(tile.tileType);
                        }
                        break;
                    case ECONOMY:
                        fh.writeBString(economyInfo.areaName != null ? economyInfo.areaName : "0");
                        fh.writeInt(economyInfo.isUnderground);
                        fh.writeBString(economyInfo.buttonBgm != null ? economyInfo.buttonBgm : "button1");
                        fh.writeBString(economyInfo.buttonBack != null ? economyInfo.buttonBack : "button2");
                        fh.writeInt(economyInfo.checkCount);
                        fh.writeInt(economyInfo.standardPopulation);
                        fh.writeInt(economyInfo.standardGrowthRate);
                        fh.writeInt(economyInfo.metalConsumption);
                        fh.writeInt(economyInfo.stoneConsumption);
                        fh.writeInt(economyInfo.woodConsumption);
                        fh.writeInt(economyInfo.leatherConsumption);
                        fh.writeInt(economyInfo.clothConsumption);
                        fh.writeInt(economyInfo.alchemyConsumption);
                        fh.writeInt(economyInfo.chemicalConsumption);
                        fh.writeInt(economyInfo.industrialConsumption);
                        fh.writeInt(economyInfo.medicineConsumption);
                        fh.writeInt(economyInfo.foodConsumption);
                        break;
                    default:
                        break;
                }
            }

            fh.seek(4);
            for (Block block : blocks) {
                fh.writeInt(block.type.ordinal());
                fh.writeInt(block.offset);
            }
        }
    }

    public void save() throws IOException {
        save(filePath);
    }
}
