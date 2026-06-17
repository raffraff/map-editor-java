package com.rose.mapeditor.map;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.io.FileHandler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Rose Online map object file (.IFO) - decorations, NPCs, spawns, water, etc.
 */
public final class Ifo {

    private static final float WORLD_OFFSET = 520000.0f;
    private static final float WORLD_SCALE = 100.0f;

    public static final int BLOCK_COUNT = 13;

    public enum ObjectType {
        NULL(0), MORPH(1), ITEM(2), COLLISION(3), GROUND(4), CONSTRUCTION(5),
        NPC(6), MONSTER(7), AVATAR(8), USER(9), CART(10), CASTLE_GEAR(11), EVENT_OBJECT(12);

        public final int id;

        ObjectType(int id) {
            this.id = id;
        }

        public static ObjectType fromId(int id) {
            for (ObjectType t : values()) {
                if (t.id == id) {
                    return t;
                }
            }
            return NULL;
        }
    }

    public enum BlockType {
        MAP_INFO(0), DECORATION(1), NPCS(2), CONSTRUCTION(3), SOUNDS(4), EFFECTS(5),
        ANIMATION(6), WIDE_WATER(7), MONSTERS(8), WATER(9), WARP_GATES(10), COLLISION(11),
        EVENT_TRIGGERS(12);

        public final int id;

        BlockType(int id) {
            this.id = id;
        }

        public static BlockType fromId(int id) {
            for (BlockType t : values()) {
                if (t.id == id) {
                    return t;
                }
            }
            throw new IllegalArgumentException("Unknown IFO block type: " + id);
        }
    }

    /** Common fields for most placed objects. */
    public static class BaseEntry {
        public String description = "";
        public short warpId;
        public short eventId;
        public ObjectType objectType = ObjectType.NULL;
        public int objectId;
        public Vector2 mapPosition = new Vector2();
        public Quaternion rotation = new Quaternion();
        public Vector3 position = new Vector3();
        public Vector3 scale = new Vector3(1, 1, 1);
    }

    public static final class MapInformation {
        public int width;
        public int height;
        public int mapCellX;
        public int mapCellY;
        /** matrix M11..M44 (row-major). */
        public float[] world = new float[16];
        public String mapName = "";
    }

    public static final class NpcEntry extends BaseEntry {
        public int aiPatternIndex;
        public String path = "";
    }

    public static final class SoundEntry extends BaseEntry {
        public String path = "";
        public int range;
        public int interval;
    }

    public static final class EffectEntry extends BaseEntry {
        public String path = "";
    }

    public static final class UnusedWideWater {
        public int x;
        public int y;
        public WideWaterCell[][] waterBlocks;
    }

    public static final class WideWaterCell {
        public byte use;
        public float height;
        public int waterType;
        public int waterIndex;
        public int reserved;
    }

    public static final class MonsterSpawnEntry extends BaseEntry {
        public String name = "";
        public int interval;
        public int limit;
        public int range;
        public int tacticPoints;
        public List<SpawnMonster> basic = new ArrayList<>();
        public List<SpawnMonster> tactic = new ArrayList<>();
    }

    public static final class SpawnMonster {
        public String description = "";
        public int id;
        public int count;
    }

    public static final class WaterVolume {
        public Vector3 minimum = new Vector3();
        public Vector3 maximum = new Vector3();
    }

    public static final class EventTriggerEntry extends BaseEntry {
        public String qsdTrigger = "";
        public String luaTrigger = "";
    }

    public String filePath;
    public String fileName;

    public MapInformation mapInfo = new MapInformation();
    public List<BaseEntry> decoration = new ArrayList<>();
    public List<NpcEntry> npcs = new ArrayList<>();
    public List<BaseEntry> construction = new ArrayList<>();
    public List<SoundEntry> sounds = new ArrayList<>();
    public List<EffectEntry> effects = new ArrayList<>();
    public List<BaseEntry> animation = new ArrayList<>();
    public UnusedWideWater wideWater = new UnusedWideWater();
    public List<MonsterSpawnEntry> monsters = new ArrayList<>();
    public List<WaterVolume> water = new ArrayList<>();
    public List<BaseEntry> warpGates = new ArrayList<>();
    public List<BaseEntry> collision = new ArrayList<>();
    public List<EventTriggerEntry> eventTriggers = new ArrayList<>();

    public Ifo() {
    }

