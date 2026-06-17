package com.rose.mapeditor.map;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.GameData;
import com.rose.mapeditor.data.Stb;
import com.rose.mapeditor.data.Stl;
import com.rose.mapeditor.model.Eft;
import com.rose.mapeditor.model.Ptl;
import com.rose.mapeditor.model.Zsc;
import com.rose.mapeditor.scene.MapObjectKind;
import com.rose.mapeditor.scene.SceneObjectRef;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Builds a read-only summary for a picked map object. */
public final class ObjectInfoFormatter {

    private ObjectInfoFormatter() {
    }

    public static String titleFor(SceneObjectRef ref) {
        if (ref == null) {
            return "Object";
        }
        String kind = kindLabel(ref.kind);
        if (ref.entry instanceof Ifo.MonsterSpawnEntry) {
            Ifo.MonsterSpawnEntry monster = (Ifo.MonsterSpawnEntry) ref.entry;
            if (monster.name != null && !monster.name.isBlank()) {
                return kind + " - " + monster.name.trim();
            }
        }
        if (ref.entry instanceof Ifo.SoundEntry) {
            Ifo.SoundEntry sound = (Ifo.SoundEntry) ref.entry;
            if (sound.path != null && !sound.path.isBlank()) {
                return kind + " - " + sound.path.trim();
            }
        }
        if (ref.entry instanceof Ifo.EffectEntry) {
            Ifo.EffectEntry effect = (Ifo.EffectEntry) ref.entry;
            if (effect.path != null && !effect.path.isBlank()) {
                return kind + " - " + effect.path.trim();
            }
        }
        if (ref.entry.description != null && !ref.entry.description.isBlank()) {
            return kind + " - " + ref.entry.description.trim();
        }
        if (ref.entry.objectId >= 0) {
            return kind + " #" + ref.entry.objectId;
        }
        return kind;
    }

    public static String format(GameData data, SceneObjectRef ref) {
        if (ref == null || ref.entry == null) {
            return "No object selected.";
        }

        StringBuilder out = new StringBuilder();
        appendLine(out, "Type", kindLabel(ref.kind));
        appendLine(out, "IFO file", blankToDash(ref.ifoFileName));
        appendLine(out, "IFO path", blankToDash(ref.ifoPath));
        appendLine(out, "Block", blankToDash(ref.blockName));
        appendLine(out, "Entry index", ref.entryIndex >= 0 ? Integer.toString(ref.entryIndex) : "-");

        Ifo.BaseEntry entry = ref.entry;
        appendLine(out, "Object ID", Integer.toString(entry.objectId));
        appendLine(out, "Object type", entry.objectType != null ? entry.objectType.name() : "-");
        appendVector3(out, "Position", entry.position);
        appendQuaternion(out, "Rotation", entry.rotation);
        appendVector3(out, "Scale", entry.scale);
        appendVector2(out, "Map cell", entry.mapPosition);
        appendLine(out, "Warp ID", Short.toString(entry.warpId));
        appendLine(out, "Event ID", Short.toString(entry.eventId));

        if (entry.description != null && !entry.description.isBlank()) {
            appendLine(out, "Description", entry.description.trim());
        }

        if (ref.zscName != null && !ref.zscName.isBlank()) {
            appendLine(out, "ZSC table", ref.zscName);
            appendZscInfo(out, data, ref.zscName, entry.objectId);
        }

        if (ref.kind == MapObjectKind.NPC) {
            appendNpcInfo(out, data, (Ifo.NpcEntry) entry, ref.chrId);
        } else if (ref.kind == MapObjectKind.MONSTER) {
            appendMonsterInfo(out, data, (Ifo.MonsterSpawnEntry) entry, ref.chrId);
        } else if (ref.kind == MapObjectKind.SOUND) {
            appendSoundInfo(out, (Ifo.SoundEntry) entry);
        } else if (ref.kind == MapObjectKind.EFFECT) {
            appendEffectInfo(out, data, (Ifo.EffectEntry) entry);
        }

        return out.toString().trim();
    }

