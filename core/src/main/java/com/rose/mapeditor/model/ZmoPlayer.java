package com.rose.mapeditor.model;

/** Applies ZMO morph keyframes to ZMS vertex data. */
public final class ZmoPlayer {

    private ZmoPlayer() {
    }

    public static int advanceFrame(Zmo motion, int frame, long lastUpdateMs, long nowMs) {
        if (motion == null || motion.frames == null || motion.frames.length == 0) {
            return 0;
        }
        int fps = Math.max(1, motion.fps);
        long intervalMs = 1000L / fps;
        if (nowMs - lastUpdateMs < intervalMs) {
            return frame;
        }
        return (frame + 1) % motion.frames.length;
    }

    public static void applyFrame(Zms.Vertex[] base, Zms.Vertex[] out, Zmo motion, int frameIndex) {
        if (base == null || out == null || motion == null || motion.frames == null || motion.frames.length == 0) {
            return;
        }
        for (int i = 0; i < base.length; i++) {
            Zms.copyVertex(base[i], out[i]);
        }

        int frame = Math.floorMod(frameIndex, motion.frames.length);
        Zmo.Frame frameData = motion.frames[frame];
        for (int j = 0; j < motion.channels.length; j++) {
            Zmo.Channel channel = motion.channels[j];
            Zmo.FrameChannel data = frameData.channels[j];
            if (channel == null || channel.type == null || data == null) {
                continue;
            }
            int vertexId = channel.id;
            if (vertexId < 0 || vertexId >= out.length) {
                continue;
            }
            Zms.Vertex vertex = out[vertexId];
            switch (channel.type) {
                case POSITION:
                    vertex.position.set(data.position);
                    break;
                case ALPHA:
                    vertex.vertexAlpha = data.alpha;
                    break;
                case UV0:
                    vertex.texCoord.set(data.uv0);
                    break;
                case UV1:
                    vertex.lightmapCoord.set(data.uv1);
                    break;
                default:
                    break;
            }
        }
    }

    /** Reads object-level position/rotation/scale channels (decorations with ZSC Motion). */
    public static void applyObjectMotionFrame(Zmo motion, int frameIndex, com.rose.mapeditor.scene.AnimatedMeshRuntime out) {
        out.hasMotionPosition = false;
        out.hasMotionRotation = false;
        out.hasMotionScale = false;
        if (motion == null || motion.frames == null || motion.frames.length == 0) {
            return;
        }
        int frame = Math.floorMod(frameIndex, motion.frames.length);
        Zmo.Frame frameData = motion.frames[frame];
        for (int j = 0; j < motion.channels.length; j++) {
            Zmo.Channel channel = motion.channels[j];
            Zmo.FrameChannel data = frameData.channels[j];
            if (channel == null || channel.type == null || data == null) {
                continue;
            }
            switch (channel.type) {
                case POSITION:
                    out.motionPosition.set(data.position);
                    out.hasMotionPosition = true;
                    break;
                case ROTATION:
                    out.motionRotation.set(data.rotation);
                    out.hasMotionRotation = true;
                    break;
                case SCALE:
                    out.motionScale = data.scale;
                    out.hasMotionScale = true;
                    break;
                default:
                    break;
            }
        }
    }
}