    public Ifo(String filePath) throws IOException {
        load(filePath);
    }

    public void load(String filePath) throws IOException {
        this.filePath = filePath;
        this.fileName = new File(filePath).getName();

        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.READING, FileHandler.eucKr())) {
            int blockCount = fh.readInt();

            mapInfo = new MapInformation();
            decoration = new ArrayList<>();
            npcs = new ArrayList<>();
            construction = new ArrayList<>();
            sounds = new ArrayList<>();
            effects = new ArrayList<>();
            animation = new ArrayList<>();
            wideWater = new UnusedWideWater();
            monsters = new ArrayList<>();
            water = new ArrayList<>();
            warpGates = new ArrayList<>();
            collision = new ArrayList<>();
            eventTriggers = new ArrayList<>();

            for (int i = 0; i < blockCount; i++) {
                BlockType blockType = BlockType.fromId(fh.readInt());
                int offset = fh.readInt();
                long resume = fh.tell();

                fh.seek(offset);
                readBlock(fh, blockType);
                fh.seek(resume);
            }
        }
    }

    private void readBlock(FileHandler fh, BlockType blockType) throws IOException {
        switch (blockType) {
            case MAP_INFO:
                mapInfo.width = fh.readInt();
                mapInfo.height = fh.readInt();
                mapInfo.mapCellX = fh.readInt();
                mapInfo.mapCellY = fh.readInt();
                mapInfo.world = fh.readMatrix16();
                mapInfo.mapName = fh.readBString();
                break;
            case DECORATION:
                decoration = readBaseEntryList(fh, fh.readInt());
                break;
            case NPCS:
                readNpcBlock(fh);
                break;
            case CONSTRUCTION:
                construction = readBaseEntryList(fh, fh.readInt());
                break;
            case SOUNDS:
                readSoundBlock(fh);
                break;
            case EFFECTS:
                readEffectBlock(fh);
                break;
            case ANIMATION:
                animation = readBaseEntryList(fh, fh.readInt());
                break;
            case WIDE_WATER:
                readWideWaterBlock(fh);
                break;
            case MONSTERS:
                readMonsterBlock(fh);
                break;
            case WATER:
                readWaterBlock(fh);
                break;
            case WARP_GATES:
                warpGates = readBaseEntryList(fh, fh.readInt());
                break;
            case COLLISION:
                collision = readBaseEntryList(fh, fh.readInt());
                break;
            case EVENT_TRIGGERS:
                readEventTriggerBlock(fh);
                break;
            default:
                break;
        }
    }

    private static List<BaseEntry> readBaseEntryList(FileHandler fh, int count) throws IOException {
        List<BaseEntry> list = new ArrayList<>(count);
        for (int j = 0; j < count; j++) {
            list.add(readBaseEntry(fh));
        }
        return list;
    }

    private static BaseEntry readBaseEntry(FileHandler fh) throws IOException {
        BaseEntry entry = new BaseEntry();
        entry.description = fh.readBString();
        entry.warpId = fh.readShort();
        entry.eventId = fh.readShort();
        entry.objectType = ObjectType.fromId(fh.readInt());
        entry.objectId = fh.readInt();
        entry.mapPosition = fh.readVector2Ints();
        entry.rotation = fh.readQuaternion();
        readWorldPosition(fh, entry.position);
        entry.scale = fh.readVector3();
        return entry;
    }

    private static void readWorldPosition(FileHandler fh, Vector3 out) throws IOException {
        out.x = (fh.readFloat() + WORLD_OFFSET) / WORLD_SCALE;
        out.y = (fh.readFloat() + WORLD_OFFSET) / WORLD_SCALE;
        out.z = fh.readFloat() / WORLD_SCALE;
    }

    private void readNpcBlock(FileHandler fh) throws IOException {
        int count = fh.readInt();
        npcs = new ArrayList<>(count);
        for (int j = 0; j < count; j++) {
            NpcEntry entry = new NpcEntry();
            entry.description = fh.readBString();
            entry.warpId = fh.readShort();
            entry.eventId = fh.readShort();
            entry.objectType = ObjectType.fromId(fh.readInt());
            entry.objectId = fh.readInt();
            entry.mapPosition = fh.readVector2Ints();
            entry.rotation = fh.readQuaternion();
            readWorldPosition(fh, entry.position);
            entry.scale = fh.readVector3();
            entry.aiPatternIndex = fh.readInt();
            entry.path = fh.readBString();
            npcs.add(entry);
        }
    }

    private void readSoundBlock(FileHandler fh) throws IOException {
        int count = fh.readInt();
        sounds = new ArrayList<>(count);
        for (int j = 0; j < count; j++) {
            SoundEntry entry = new SoundEntry();
            entry.description = fh.readBString();
            entry.warpId = fh.readShort();
            entry.eventId = fh.readShort();
            entry.objectType = ObjectType.fromId(fh.readInt());
            entry.objectId = fh.readInt();
            entry.mapPosition = fh.readVector2Ints();
            entry.rotation = fh.readQuaternion();
            readWorldPosition(fh, entry.position);
            entry.scale = fh.readVector3();
            entry.path = fh.readBString();
            entry.range = fh.readInt();
            entry.interval = fh.readInt();
            sounds.add(entry);
        }
    }

    private void readEffectBlock(FileHandler fh) throws IOException {
        int count = fh.readInt();
        effects = new ArrayList<>(count);
        for (int j = 0; j < count; j++) {
            EffectEntry entry = new EffectEntry();
            entry.description = fh.readBString();
            entry.warpId = fh.readShort();
            entry.eventId = fh.readShort();
            entry.objectType = ObjectType.fromId(fh.readInt());
            entry.objectId = fh.readInt();
            entry.mapPosition = fh.readVector2Ints();
            entry.rotation = fh.readQuaternion();
            readWorldPosition(fh, entry.position);
            entry.scale = fh.readVector3();
            entry.path = fh.readBString();
            effects.add(entry);
        }
    }

    private void readWideWaterBlock(FileHandler fh) throws IOException {
        wideWater.x = fh.readInt();
        wideWater.y = fh.readInt();
        wideWater.waterBlocks = new WideWaterCell[wideWater.x][wideWater.y];
        for (int j = 0; j < wideWater.x; j++) {
            for (int k = 0; k < wideWater.y; k++) {
                WideWaterCell cell = new WideWaterCell();
                cell.use = fh.readByte();
                cell.height = fh.readFloat();
                cell.waterType = fh.readInt();
                cell.waterIndex = fh.readInt();
                cell.reserved = fh.readInt();
                wideWater.waterBlocks[j][k] = cell;
            }
        }
    }

    private void readMonsterBlock(FileHandler fh) throws IOException {
        int count = fh.readInt();
        monsters = new ArrayList<>(count);
        for (int j = 0; j < count; j++) {
            MonsterSpawnEntry entry = new MonsterSpawnEntry();
            entry.description = fh.readBString();
            entry.warpId = fh.readShort();
            entry.eventId = fh.readShort();
            entry.objectType = ObjectType.fromId(fh.readInt());
            entry.objectId = fh.readInt();
            entry.mapPosition = fh.readVector2Ints();
            entry.rotation = fh.readQuaternion();
            readWorldPosition(fh, entry.position);
            entry.scale = fh.readVector3();
            entry.name = fh.readBString();

            int basicCount = fh.readInt();
            entry.basic = new ArrayList<>(basicCount);
            for (int k = 0; k < basicCount; k++) {
                entry.basic.add(readSpawnMonster(fh));
            }

            int tacticCount = fh.readInt();
            entry.tactic = new ArrayList<>(tacticCount);
            for (int k = 0; k < tacticCount; k++) {
                entry.tactic.add(readSpawnMonster(fh));
            }

            entry.interval = fh.readInt();
            entry.limit = fh.readInt();
            entry.range = fh.readInt();
            entry.tacticPoints = fh.readInt();
            monsters.add(entry);
        }
    }

    private static SpawnMonster readSpawnMonster(FileHandler fh) throws IOException {
        SpawnMonster m = new SpawnMonster();
        m.description = fh.readBString();
        m.id = fh.readInt();
        m.count = fh.readInt();
        return m;
    }

    private void readWaterBlock(FileHandler fh) throws IOException {
        fh.readFloat(); // unused header float (2000.0 on save)
        int count = fh.readInt();
        water = new ArrayList<>(count);
        for (int j = 0; j < count; j++) {
            WaterVolume vol = new WaterVolume();
            vol.minimum.x = (fh.readFloat() + WORLD_OFFSET) / WORLD_SCALE;
            vol.minimum.z = fh.readFloat() / WORLD_SCALE;
            vol.minimum.y = (fh.readFloat() + WORLD_OFFSET) / WORLD_SCALE;
            vol.maximum.x = (fh.readFloat() + WORLD_OFFSET) / WORLD_SCALE;
            vol.maximum.z = fh.readFloat() / WORLD_SCALE;
            vol.maximum.y = (fh.readFloat() + WORLD_OFFSET) / WORLD_SCALE;
            water.add(vol);
        }
    }

    private void readEventTriggerBlock(FileHandler fh) throws IOException {
        int count = fh.readInt();
        eventTriggers = new ArrayList<>(count);
        for (int j = 0; j < count; j++) {
            EventTriggerEntry entry = new EventTriggerEntry();
            entry.description = fh.readBString();
            entry.warpId = fh.readShort();
            entry.eventId = fh.readShort();
            entry.objectType = ObjectType.fromId(fh.readInt());
            entry.objectId = fh.readInt();
            entry.mapPosition = fh.readVector2Ints();
            entry.rotation = fh.readQuaternion();
            readWorldPosition(fh, entry.position);
            entry.scale = fh.readVector3();
            entry.qsdTrigger = fh.readBString();
            entry.luaTrigger = fh.readBString();
            eventTriggers.add(entry);
        }
    }

    /** Total placed objects across all categories (excluding map info / wide water). */
    public int totalObjectCount() {
        return decoration.size() + npcs.size() + construction.size() + sounds.size()
            + effects.size() + animation.size() + monsters.size() + water.size()
            + warpGates.size() + collision.size() + eventTriggers.size();
    }

    public String summaryLine() {
        return String.format(
            "%s: deco=%d build=%d npc=%d monster=%d water=%d warp=%d fx=%d snd=%d",
            fileName, decoration.size(), construction.size(), npcs.size(), monsters.size(),
            water.size(), warpGates.size(), effects.size(), sounds.size()
        );
    }

    /** Creates an empty sector IFO with default wide-water grid. */
    public static Ifo createEmptySector(int mapCellX, int mapCellY, String sectorName) {
        Ifo ifo = new Ifo();
        ifo.mapInfo.width = 16;
        ifo.mapInfo.height = 16;
        ifo.mapInfo.mapCellX = mapCellX;
        ifo.mapInfo.mapCellY = mapCellY;
        ifo.mapInfo.mapName = sectorName;
        ifo.mapInfo.world[0] = 1f;
        ifo.mapInfo.world[5] = 1f;
        ifo.mapInfo.world[10] = 1f;
        ifo.mapInfo.world[15] = 1f;

        ifo.wideWater.x = 16;
        ifo.wideWater.y = 16;
        ifo.wideWater.waterBlocks = new WideWaterCell[16][16];
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                WideWaterCell cell = new WideWaterCell();
                cell.waterType = 1;
                ifo.wideWater.waterBlocks[x][y] = cell;
            }
        }
        return ifo;
    }

    public void save(String filePath) throws IOException {
        this.filePath = filePath;
        this.fileName = new File(filePath).getName();

        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.WRITING, FileHandler.eucKr())) {
            fh.writeInt(BLOCK_COUNT);
            fh.writeBytes(new byte[BLOCK_COUNT * 8]);

            int[] offsets = new int[BLOCK_COUNT];

            offsets[0] = (int) fh.tell();
            fh.writeInt(mapInfo.width);
            fh.writeInt(mapInfo.height);
            fh.writeInt(mapInfo.mapCellX);
            fh.writeInt(mapInfo.mapCellY);
            fh.writeMatrix16(mapInfo.world);
            fh.writeBString(mapInfo.mapName != null ? mapInfo.mapName : "");

            offsets[1] = (int) fh.tell();
            writeBaseEntryList(fh, decoration);

            offsets[2] = (int) fh.tell();
            fh.writeInt(npcs.size());
            for (NpcEntry entry : npcs) {
                writeBaseEntry(fh, entry);
                fh.writeInt(entry.aiPatternIndex);
                fh.writeBString(entry.path != null ? entry.path : "");
            }

            offsets[3] = (int) fh.tell();
            writeBaseEntryList(fh, construction);

            offsets[4] = (int) fh.tell();
            fh.writeInt(sounds.size());
            for (SoundEntry entry : sounds) {
                writeBaseEntry(fh, entry);
                fh.writeBString(entry.path != null ? entry.path : "");
                fh.writeInt(entry.range);
                fh.writeInt(entry.interval);
            }

            offsets[5] = (int) fh.tell();
            fh.writeInt(effects.size());
            for (EffectEntry entry : effects) {
                writeBaseEntry(fh, entry);
                fh.writeBString(entry.path != null ? entry.path : "");
            }

            offsets[6] = (int) fh.tell();
            writeBaseEntryList(fh, animation);

            offsets[7] = (int) fh.tell();
            fh.writeInt(wideWater.x);
            fh.writeInt(wideWater.y);
            for (int x = 0; x < wideWater.x; x++) {
                for (int y = 0; y < wideWater.y; y++) {
                    WideWaterCell cell = wideWater.waterBlocks[x][y];
                    fh.writeByte(cell.use);
                    fh.writeFloat(cell.height);
                    fh.writeInt(cell.waterType);
                    fh.writeInt(cell.waterIndex);
                    fh.writeInt(cell.reserved);
                }
            }

            offsets[8] = (int) fh.tell();
            fh.writeInt(monsters.size());
            for (MonsterSpawnEntry entry : monsters) {
                writeBaseEntry(fh, entry);
                fh.writeBString(entry.name != null ? entry.name : "");
                fh.writeInt(entry.basic.size());
                for (SpawnMonster monster : entry.basic) {
                    fh.writeBString(monster.description != null ? monster.description : "");
                    fh.writeInt(monster.id);
                    fh.writeInt(monster.count);
                }
                fh.writeInt(entry.tactic.size());
                for (SpawnMonster monster : entry.tactic) {
                    fh.writeBString(monster.description != null ? monster.description : "");
                    fh.writeInt(monster.id);
                    fh.writeInt(monster.count);
                }
                fh.writeInt(entry.interval);
                fh.writeInt(entry.limit);
                fh.writeInt(entry.range);
                fh.writeInt(entry.tacticPoints);
            }

            offsets[9] = (int) fh.tell();
            fh.writeFloat(2000.0f);
            fh.writeInt(water.size());
            for (WaterVolume vol : water) {
                fh.writeFloat(-WORLD_OFFSET + vol.minimum.x * WORLD_SCALE);
                fh.writeFloat(vol.minimum.z * WORLD_SCALE);
                fh.writeFloat(-WORLD_OFFSET + vol.minimum.y * WORLD_SCALE);
                fh.writeFloat(-WORLD_OFFSET + vol.maximum.x * WORLD_SCALE);
                fh.writeFloat(vol.maximum.z * WORLD_SCALE);
                fh.writeFloat(-WORLD_OFFSET + vol.maximum.y * WORLD_SCALE);
            }

            offsets[10] = (int) fh.tell();
            writeBaseEntryList(fh, warpGates);

            offsets[11] = (int) fh.tell();
            writeBaseEntryList(fh, collision);

            offsets[12] = (int) fh.tell();
            fh.writeInt(eventTriggers.size());
            for (EventTriggerEntry entry : eventTriggers) {
                writeBaseEntry(fh, entry);
                fh.writeBString(entry.qsdTrigger != null ? entry.qsdTrigger : "");
                fh.writeBString(entry.luaTrigger != null ? entry.luaTrigger : "");
            }

            fh.seek(4);
            for (int i = 0; i < BLOCK_COUNT; i++) {
                fh.writeInt(i);
                fh.writeInt(offsets[i]);
            }
        }
    }

    public void save() throws IOException {
        save(filePath);
    }

    private static void writeBaseEntryList(FileHandler fh, List<BaseEntry> entries) throws IOException {
        fh.writeInt(entries.size());
        for (BaseEntry entry : entries) {
            writeBaseEntry(fh, entry);
        }
    }

    private static void writeBaseEntry(FileHandler fh, BaseEntry entry) throws IOException {
        fh.writeBString(entry.description != null ? entry.description : "");
        fh.writeShort(entry.warpId);
        fh.writeShort(entry.eventId);
        fh.writeInt(entry.objectType.id);
        fh.writeInt(entry.objectId);
        fh.writeVector2Ints(entry.mapPosition);
        fh.writeQuaternion(entry.rotation);
        fh.writeFloat(-WORLD_OFFSET + entry.position.x * WORLD_SCALE);
        fh.writeFloat(-WORLD_OFFSET + entry.position.y * WORLD_SCALE);
        fh.writeFloat(entry.position.z * WORLD_SCALE);
        fh.writeVector3(entry.scale);
    }
}
