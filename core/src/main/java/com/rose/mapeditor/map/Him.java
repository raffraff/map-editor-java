package com.rose.mapeditor.map;

import com.rose.mapeditor.io.FileHandler;

import java.io.IOException;

/** Rose Online heightmap (.HIM). */
public final class Him {

    public float[][] position;
    public String filePath;

    public Him() {
    }

    public Him(String filePath) throws IOException {
        load(filePath);
    }

    public void load(String filePath) throws IOException {
        this.filePath = filePath;
        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.READING, FileHandler.eucKr())) {
            int height = fh.readInt();
            int width = fh.readInt();
            fh.seek(fh.tell() + 8);

            position = new float[height][width];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    position[y][x] = fh.readFloat() / 100.0f;
                }
            }
        }
    }

    /** Writes heights and quad-tree metadata */
    public void save() throws IOException {
        if (filePath == null || position == null) {
            return;
        }
        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.WRITING, FileHandler.eucKr())) {
            fh.writeInt(65);
            fh.writeInt(65);
            fh.writeInt(4);
            fh.writeFloat(250.0f);

            float[][] mapHeight = new float[65][65];
            for (int y = 0; y < 65; y++) {
                for (int x = 0; x < 65; x++) {
                    float stored = position[y][x] * 100.0f;
                    fh.writeFloat(stored);
                    mapHeight[65 - (y + 1)][x] = stored;
                }
            }

            float[][][] patchHeights = new float[16][16][25];
            float[][] patchMax = new float[16][16];
            float[][] patchMin = new float[16][16];

            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    for (int k = 0; k < 5; k++) {
                        for (int l = 0; l < 5; l++) {
                            patchHeights[i][j][((4 - k) * 5) + l] = mapHeight[(i * 4) + k][(j * 4) + l];
                        }
                    }
                    patchMax[i][j] = Float.NEGATIVE_INFINITY;
                    patchMin[i][j] = Float.POSITIVE_INFINITY;
                    for (int k = 0; k < 25; k++) {
                        patchMax[i][j] = Math.max(patchMax[i][j], patchHeights[i][j][k]);
                        patchMin[i][j] = Math.min(patchMin[i][j], patchHeights[i][j][k]);
                    }
                }
            }

            fh.writeBString("quad");
            fh.writeInt(256);

            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    fh.writeFloat(patchMax[i][j]);
                    fh.writeFloat(patchMin[i][j]);
                }
            }

            fh.writeInt(85);
            for (int i = 0; i < 85; i++) {
                fh.writeFloat(-10.0f);
                fh.writeFloat(10000.0f);
            }
        }
    }
}
