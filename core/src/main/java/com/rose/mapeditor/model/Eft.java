package com.rose.mapeditor.model;

import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.io.FileHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Rose Online effect container (.EFT) with particle and mesh emitters. */
public final class Eft {

    /** EFT emitter offsets are stored in centimeters (same as ZSC positions). */
    private static final float ROSE_UNIT_SCALE = 0.01f;

    public static final class ParticleEntry {
        public String particleFile = "";
        public String animationFile;
        public int animationRepeatCount;
        public final Vector3 position = new Vector3();
        public float pitch;
        public float yaw;
        public float roll;
        public int startDelay;
        public boolean linked;
    }

    public static final class MeshEntry {
        public String meshFile = "";
        public String meshAnimationFile;
        public String meshTextureFile = "";
        public boolean alphaEnabled;
        public boolean twoSided;
        public boolean alphaTestEnabled;
        public boolean depthTestEnabled = true;
        public boolean depthWriteEnabled = true;
        public int srcBlendFactor;
        public int dstBlendFactor;
        public int blendOp;
        public String animationFile;
        public int animationRepeatCount;
        public final Vector3 position = new Vector3();
        public float pitch;
        public float yaw;
        public float roll;
        public int startDelay;
        public int repeatCount;
        public boolean linked;
    }

    public String soundFile;
    public int soundRepeatCount;
    public final List<ParticleEntry> particles = new ArrayList<>();
    public final List<MeshEntry> meshes = new ArrayList<>();

    public Eft(String filePath) throws IOException {
        load(filePath);
    }

    public void load(String filePath) throws IOException {
        particles.clear();
        meshes.clear();
        soundFile = null;
        soundRepeatCount = 0;

        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.READING, null)) {
            fh.skipBytes(fh.readInt());
            boolean useSoundFile = fh.readInt() != 0;
            String soundPath = fh.readU32String();
            soundRepeatCount = fh.readInt();
            if (useSoundFile && isValidPath(soundPath)) {
                soundFile = soundPath;
            }

            int particleCount = fh.readInt();
            for (int i = 0; i < particleCount; i++) {
                particles.add(readParticle(fh));
            }

            int meshCount = fh.readInt();
            for (int i = 0; i < meshCount; i++) {
                meshes.add(readMesh(fh));
            }
        }
    }

    private static ParticleEntry readParticle(FileHandler fh) throws IOException {
        ParticleEntry entry = new ParticleEntry();
        fh.skipBytes(fh.readInt());
        fh.skipBytes(fh.readInt());
        fh.skipBytes(4);

        entry.particleFile = fh.readU32String();
        boolean useAnimation = fh.readInt() != 0;
        String animationPath = fh.readU32String();
        entry.animationRepeatCount = fh.readInt();
        fh.skipBytes(4);

        entry.position.set(fh.readVector3()).scl(ROSE_UNIT_SCALE);
        entry.yaw = fh.readFloat();
        entry.pitch = fh.readFloat();
        entry.roll = fh.readFloat();
        fh.skipBytes(4);

        entry.startDelay = fh.readInt();
        entry.linked = fh.readInt() != 0;

        if (useAnimation && isValidPath(animationPath)) {
            entry.animationFile = animationPath;
        }
        return entry;
    }

    private static MeshEntry readMesh(FileHandler fh) throws IOException {
        MeshEntry entry = new MeshEntry();
        fh.skipBytes(fh.readInt());
        fh.skipBytes(fh.readInt());
        fh.skipBytes(4);

        entry.meshFile = fh.readU32String();
        String meshAnimationPath = fh.readU32String();
        entry.meshTextureFile = fh.readU32String();

        entry.alphaEnabled = fh.readInt() != 0;
        entry.twoSided = fh.readInt() != 0;
        entry.alphaTestEnabled = fh.readInt() != 0;
        entry.depthTestEnabled = fh.readInt() != 0;
        entry.depthWriteEnabled = fh.readInt() != 0;
        entry.srcBlendFactor = fh.readInt();
        entry.dstBlendFactor = fh.readInt();
        entry.blendOp = fh.readInt();

        boolean useAnimation = fh.readInt() != 0;
        String animationPath = fh.readU32String();
        entry.animationRepeatCount = fh.readInt();
        fh.skipBytes(4);

        entry.position.set(fh.readVector3()).scl(ROSE_UNIT_SCALE);
        entry.yaw = fh.readFloat();
        entry.pitch = fh.readFloat();
        entry.roll = fh.readFloat();
        fh.skipBytes(4);

        entry.startDelay = fh.readInt();
        entry.repeatCount = fh.readInt();
        entry.linked = fh.readInt() != 0;

        if (isValidPath(meshAnimationPath)) {
            entry.meshAnimationFile = meshAnimationPath;
        }
        if (useAnimation && isValidPath(animationPath)) {
            entry.animationFile = animationPath;
        }
        return entry;
    }

    private static boolean isValidPath(String path) {
        return path != null && !path.isBlank() && !"NULL".equalsIgnoreCase(path.trim());
    }
}
