package com.rose.mapeditor.model;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.io.FileHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Rose Online scene container (.ZSC) - models, textures, object definitions. */
public final class Zsc {

    public enum FlagType {
        END(0),
        POSITION(1),
        ROTATION(2),
        SCALE(3),
        AXIS_ROTATION(4),
        BONE_INDEX(5),
        DUMMY_INDEX(6),
        PARENT(7),
        COLLISION(29),
        MOTION(30),
        RANGE_SET(31),
        LIGHTMAP(32);

        public final int id;

        FlagType(int id) {
            this.id = id;
        }

        static FlagType fromId(int id) {
            for (FlagType f : values()) {
                if (f.id == id) {
                    return f;
                }
            }
            return null;
        }
    }

    public static final class TextureEntry {
        public String path;
        public boolean skin;
        public boolean alphaEnabled;
        public boolean twoSided;
        public boolean alphaTestEnabled;
        public short alphaReference;
        public boolean zWriteEnabled;
        public boolean zTestEnabled;
        public short blendingMode;
        public boolean specularEnabled;
        public float alpha;
        public short glowType;
        public Vector3 glow = new Vector3();
    }

    public static final class PartModel {
        public short modelId;
        public short textureId;
        public Vector3 position = new Vector3();
        public Quaternion rotation = new Quaternion();
        public Vector3 scale = new Vector3(1, 1, 1);
        public String motion;
        public boolean useLightmap;
    }

    public static final class PartEffect {
        public short effectId;
        public short effectType;
        public Vector3 position = new Vector3();
        public Quaternion rotation = new Quaternion();
        public Vector3 scale = new Vector3(1, 1, 1);
        public short parent = -1;
    }

    public static final class SceneObject {
        public int cylinderRadius;
        public int cylinderX;
        public int cylinderY;
        public List<PartModel> models = new ArrayList<>();
        public List<PartEffect> effects = new ArrayList<>();
        public Vector3 boundsMin = new Vector3();
        public Vector3 boundsMax = new Vector3();
    }

    public String filePath;
    public List<String> models = new ArrayList<>();
    public List<TextureEntry> textures = new ArrayList<>();
    public List<String> effects = new ArrayList<>();
    public List<SceneObject> objects = new ArrayList<>();

    public Zsc() {
    }

    public Zsc(String filePath) throws IOException {
        load(filePath);
    }

    public void load(String filePath) throws IOException {
        this.filePath = filePath;
        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.READING, FileHandler.eucKr())) {
            short modelCount = fh.readShort();
            models = new ArrayList<>(modelCount);
            for (int i = 0; i < modelCount; i++) {
                models.add(fh.readZString());
            }

            short textureCount = fh.readShort();
            textures = new ArrayList<>(textureCount);
            for (int i = 0; i < textureCount; i++) {
                TextureEntry tex = new TextureEntry();
                tex.path = fh.readZString();
                tex.skin = fh.readShort() > 0;
                tex.alphaEnabled = fh.readShort() > 0;
                tex.twoSided = fh.readShort() > 0;
                tex.alphaTestEnabled = fh.readShort() > 0;
                tex.alphaReference = fh.readShort();
                tex.zWriteEnabled = fh.readShort() > 0;
                tex.zTestEnabled = fh.readShort() > 0;
                tex.blendingMode = fh.readShort();
                tex.specularEnabled = fh.readShort() > 0;
                tex.alpha = fh.readFloat();
                tex.glowType = fh.readShort();
                tex.glow = fh.readVector3();
                textures.add(tex);
            }

            short effectCount = fh.readShort();
            effects = new ArrayList<>(effectCount);
            for (int i = 0; i < effectCount; i++) {
                effects.add(fh.readZString());
            }

            short objectCount = fh.readShort();
            objects = new ArrayList<>(objectCount);
            for (int i = 0; i < objectCount; i++) {
                SceneObject obj = new SceneObject();
                obj.cylinderRadius = fh.readInt() / 100;
                obj.cylinderX = fh.readInt() / 100;
                obj.cylinderY = fh.readInt() / 100;
                objects.add(obj);

                short modelPartCount = fh.readShort();
                if (modelPartCount == 0) {
                    continue;
                }

                for (int j = 0; j < modelPartCount; j++) {
                        PartModel part = new PartModel();
                        part.modelId = fh.readShort();
                        part.textureId = fh.readShort();
                        readModelFlags(fh, part);
                        obj.models.add(part);
                    }

                short effectPartCount = fh.readShort();
                for (int j = 0; j < effectPartCount; j++) {
                    PartEffect effect = new PartEffect();
                    effect.effectId = fh.readShort();
                    effect.effectType = fh.readShort();
                    readEffectFlags(fh, effect);
                    obj.effects.add(effect);
                }

                obj.boundsMin = fh.readVector3().scl(1f / 100f);
                obj.boundsMax = fh.readVector3().scl(1f / 100f);
            }
        }
    }

    private static void readModelFlags(FileHandler fh, PartModel part) throws IOException {
        while (true) {
            FlagType command = FlagType.fromId(fh.readByte() & 0xFF);
            if (command == null || command == FlagType.END) {
                break;
            }
            int flagSize = fh.readByte() & 0xFF;
            switch (command) {
                case POSITION:
                    part.position = fh.readVector3().scl(1f / 100f);
                    break;
                case ROTATION: {
                    float w = fh.readFloat();
                    float x = fh.readFloat();
                    float y = fh.readFloat();
                    float z = fh.readFloat();
                    part.rotation.set(x, y, z, w);
                    break;
                }
                case SCALE:
                    part.scale = fh.readVector3();
                    break;
                case AXIS_ROTATION:
                    fh.readQuaternion();
                    break;
                case BONE_INDEX:
                case DUMMY_INDEX:
                case RANGE_SET:
                    fh.readShort();
                    break;
                case PARENT:
                    fh.readShort();
                    break;
                case COLLISION:
                    fh.readShort();
                    break;
                case MOTION:
                    part.motion = fh.readNString(flagSize);
                    break;
                case LIGHTMAP:
                    part.useLightmap = fh.readShort() > 0;
                    break;
                default:
                    fh.seek(fh.tell() + flagSize);
                    break;
            }
        }
    }

    private static void readEffectFlags(FileHandler fh, PartEffect effect) throws IOException {
        while (true) {
            FlagType command = FlagType.fromId(fh.readByte() & 0xFF);
            if (command == null || command == FlagType.END) {
                break;
            }
            int flagSize = fh.readByte() & 0xFF;
            switch (command) {
                case POSITION:
                    effect.position = fh.readVector3().scl(1f / 100f);
                    break;
                case ROTATION:
                    effect.rotation = fh.readQuaternion();
                    break;
                case SCALE:
                    effect.scale = fh.readVector3();
                    break;
                case PARENT:
                    effect.parent = (short) (fh.readShort() - 1);
                    break;
                default:
                    fh.seek(fh.tell() + flagSize);
                    break;
            }
        }
    }
}
