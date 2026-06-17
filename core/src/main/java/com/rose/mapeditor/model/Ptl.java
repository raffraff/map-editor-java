package com.rose.mapeditor.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.io.FileHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Rose Online particle system (.PTL) with keyframe sequences. */
public final class Ptl {

    /** Rose client scale factor for spatial PTL values. */
    public static final float SCALE = 0.01f;

    public static final class FloatRange {
        public float min;
        public float max;

        public float pick(java.util.Random random) {
            if (max <= min) {
                return min;
            }
            return min + random.nextFloat() * (max - min);
        }
    }

    public static final class Vector2Range {
        public final Vector2 min = new Vector2();
        public final Vector2 max = new Vector2();

        public Vector2 pick(java.util.Random random, Vector2 out) {
            out.x = min.x + random.nextFloat() * (max.x - min.x);
            out.y = min.y + random.nextFloat() * (max.y - min.y);
            return out;
        }
    }

    public static final class Vector3Range {
        public final Vector3 min = new Vector3();
        public final Vector3 max = new Vector3();

        public Vector3 pick(java.util.Random random, Vector3 out) {
            out.x = min.x + random.nextFloat() * (max.x - min.x);
            out.y = min.y + random.nextFloat() * (max.y - min.y);
            out.z = min.z + random.nextFloat() * (max.z - min.z);
            return out;
        }
    }

    public static final class ColorRange {
        public final Color min = new Color();
        public final Color max = new Color();

        public Color pick(java.util.Random random, Color out) {
            out.r = min.r + random.nextFloat() * (max.r - min.r);
            out.g = min.g + random.nextFloat() * (max.g - min.g);
            out.b = min.b + random.nextFloat() * (max.b - min.b);
            out.a = min.a + random.nextFloat() * (max.a - min.a);
            return out;
        }
    }

    public enum KeyframeType {
        SIZE(1),
        TIMER(2),
        RED(3),
        GREEN(4),
        BLUE(5),
        ALPHA(6),
        COLOR(7),
        VELOCITY_X(8),
        VELOCITY_Y(9),
        VELOCITY_Z(10),
        VELOCITY(11),
        TEXTURE(12),
        ROTATION(13);

        public final int id;

        KeyframeType(int id) {
            this.id = id;
        }

        static KeyframeType fromId(int id) throws IOException {
            for (KeyframeType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            throw new IOException("Invalid PTL keyframe type " + id);
        }
    }

    public enum AlignType {
        BILLBOARD,
        WORLD_MESH,
        AXIS_ALIGNED
    }

    public enum CoordType {
        /** Positions simulated in world space; parent rotation does not affect velocity. */
        WORLD,
        /** Parent translation only at render (PTL update_coords = 1). */
        LOCAL_POSITION,
        /** Fully inherit parent transform. */
        LOCAL
    }

    public static final class Keyframe {
        public KeyframeType type;
        public final FloatRange startTime = new FloatRange();
        public boolean fade;
        public final Vector2Range size = new Vector2Range();
        public final FloatRange timer = new FloatRange();
        public final FloatRange red = new FloatRange();
        public final FloatRange green = new FloatRange();
        public final FloatRange blue = new FloatRange();
        public final FloatRange alpha = new FloatRange();
        public final ColorRange color = new ColorRange();
        public final FloatRange velocityX = new FloatRange();
        public final FloatRange velocityY = new FloatRange();
        public final FloatRange velocityZ = new FloatRange();
        public final Vector3Range velocity = new Vector3Range();
        public final FloatRange textureIndex = new FloatRange();
        public final FloatRange rotation = new FloatRange();
    }

    public static final class Sequence {
        public String name = "";
        public final FloatRange lifeTime = new FloatRange();
        public final FloatRange emitRate = new FloatRange();
        public int loopCount;
        public final Vector3Range spawnDirection = new Vector3Range();
        public final Vector3Range emitRadius = new Vector3Range();
        public final Vector3Range gravity = new Vector3Range();
        public String texturePath = "";
        public int particleCount;
        public AlignType alignType = AlignType.BILLBOARD;
        public CoordType coordType = CoordType.WORLD;
        public int spriteCols = 1;
        public int spriteRows = 1;
        public int blendSrc;
        public int blendDst;
        public int blendOp;
        public final List<Keyframe> keyframes = new ArrayList<>();
    }

    public final List<Sequence> sequences = new ArrayList<>();

    public Ptl() {
    }

    public Ptl(String filePath) throws IOException {
        load(filePath);
    }