    private static void appendNpcInfo(StringBuilder out, GameData data, Ifo.NpcEntry entry, int chrId) {
        int npcId = chrId >= 0 ? chrId : entry.objectId;
        appendLine(out, "LIST_NPC ID", Integer.toString(npcId));
        appendLine(out, "Name", resolveListNpcName(data, npcId));
        if (entry.path != null && !entry.path.isBlank()) {
            appendLine(out, "Path", entry.path.trim());
        }
        appendLine(out, "AI pattern", Integer.toString(entry.aiPatternIndex));
    }

    private static void appendMonsterInfo(StringBuilder out, GameData data, Ifo.MonsterSpawnEntry entry, int chrId) {
        if (entry.name != null && !entry.name.isBlank()) {
            appendLine(out, "Spawn name", entry.name.trim());
        }
        appendLine(out, "Range", formatWorldUnits(entry.range));
        appendLine(out, "Limit", Integer.toString(entry.limit));
        appendLine(out, "Interval", Integer.toString(entry.interval));
        appendLine(out, "Tactic points", Integer.toString(entry.tacticPoints));

        if (chrId >= 0) {
            appendLine(out, "Preview monster ID", Integer.toString(chrId));
            appendLine(out, "Preview monster name", resolveListNpcName(data, chrId));
            appendLine(out, "Preview scale", formatNpcScale(data, chrId));
        }

        if (!entry.basic.isEmpty()) {
            out.append('\n').append("Basic spawns:").append('\n');
            for (int i = 0; i < entry.basic.size(); i++) {
                Ifo.SpawnMonster spawn = entry.basic.get(i);
                out.append("  [").append(i).append("] id=").append(spawn.id)
                    .append(" count=").append(spawn.count);
                if (spawn.description != null && !spawn.description.isBlank()) {
                    out.append(" - ").append(spawn.description.trim());
                }
                out.append('\n');
            }
        }

        if (!entry.tactic.isEmpty()) {
            out.append('\n').append("Tactic spawns:").append('\n');
            for (int i = 0; i < entry.tactic.size(); i++) {
                Ifo.SpawnMonster spawn = entry.tactic.get(i);
                out.append("  [").append(i).append("] id=").append(spawn.id)
                    .append(" count=").append(spawn.count);
                if (spawn.description != null && !spawn.description.isBlank()) {
                    out.append(" - ").append(spawn.description.trim());
                }
                out.append('\n');
            }
        }
    }

    private static void appendSoundInfo(StringBuilder out, Ifo.SoundEntry entry) {
        appendLine(out, "Sound file", blankToDash(entry.path));
        appendLine(out, "Range", formatWorldUnits(entry.range));
        appendLine(out, "Interval", Integer.toString(entry.interval));
    }

    private static void appendEffectInfo(StringBuilder out, GameData data, Ifo.EffectEntry entry) {
        appendLine(out, "EFT file", blankToDash(entry.path));
        if (entry.path != null && !entry.path.isBlank()) {
            appendEftContents(out, data, entry.path.trim(), "  ");
        }
    }

    private static void appendZscInfo(StringBuilder out, GameData data, String zscName, int objectId) {
        Zsc zsc = data.zscs.get(zscName);
        if (zsc == null || objectId < 0 || objectId >= zsc.objects.size()) {
            return;
        }

        appendZscFileLocation(out, data, zsc);

        Zsc.SceneObject sceneObject = zsc.objects.get(objectId);
        out.append('\n').append("ZSC object #").append(objectId).append(" (")
            .append(sceneObject.models.size()).append(" part(s)):").append('\n');

        for (int partIndex = 0; partIndex < sceneObject.models.size(); partIndex++) {
            Zsc.PartModel part = sceneObject.models.get(partIndex);
            out.append("  Part ").append(partIndex).append(':');
            if (part.modelId >= 0 && part.modelId < zsc.models.size()) {
                out.append(" mesh=").append(zsc.models.get(part.modelId));
            }
            if (part.textureId >= 0 && part.textureId < zsc.textures.size()) {
                Zsc.TextureEntry texture = zsc.textures.get(part.textureId);
                out.append(" texture=").append(texture.path);
            }
            if (part.motion != null && !part.motion.isBlank()) {
                out.append(" motion=").append(part.motion.trim());
            }
            out.append('\n');
        }

        appendZscAttachedEffects(out, data, zsc, sceneObject);
    }

