package com.rose.mapeditor.model;

/** Distinguishes ZMO files used for vertex morph vs part transform motion. */
public final class ZmoAnimationMode {

    private ZmoAnimationMode() {
    }

    /** True when ZMO keyframes deform mesh vertices. */
    public static boolean isVertexMorph(Zmo zmo) {
        if (zmo == null || zmo.channels == null) {
            return false;
        }
        for (Zmo.Channel channel : zmo.channels) {
            if (channel == null || channel.type == null) {
                continue;
            }
            switch (channel.type) {
                case ALPHA:
                case UV0:
                case UV1:
                case UV2:
                case UV3:
                case NORMAL:
                case TEXTURE_ANIMATION:
                    return true;
                case POSITION:
                    if (channel.id > 0) {
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        return false;
    }
}