    public void load(String filePath) throws IOException {
        sequences.clear();
        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.READING, null)) {
            int count = fh.readInt();
            for (int i = 0; i < count; i++) {
                sequences.add(readSequence(fh));
            }
        }
    }

    private static Sequence readSequence(FileHandler fh) throws IOException {
        Sequence sequence = new Sequence();
        sequence.name = fh.readU32String();
        sequence.lifeTime.min = fh.readFloat();
        sequence.lifeTime.max = fh.readFloat();
        sequence.emitRate.min = fh.readFloat();
        sequence.emitRate.max = fh.readFloat();
        sequence.loopCount = fh.readInt();
        readScaledVector3Range(fh, sequence.spawnDirection);
        readScaledVector3Range(fh, sequence.emitRadius);
        readScaledVector3Range(fh, sequence.gravity);
        sequence.texturePath = fh.readU32String();
        sequence.particleCount = fh.readInt();
        sequence.alignType = alignTypeFromId(fh.readInt());
        sequence.coordType = coordTypeFromId(fh.readInt());
        sequence.spriteCols = Math.max(1, fh.readInt());
        sequence.spriteRows = Math.max(1, fh.readInt());
        fh.readInt();
        sequence.blendDst = fh.readInt();
        sequence.blendSrc = fh.readInt();
        sequence.blendOp = fh.readInt();

        int keyframeCount = fh.readInt();
        for (int j = 0; j < keyframeCount; j++) {
            sequence.keyframes.add(readKeyframe(fh));
        }
        return sequence;
    }

    private static void readScaledVector3Range(FileHandler fh, Vector3Range range) throws IOException {
        range.min.set(fh.readVector3()).scl(SCALE);
        range.max.set(fh.readVector3()).scl(SCALE);
    }

    private static Keyframe readKeyframe(FileHandler fh) throws IOException {
        Keyframe keyframe = new Keyframe();
        keyframe.type = KeyframeType.fromId(fh.readInt());
        keyframe.startTime.min = fh.readFloat();
        keyframe.startTime.max = fh.readFloat();
        keyframe.fade = fh.readByte() != 0;

        switch (keyframe.type) {
            case SIZE:
                readScaledVector2Range(fh, keyframe.size);
                break;
            case TIMER:
                keyframe.timer.min = fh.readFloat();
                keyframe.timer.max = fh.readFloat();
                break;
            case RED:
                keyframe.red.min = fh.readFloat();
                keyframe.red.max = fh.readFloat();
                break;
            case GREEN:
                keyframe.green.min = fh.readFloat();
                keyframe.green.max = fh.readFloat();
                break;
            case BLUE:
                keyframe.blue.min = fh.readFloat();
                keyframe.blue.max = fh.readFloat();
                break;
            case ALPHA:
                keyframe.alpha.min = fh.readFloat();
                keyframe.alpha.max = fh.readFloat();
                break;
            case COLOR:
                readColorRange(fh, keyframe.color);
                break;
            case VELOCITY_X:
                keyframe.velocityX.min = fh.readFloat() * SCALE;
                keyframe.velocityX.max = fh.readFloat() * SCALE;
                break;
            case VELOCITY_Y:
                keyframe.velocityY.min = fh.readFloat() * SCALE;
                keyframe.velocityY.max = fh.readFloat() * SCALE;
                break;
            case VELOCITY_Z:
                keyframe.velocityZ.min = fh.readFloat() * SCALE;
                keyframe.velocityZ.max = fh.readFloat() * SCALE;
                break;
            case VELOCITY:
                keyframe.velocity.min.set(fh.readVector3()).scl(SCALE);
                keyframe.velocity.max.set(fh.readVector3()).scl(SCALE);
                break;
            case TEXTURE:
                keyframe.textureIndex.min = fh.readFloat();
                keyframe.textureIndex.max = fh.readFloat();
                break;
            case ROTATION:
                keyframe.rotation.min = fh.readFloat();
                keyframe.rotation.max = fh.readFloat();
                break;
            default:
                throw new IOException("Unhandled keyframe type " + keyframe.type);
        }
        return keyframe;
    }

    private static void readScaledVector2Range(FileHandler fh, Vector2Range range) throws IOException {
        range.min.set(fh.readFloat() * SCALE, fh.readFloat() * SCALE);
        range.max.set(fh.readFloat() * SCALE, fh.readFloat() * SCALE);
    }

    private static void readColorRange(FileHandler fh, ColorRange range) throws IOException {
        range.min.r = fh.readFloat();
        range.min.g = fh.readFloat();
        range.min.b = fh.readFloat();
        range.min.a = fh.readFloat();
        range.max.r = fh.readFloat();
        range.max.g = fh.readFloat();
        range.max.b = fh.readFloat();
        range.max.a = fh.readFloat();
    }

    private static AlignType alignTypeFromId(int id) {
        switch (id) {
            case 1:
                return AlignType.WORLD_MESH;
            case 2:
                return AlignType.AXIS_ALIGNED;
            default:
                return AlignType.BILLBOARD;
        }
    }

    private static CoordType coordTypeFromId(int id) {
        switch (id) {
            case 1:
                return CoordType.LOCAL_POSITION;
            case 2:
                return CoordType.LOCAL;
            default:
                return CoordType.WORLD;
        }
    }
}