    private static void appendZscFileLocation(StringBuilder out, GameData data, Zsc zsc) {
        if (zsc.filePath == null || zsc.filePath.isBlank()) {
            appendLine(out, "ZSC file", "-");
            return;
        }
        Path absolute = Paths.get(zsc.filePath).toAbsolutePath().normalize();
        Path gameRoot = data.getGameRoot().toAbsolutePath().normalize();
        appendLine(out, "ZSC file", formatRelative(gameRoot, absolute));
        appendLine(out, "ZSC file (absolute)", absolute.toString().replace('\\', '/'));
    }

    private static void appendZscAttachedEffects(StringBuilder out, GameData data, Zsc zsc,
                                                 Zsc.SceneObject sceneObject) {
        if (sceneObject.effects.isEmpty()) {
            return;
        }

        out.append('\n').append("Attached effects (").append(sceneObject.effects.size()).append("):").append('\n');
        for (int i = 0; i < sceneObject.effects.size(); i++) {
            Zsc.PartEffect slot = sceneObject.effects.get(i);
            out.append("  [").append(i).append("] ");

            String eftPath = null;
            if (slot.effectId >= 0 && slot.effectId < zsc.effects.size()) {
                eftPath = zsc.effects.get(slot.effectId);
                out.append("EFT=").append(blankToDash(eftPath));
            } else {
                out.append("EFT=(invalid id ").append(slot.effectId).append(')');
            }
            out.append('\n');

            out.append("      type=").append(effectTypeName(slot.effectType));
            out.append(", parent=").append(effectParentLabel(slot.parent));
            out.append(", effectId=").append(slot.effectId).append('\n');
            appendVector3Indented(out, "position", slot.position, "      ");
            appendQuaternionIndented(out, "rotation", slot.rotation, "      ");
            appendVector3Indented(out, "scale", slot.scale, "      ");

            if (eftPath != null && !eftPath.isBlank()) {
                appendEftContents(out, data, eftPath.trim(), "      ");
            }
        }
    }

    private static void appendEftContents(StringBuilder out, GameData data, String eftPath, String indent) {
        try {
            Eft eft = data.getOrLoadEft(eftPath);
            for (int i = 0; i < eft.particles.size(); i++) {
                Eft.ParticleEntry particle = eft.particles.get(i);
                out.append(indent).append("Particle ").append(i).append(": PTL=")
                    .append(blankToDash(particle.particleFile)).append('\n');
                appendPtlSummary(out, data, particle.particleFile, indent + "  ");
            }
            for (int i = 0; i < eft.meshes.size(); i++) {
                Eft.MeshEntry mesh = eft.meshes.get(i);
                out.append(indent).append("Mesh ").append(i).append(": ZMS=")
                    .append(blankToDash(mesh.meshFile));
                if (mesh.meshTextureFile != null && !mesh.meshTextureFile.isBlank()) {
                    out.append(" texture=").append(mesh.meshTextureFile.trim());
                }
                out.append('\n');
            }
        } catch (IOException e) {
            out.append(indent).append("(EFT load failed: ").append(eftPath).append(')').append('\n');
        }
    }

    private static void appendPtlSummary(StringBuilder out, GameData data, String ptlPath, String indent) {
        if (ptlPath == null || ptlPath.isBlank()) {
            return;
        }
        try {
            Ptl ptl = data.getOrLoadPtl(ptlPath.trim());
            for (int i = 0; i < ptl.sequences.size(); i++) {
                Ptl.Sequence sequence = ptl.sequences.get(i);
                out.append(indent).append("Sequence ").append(i).append(": ");
                if (sequence.name != null && !sequence.name.isBlank()) {
                    out.append('"').append(sequence.name.trim()).append("\" ");
                }
                out.append("texture=").append(blankToDash(sequence.texturePath));
                out.append(" count=").append(sequence.particleCount);
                out.append(" coord=").append(sequence.coordType.name().toLowerCase());
                out.append(" align=").append(sequence.alignType.name().toLowerCase());
                out.append('\n');
            }
        } catch (IOException e) {
            out.append(indent).append("(PTL load failed: ").append(ptlPath.trim()).append(')').append('\n');
        }
    }

