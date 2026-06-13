package com.rose.mapeditor.model;

import com.rose.mapeditor.io.FileHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Rose Online character part list (.CHR). */
public final class Chr {

    public static final class Motion {
        public short id;
        public short motionId;
    }

    public static final class Effect {
        public short id;
        public short effectId;
    }

    public static final class Character {
        public boolean isActive;
        public short boneId;
        public String name = "";
        public final List<Short> models = new ArrayList<>();
        public final List<Motion> motions = new ArrayList<>();
        public final List<Effect> effects = new ArrayList<>();
    }

    public String filePath;
    public final List<String> bones = new ArrayList<>();
    public final List<String> motions = new ArrayList<>();
    public final List<String> effects = new ArrayList<>();
    public final List<Character> characters = new ArrayList<>();

    public Chr() {
    }

    public Chr(String filePath) throws IOException {
        load(filePath);
    }

    public void load(String filePath) throws IOException {
        this.filePath = filePath;
        bones.clear();
        motions.clear();
        effects.clear();
        characters.clear();

        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.READING, FileHandler.eucKr())) {
            short boneCount = fh.readShort();
            for (int i = 0; i < boneCount; i++) {
                bones.add(fh.readZString());
            }

            short motionCount = fh.readShort();
            for (int i = 0; i < motionCount; i++) {
                motions.add(fh.readZString());
            }

            short effectCount = fh.readShort();
            for (int i = 0; i < effectCount; i++) {
                effects.add(fh.readZString());
            }

            short characterCount = fh.readShort();
            for (int i = 0; i < characterCount; i++) {
                Character character = new Character();
                character.isActive = fh.readByte() > 0;
                characters.add(character);

                if (!character.isActive) {
                    continue;
                }

                character.boneId = fh.readShort();
                character.name = fh.readZString();

                int modelCount = fh.readShort();
                for (int j = 0; j < modelCount; j++) {
                    character.models.add(fh.readShort());
                }

                motionCount = fh.readShort();
                for (int j = 0; j < motionCount; j++) {
                    Motion motion = new Motion();
                    motion.id = fh.readShort();
                    motion.motionId = fh.readShort();
                    character.motions.add(motion);
                }

                effectCount = fh.readShort();
                for (int j = 0; j < effectCount; j++) {
                    Effect effect = new Effect();
                    effect.id = fh.readShort();
                    effect.effectId = fh.readShort();
                    character.effects.add(effect);
                }
            }
        }
    }
}
