package com.rose.mapeditor.map.flyff;

/** Decoded Flyff terrain image in BGRA8888 row-major order. */
public final class FlyffTextureImage {

    public final int width;
    public final int height;
    public final byte[] bgra;

    public FlyffTextureImage(int width, int height, byte[] bgra) {
        this.width = width;
        this.height = height;
        this.bgra = bgra;
    }

    /** Tileable sample; {@code u}/{@code v} may exceed 0..1. Writes RGBA floats 0..1. */
    public void sample(float u, float v, float[] rgba) {
        float fu = u - (float) Math.floor(u);
        float fv = v - (float) Math.floor(v);
        if (fu < 0f) {
            fu += 1f;
        }
        if (fv < 0f) {
            fv += 1f;
        }

        int x = Math.min(width - 1, (int) (fu * width));
        int y = Math.min(height - 1, (int) (fv * height));
        if (width <= 0 || height <= 0) {
            rgba[0] = rgba[1] = rgba[2] = 0f;
            rgba[3] = 1f;
            return;
        }

        int offset = (y * width + x) * 4;
        rgba[0] = Byte.toUnsignedInt(bgra[offset + 2]) / 255f;
        rgba[1] = Byte.toUnsignedInt(bgra[offset + 1]) / 255f;
        rgba[2] = Byte.toUnsignedInt(bgra[offset]) / 255f;
        rgba[3] = Byte.toUnsignedInt(bgra[offset + 3]) / 255f;
    }
}