    private static String effectTypeName(short effectType) {
        switch (effectType) {
            case 1:
                return "Day/Night";
            case 2:
                return "Light container";
            default:
                return "Normal";
        }
    }

    private static String effectParentLabel(short parent) {
        return parent < 0 ? "object root" : "part " + parent;
    }

    private static String formatRelative(Path gameRoot, Path target) {
        try {
            return gameRoot.relativize(target).toString().replace('\\', '/');
        } catch (Exception ignored) {
            return target.toString().replace('\\', '/');
        }
    }

    private static void appendVector3Indented(StringBuilder out, String label, Vector3 value, String indent) {
        if (value == null) {
            return;
        }
        out.append(indent).append(label).append(": ")
            .append(String.format("(%.2f, %.2f, %.2f)", value.x, value.y, value.z)).append('\n');
    }

    private static void appendQuaternionIndented(StringBuilder out, String label, Quaternion value, String indent) {
        if (value == null || value.isIdentity()) {
            return;
        }
        out.append(indent).append(label).append(": ")
            .append(String.format("(%.3f, %.3f, %.3f, %.3f)", value.x, value.y, value.z, value.w))
            .append('\n');
    }

    private static String resolveListNpcName(GameData data, int chrId) {
        Stb listNpc = data.stbs.get("LIST_NPC");
        Stl listNpcS = data.stls.get("LIST_NPC_S");
        if (listNpc == null || listNpcS == null || chrId < 0 || chrId >= listNpc.cells.size()) {
            return "-";
        }
        try {
            String nameId = listNpc.cell(chrId, 41).trim();
            if (nameId.isEmpty()) {
                return "-";
            }
            String name = listNpcS.search(nameId);
            return name != null && !name.isBlank() ? name.trim() : nameId;
        } catch (Exception ignored) {
            return "-";
        }
    }

    private static String formatNpcScale(GameData data, int chrId) {
        Stb listNpc = data.stbs.get("LIST_NPC");
        if (listNpc == null || chrId < 0 || chrId >= listNpc.cells.size()) {
            return "-";
        }
        try {
            return Integer.parseInt(listNpc.cell(chrId, 5).trim()) / 100f + "x";
        } catch (Exception ignored) {
            return "-";
        }
    }

    private static String formatWorldUnits(int raw) {
        return raw + " (" + (raw / 100f) + " world)";
    }

    private static String kindLabel(MapObjectKind kind) {
        if (kind == null) {
            return "-";
        }
        if (kind == MapObjectKind.DECORATION) {
            return "Decoration";
        }
        if (kind == MapObjectKind.CONSTRUCTION) {
            return "Building / Construction";
        }
        if (kind == MapObjectKind.NPC) {
            return "NPC";
        }
        if (kind == MapObjectKind.MONSTER) {
            return "Monster spawn";
        }
        if (kind == MapObjectKind.SOUND) {
            return "Sound";
        }
        return kind.name();
    }

    private static void appendLine(StringBuilder out, String label, String value) {
        out.append(label).append(": ").append(value != null ? value : "-").append('\n');
    }

    private static void appendVector3(StringBuilder out, String label, Vector3 value) {
        if (value == null) {
            appendLine(out, label, "-");
            return;
        }
        appendLine(out, label, String.format("(%.2f, %.2f, %.2f)", value.x, value.y, value.z));
    }

    private static void appendVector2(StringBuilder out, String label, Vector2 value) {
        if (value == null) {
            appendLine(out, label, "-");
            return;
        }
        appendLine(out, label, String.format("(%.1f, %.1f)", value.x, value.y));
    }

    private static void appendQuaternion(StringBuilder out, String label, Quaternion value) {
        if (value == null) {
            appendLine(out, label, "-");
            return;
        }
        appendLine(out, label, String.format("(%.3f, %.3f, %.3f, %.3f)",
            value.x, value.y, value.z, value.w));
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }
}
