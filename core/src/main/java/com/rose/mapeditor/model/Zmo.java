package com.rose.mapeditor.model;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.io.FileHandler;

import java.io.IOException;

/** Rose Online morph animation (.ZMO) - per-vertex channel keyframes. */
public final class Zmo {

    public enum ChannelType {
        POSITION(1 << 1),
        ROTATION(1 << 2),
        NORMAL(1 << 3),
        ALPHA(1 << 4),
        UV0(1 << 5),
        UV1(1 << 6),
        UV2(1 << 7),
        UV3(1 << 8),
        TEXTURE_ANIMATION(1 << 9),
        SCALE(1 << 10);

        public final int id;

        ChannelType(int id) {
            this.id = id;
        }

        static ChannelType fromId(int id) {
            for (ChannelType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return null;
        }
    }

    public static final class Channel {
        public ChannelType type;
        public int id;
    }

    public static final class FrameChannel {
        public final Vector3 position = new Vector3();
        public final Quaternion rotation = new Quaternion();
        public final Vector3 normal = new Vector3();
        public float alpha;
        public final Vector2 uv0 = new Vector2();
        public final Vector2 uv1 = new Vector2();
        public final Vector2 uv2 = new Vector2();
        public final Vector2 uv3 = new Vector2();
        public float textureAnimation;
        public float scale;
    }

    public static final class Frame {
        public FrameChannel[] channels;
    }

    public int fps;
    public Channel[] channels;
    public Frame[] frames;

    public Zmo() {
    }

    public Zmo(String filePath, boolean camera, boolean divide) throws IOException {
        load(filePath, camera, divide);
    }

    public void load(String filePath, boolean camera, boolean divide) throws IOException {
        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.READING, null)) {
            fh.seek(8);
            fps = fh.readInt();
            int frameCount = fh.readInt();
            int channelCount = fh.readInt();

            channels = new Channel[channelCount];
            for (int i = 0; i < channelCount; i++) {
                Channel channel = new Channel();
                channel.type = ChannelType.fromId(fh.readInt());
                channel.id = fh.readInt();
                channels[i] = channel;
            }

            frames = new Frame[frameCount];
            for (int i = 0; i < frameCount; i++) {
                Frame frame = new Frame();
                frame.channels = new FrameChannel[channelCount];
                for (int j = 0; j < channelCount; j++) {
                    frame.channels[j] = new FrameChannel();
                    ChannelType type = channels[j].type;
                    if (type == null) {
                        continue;
                    }
                    FrameChannel data = frame.channels[j];
                    switch (type) {
                        case POSITION:
                            data.position.set(fh.readVector3());
                            if (camera && (j == 0 || j == 1)) {
                                data.position.set(fh.readVector3());
                                data.position.add(520000f, 520000f, 0f).scl(1f / 100f);
                            } else if (divide) {
                                data.position.scl(1f / 100f);
                            }
                            break;
                        case ROTATION: {
                            float w = fh.readFloat();
                            float x = fh.readFloat();
                            float y = fh.readFloat();
                            float z = fh.readFloat();
                            data.rotation.set(x, y, z, w);
                            break;
                        }
                        case NORMAL:
                            data.normal.set(fh.readVector3());
                            break;
                        case ALPHA:
                            data.alpha = fh.readFloat();
                            break;
                        case UV0:
                            data.uv0.set(fh.readFloat(), fh.readFloat());
                            break;
                        case UV1:
                            data.uv1.set(fh.readFloat(), fh.readFloat());
                            break;
                        case UV2:
                            data.uv2.set(fh.readFloat(), fh.readFloat());
                            break;
                        case UV3:
                            data.uv3.set(fh.readFloat(), fh.readFloat());
                            break;
                        case TEXTURE_ANIMATION:
                            data.textureAnimation = fh.readFloat();
                            break;
                        case SCALE:
                            data.scale = fh.readFloat();
                            break;
                        default:
                            break;
                    }
                }
                frames[i] = frame;
            }
        }
    }
}
