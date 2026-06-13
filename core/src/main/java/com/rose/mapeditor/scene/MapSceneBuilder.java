package com.rose.mapeditor.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.rose.mapeditor.GameData;
import com.rose.mapeditor.data.Stb;
import com.rose.mapeditor.data.Stl;
import com.rose.mapeditor.map.Ifo;
import com.rose.mapeditor.map.Lit;
import com.rose.mapeditor.map.MapLoader;
import com.rose.mapeditor.model.Chr;
import com.rose.mapeditor.model.Zmo;
import com.rose.mapeditor.model.ZmoAnimationMode;
import com.rose.mapeditor.model.Zms;
import com.rose.mapeditor.model.Zsc;
import com.rose.mapeditor.render.AnimatedMeshFactory;
import com.rose.mapeditor.render.RoseTransform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Builds a {@link MapScene} from loaded map + IFO + ZSC + LIT data. */
public final class MapSceneBuilder {

    private Matrix4 collisionTemplateWorld;
    private Zms collisionTemplateMesh;
    private Matrix4 warpTemplateWorld;
    private Zms warpTemplateMesh;

    public MapScene build(MapLoader.LoadedMap map) throws IOException {
        GameData data = GameData.get();
        data.loadCoreZscs();
        data.loadMapZscs(map.mapId);

        MapScene scene = new MapScene();
        loadDecoSpecialTemplates(data);

        for (int fileIndex = 0; fileIndex < map.ifoFiles.size(); fileIndex++) {
            Ifo ifo = map.ifoFiles.get(fileIndex);
            String subFolder = ifo.fileName.length() >= 5 ? ifo.fileName.substring(0, 5) : ifo.fileName;

            Lit decorationLit = loadLitIfExists(map.mapFolder, subFolder, "LIGHTMAP/OBJECTLIGHTMAPDATA.LIT");
            Lit constructionLit = loadLitIfExists(map.mapFolder, subFolder, "LIGHTMAP/BUILDINGLIGHTMAPDATA.LIT");

            for (int j = 0; j < ifo.decoration.size(); j++) {
                addZscObject(scene, data.zscs.get("Decoration"), ifo, "Decoration", "Decoration",
                    ifo.decoration.get(j), j, decorationLit, MapObjectKind.DECORATION);
            }
            for (int j = 0; j < ifo.construction.size(); j++) {
                addZscObject(scene, data.zscs.get("Construction"), ifo, "Construction", "Construction",
                    ifo.construction.get(j), j, constructionLit, MapObjectKind.CONSTRUCTION);
            }
            for (int j = 0; j < ifo.eventTriggers.size(); j++) {
                addZscObject(scene, data.zscs.get("EVENT_OBJECT"), ifo, "EVENT_OBJECT", "Event trigger",
                    ifo.eventTriggers.get(j), j, null, MapObjectKind.EVENT_TRIGGER);
            }
            for (Ifo.BaseEntry entry : ifo.animation) {
                addAnimationObject(scene, data, entry);
            }
            for (Ifo.BaseEntry entry : ifo.warpGates) {
                addTemplateMesh(scene, warpTemplateMesh, warpTemplateWorld, entry, MapObjectKind.WARP_GATE);
            }
            for (Ifo.BaseEntry entry : ifo.collision) {
                addTemplateMesh(scene, collisionTemplateMesh, collisionTemplateWorld, entry, MapObjectKind.COLLISION);
            }
            for (Ifo.WaterVolume vol : ifo.water) {
                scene.waterVolumes.add(vol);
                scene.waterSurfaces.add(WaterSurface.fromVolume(vol));
            }
            for (int j = 0; j < ifo.npcs.size(); j++) {
                addNpcObject(scene, data, ifo, j, ifo.npcs.get(j));
            }
            for (int j = 0; j < ifo.monsters.size(); j++) {
                addMonsterObject(scene, data, ifo, j, ifo.monsters.get(j));
            }
            for (int j = 0; j < ifo.sounds.size(); j++) {
                Ifo.SoundEntry entry = ifo.sounds.get(j);
                SceneObjectRef ref = SceneObjectRef.forEntry(ifo, MapObjectKind.SOUND, "Sound", j, entry, null);
                MapMarker marker = addMarker(scene, entry.position, MapObjectKind.SOUND, Color.YELLOW, 2f, entry.path, ref);
                marker.range = entry.range / 100f;
            }
            for (Ifo.EffectEntry entry : ifo.effects) {
                addMarker(scene, entry.position, MapObjectKind.EFFECT, Color.MAGENTA, 2f, entry.path);
            }
        }

        prepareSky(scene, map.mapId, data);

        Gdx.app.log("MapSceneBuilder", scene.summary());
        return scene;
    }

    private void loadDecoSpecialTemplates(GameData data) throws IOException {
        Zsc decoSpecial = data.zscs.get("LIST_DECO_SPECIAL");
        if (decoSpecial == null || decoSpecial.objects.size() < 3) {
            return;
        }

        warpTemplateMesh = loadZms(data, decoSpecial, decoSpecial.objects.get(1));
        warpTemplateWorld = partWorld(decoSpecial.objects.get(1).models.get(0));

        collisionTemplateMesh = loadZms(data, decoSpecial, decoSpecial.objects.get(2));
        collisionTemplateWorld = partWorld(decoSpecial.objects.get(2).models.get(0));
    }

    private static Matrix4 partWorld(Zsc.PartModel part) {
        return RoseTransform.fromRose(part.position, part.rotation, part.scale);
    }

    private static Lit loadLitIfExists(String mapFolder, String subFolder, String relative) {
        Path litPath = Path.of(mapFolder, subFolder, relative);
        if (!Files.isRegularFile(litPath)) {
            return null;
        }
        try {
            return new Lit(litPath.toString());
        } catch (IOException e) {
            Gdx.app.error("MapSceneBuilder", "Failed loading LIT: " + litPath, e);
            return null;
        }
    }

    private void addZscObject(MapScene scene, Zsc zsc, Ifo ifo, String zscName, String blockName,
                              Ifo.BaseEntry entry, int entryIndex, Lit lit, MapObjectKind kind) {
        SceneObjectRef ref = SceneObjectRef.forEntry(ifo, kind, blockName, entryIndex, entry, zscName);
        if (zsc == null || entry.objectId < 0 || entry.objectId >= zsc.objects.size()) {
            addMarker(scene, entry.position, kind, Color.GRAY, 2f, entry.description, ref);
            return;
        }

        Zsc.SceneObject zscObject = zsc.objects.get(entry.objectId);
        Matrix4 objectWorld = RoseTransform.fromRose(entry.position, entry.rotation, entry.scale);

        for (int partIndex = 0; partIndex < zscObject.models.size(); partIndex++) {
            Zsc.PartModel part = zscObject.models.get(partIndex);

            Zms zms;
            try {
                zms = loadZms(GameData.get(), zsc.models.get(part.modelId));
            } catch (IOException e) {
                Gdx.app.error("MapSceneBuilder", "ZMS load failed: " + zsc.models.get(part.modelId), e);
                continue;
            }

            Matrix4 partLocal = partWorld(part);
            Matrix4 world = new Matrix4();
            RoseTransform.combinePartObject(world, partLocal, objectWorld);

            MeshInstance inst = new MeshInstance();
            inst.kind = kind;
            inst.mesh = zms;
            inst.pickRef = ref;
            inst.ifoWorld.set(objectWorld);
            inst.world.set(world);

            if (part.textureId >= 0 && part.textureId < zsc.textures.size()) {
                applyTextureState(inst, zsc.textures.get(part.textureId));
            }

            attachPartMotion(part, inst, zms);
            applyLightmap(inst, lit, entryIndex, partIndex);
            scene.meshes.add(inst);
        }
    }

    private void attachPartMotion(Zsc.PartModel part, MeshInstance inst, Zms zms) {
        if (part.motion == null || part.motion.isBlank()) {
            return;
        }
        try {
            Zmo zmo = GameData.get().getOrLoadZmo(part.motion.trim());
            if (ZmoAnimationMode.isVertexMorph(zmo)) {
                inst.animation = AnimatedMeshFactory.prepareMorphCpu(zms, zmo);
            } else {
                inst.animation = AnimatedMeshFactory.prepareObjectMotionCpu(zmo, part);
            }
        } catch (IOException e) {
            Gdx.app.error("MapSceneBuilder", "ZMO load failed: " + part.motion, e);
        }
    }

    private void applyLightmap(MeshInstance inst, Lit lit, int entryIndex, int partIndex) {
        if (lit == null || entryIndex < 0) {
            return;
        }
        try {
            int litObjectIdx = lit.searchObject(entryIndex + 1);
            if (litObjectIdx < 0) {
                return;
            }
            Lit.LitObject litObject = lit.objects.get(litObjectIdx);
            int litPartIdx = lit.searchPart(litObjectIdx, partIndex);
            if (litPartIdx < 0) {
                return;
            }
            Lit.Part litPart = litObject.parts.get(litPartIdx);
            int objectsPerWidth = litPart.objectsPerWidth;
            int mapPosition = litPart.mapPosition;

            inst.lightmapEnabled = true;
            inst.lightmapPath = lit.folder + "/" + litPart.ddsName;
            inst.lightmapAdd.set(mapPosition % objectsPerWidth, mapPosition / objectsPerWidth, 0);
            float inv = 1f / objectsPerWidth;
            inst.lightmapMul.set(inv, inv, 1);
        } catch (Exception e) {
            inst.lightmapEnabled = false;
        }
    }

    private void addTemplateMesh(MapScene scene, Zms templateMesh, Matrix4 templatePartWorld,
                                 Ifo.BaseEntry entry, MapObjectKind kind) {
        if (templateMesh == null || templatePartWorld == null) {
            addMarker(scene, entry.position, kind, Color.ORANGE, 2f, entry.description);
            return;
        }
        Matrix4 objectWorld = RoseTransform.fromRose(entry.position, entry.rotation, entry.scale);
        Matrix4 world = new Matrix4();
        RoseTransform.combinePartObject(world, templatePartWorld, objectWorld);

        MeshInstance inst = new MeshInstance();
        inst.kind = kind;
        inst.mesh = templateMesh;
        inst.world.set(world);
        scene.meshes.add(inst);
    }

    private void prepareSky(MapScene scene, int mapId, GameData data) {
        try {
            Stb listZone = data.stbs.get("LIST_ZONE");
            Stb listSky = data.stbs.get("LIST_SKY");
            if (listZone == null || listSky == null) {
                return;
            }
            int skyId = Integer.parseInt(listZone.cell(mapId, 8).trim());
            if (skyId < 0 || skyId >= listSky.cells.size()) {
                return;
            }
            String modelPath = listSky.cell(skyId, 1);
            String texturePath = listSky.cell(skyId, 2);
            if (modelPath == null || modelPath.isBlank() || texturePath == null || texturePath.isBlank()) {
                return;
            }
            scene.skyModelPath = modelPath;
            scene.skyTexturePath = texturePath;
            scene.skyMesh = data.getOrLoadZms(modelPath);
        } catch (Exception e) {
            Gdx.app.error("MapSceneBuilder", "Sky load failed for map " + mapId, e);
        }
    }

    private void addMonsterObject(MapScene scene, GameData data, Ifo ifo, int entryIndex,
                                  Ifo.MonsterSpawnEntry entry) {
        int chrId = resolveMonsterCharacterId(entry, data);
        String label = monsterMarkerLabel(data, entry, chrId);
        SceneObjectRef ref = SceneObjectRef.forEntry(ifo, MapObjectKind.MONSTER, "Monster", entryIndex, entry, "LIST_NPC");
        ref.chrId = chrId;

        if (chrId < 0) {
            MapMarker marker = addMarker(scene, entry.position, MapObjectKind.MONSTER, Color.BLUE, 3f, label, ref);
            marker.range = entry.range / 100f;
            return;
        }

        Stb listNpc = data.stbs.get("LIST_NPC");
        Zsc partNpc = data.zscs.get("PART_NPC");
        Chr chr;
        try {
            chr = data.getOrLoadListNpcChr();
        } catch (IOException e) {
            Gdx.app.error("MapSceneBuilder", "CHR load failed for monster spawn", e);
            MapMarker marker = addMarker(scene, entry.position, MapObjectKind.MONSTER, Color.BLUE, 3f, label, ref);
            marker.range = entry.range / 100f;
            return;
        }

        if (listNpc == null || partNpc == null || chrId >= chr.characters.size()) {
            MapMarker marker = addMarker(scene, entry.position, MapObjectKind.MONSTER, Color.BLUE, 3f, label, ref);
            marker.range = entry.range / 100f;
            return;
        }

        Chr.Character chrCharacter = chr.characters.get(chrId);
        if (!chrCharacter.isActive || chrCharacter.models.isEmpty()) {
            MapMarker marker = addMarker(scene, entry.position, MapObjectKind.MONSTER, Color.BLUE, 3f, label, ref);
            marker.range = entry.range / 100f;
            return;
        }

        float scale = monsterScale(listNpc, chrId);
        Vector3 uniformScale = new Vector3(scale, scale, scale);
        Quaternion identity = new Quaternion();
        Matrix4 objectWorld = RoseTransform.fromRose(entry.position, identity, uniformScale);
        BoundingBox bounds = new BoundingBox();
        boolean hasBounds = addChrPartMeshes(scene, data, partNpc, chrCharacter, objectWorld,
            MapObjectKind.MONSTER, bounds, ref);

        MapMarker marker = addMarker(scene, entry.position, MapObjectKind.MONSTER, Color.BLUE, 3f, label, ref);
        marker.range = entry.range / 100f;
        if (hasBounds) {
            marker.useWorldBounds = true;
            marker.boundsMin.set(bounds.min);
            marker.boundsMax.set(bounds.max);
        }
    }

    private static int resolveMonsterCharacterId(Ifo.MonsterSpawnEntry entry, GameData data) {
        Chr chr;
        try {
            chr = data.getOrLoadListNpcChr();
        } catch (IOException e) {
            return -1;
        }
        for (Ifo.SpawnMonster spawn : entry.basic) {
            if (isActiveChrId(chr, spawn.id)) {
                return spawn.id;
            }
        }
        for (Ifo.SpawnMonster spawn : entry.tactic) {
            if (isActiveChrId(chr, spawn.id)) {
                return spawn.id;
            }
        }
        return -1;
    }

    private static boolean isActiveChrId(Chr chr, int id) {
        return id >= 0 && id < chr.characters.size() && chr.characters.get(id).isActive;
    }

    private static float monsterScale(Stb listNpc, int chrId) {
        float scale = 1f;
        try {
            if (listNpc != null && chrId >= 0 && chrId < listNpc.cells.size()) {
                scale = Integer.parseInt(listNpc.cell(chrId, 5).trim()) / 100f;
            }
        } catch (Exception ignored) {
            scale = 1f;
        }
        return scale > 0f ? scale : 1f;
    }

    private static String monsterMarkerLabel(GameData data, Ifo.MonsterSpawnEntry entry, int chrId) {
        if (chrId >= 0) {
            Stb listNpc = data.stbs.get("LIST_NPC");
            Stl listNpcS = data.stls.get("LIST_NPC_S");
            if (listNpc != null && listNpcS != null && chrId < listNpc.cells.size()) {
                try {
                    String nameId = listNpc.cell(chrId, 41).trim();
                    if (!nameId.isEmpty()) {
                        String name = listNpcS.search(nameId);
                        if (name != null && !name.isBlank()) {
                            return name.trim();
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            return "[" + chrId + "]";
        }
        if (entry.name != null && !entry.name.isBlank()) {
            return entry.name.trim();
        }
        return "Empty spawn";
    }

    private boolean addChrPartMeshes(MapScene scene, GameData data, Zsc partNpc, Chr.Character chrCharacter,
                                     Matrix4 objectWorld, MapObjectKind kind, BoundingBox bounds,
                                     SceneObjectRef ref) {
        boolean hasBounds = false;
        for (short characterModelId : chrCharacter.models) {
            if (characterModelId < 0 || characterModelId >= partNpc.objects.size()) {
                continue;
            }
            Zsc.SceneObject zscObject = partNpc.objects.get(characterModelId);
            for (Zsc.PartModel part : zscObject.models) {
                if (part.modelId < 0 || part.modelId >= partNpc.models.size()) {
                    continue;
                }
                Zms zms;
                try {
                    zms = loadZms(data, partNpc.models.get(part.modelId));
                } catch (IOException e) {
                    Gdx.app.error("MapSceneBuilder", "CHR ZMS load failed: " + partNpc.models.get(part.modelId), e);
                    continue;
                }

                Matrix4 partLocal = partWorld(part);
                Matrix4 world = new Matrix4();
                RoseTransform.combinePartObject(world, partLocal, objectWorld);

                MeshInstance inst = new MeshInstance();
                inst.kind = kind;
                inst.mesh = zms;
                inst.pickRef = ref;
                inst.ifoWorld.set(objectWorld);
                inst.world.set(world);

                if (part.textureId >= 0 && part.textureId < partNpc.textures.size()) {
                    applyTextureState(inst, partNpc.textures.get(part.textureId));
                }

                scene.meshes.add(inst);
                hasBounds = expandMeshBounds(bounds, zms, world, hasBounds);
            }
        }
        return hasBounds;
    }

    private void addNpcObject(MapScene scene, GameData data, Ifo ifo, int entryIndex, Ifo.NpcEntry entry) {
        SceneObjectRef ref = SceneObjectRef.forEntry(ifo, MapObjectKind.NPC, "NPC", entryIndex, entry, "LIST_NPC");
        ref.chrId = entry.objectId;
        String label = npcMarkerLabel(data, entry);

        Stb listNpc = data.stbs.get("LIST_NPC");
        Zsc partNpc = data.zscs.get("PART_NPC");
        Chr chr;
        try {
            chr = data.getOrLoadListNpcChr();
        } catch (IOException e) {
            Gdx.app.error("MapSceneBuilder", "CHR load failed", e);
            addMarker(scene, entry.position, MapObjectKind.NPC, Color.GREEN, 2.5f, label, ref);
            return;
        }

        if (listNpc == null || partNpc == null || entry.objectId < 0 || entry.objectId >= chr.characters.size()) {
            addMarker(scene, entry.position, MapObjectKind.NPC, Color.GREEN, 2.5f, label, ref);
            return;
        }

        Chr.Character chrCharacter = chr.characters.get(entry.objectId);
        if (!chrCharacter.isActive || chrCharacter.models.isEmpty()) {
            addMarker(scene, entry.position, MapObjectKind.NPC, Color.GREEN, 2.5f, label, ref);
            return;
        }

        float npcScale = monsterScale(listNpc, entry.objectId);
        Vector3 uniformScale = new Vector3(npcScale, npcScale, npcScale);
        Matrix4 objectWorld = RoseTransform.fromRose(entry.position, entry.rotation, uniformScale);
        BoundingBox npcBounds = new BoundingBox();
        boolean hasNpcBounds = addChrPartMeshes(scene, data, partNpc, chrCharacter, objectWorld,
            MapObjectKind.NPC, npcBounds, ref);

        MapMarker marker = addMarker(scene, entry.position, MapObjectKind.NPC, Color.GREEN, 2.5f, label, ref);
        if (hasNpcBounds) {
            marker.useWorldBounds = true;
            marker.boundsMin.set(npcBounds.min);
            marker.boundsMax.set(npcBounds.max);
        }
    }

    private void addAnimationObject(MapScene scene, GameData data, Ifo.BaseEntry entry) {
        Stb morphList = data.stbs.get("LIST_MORPH_OBJECT");
        if (morphList == null || entry.objectId < 0 || entry.objectId >= morphList.cells.size()) {
            addMarker(scene, entry.position, MapObjectKind.ANIMATION, Color.CYAN, 2f, entry.description);
            return;
        }
        try {
            String zmsPath = morphList.cell(entry.objectId, 2);
            String zmoPath = morphList.cell(entry.objectId, 3);
            String texturePath = morphList.cell(entry.objectId, 4);
            if (zmsPath == null || zmsPath.isBlank()) {
                addMarker(scene, entry.position, MapObjectKind.ANIMATION, Color.CYAN, 2f, entry.description);
                return;
            }

            Zms zms = loadZms(data, zmsPath);
            MeshInstance inst = new MeshInstance();
            inst.kind = MapObjectKind.ANIMATION;
            inst.mesh = zms;
            applyTextureState(inst, texturePath);
            inst.alphaBlend = true;
            inst.alphaTest = true;
            inst.twoSided = true;
            inst.world.set(RoseTransform.fromRose(entry.position, entry.rotation, entry.scale));

            if (zmoPath != null && !zmoPath.isBlank()) {
                Zmo zmo = data.getOrLoadZmo(zmoPath);
                inst.animation = AnimatedMeshFactory.prepareMorphCpu(zms, zmo);
            }

            scene.meshes.add(inst);
        } catch (Exception e) {
            Gdx.app.error("MapSceneBuilder", "Animation load failed for objectId " + entry.objectId, e);
            addMarker(scene, entry.position, MapObjectKind.ANIMATION, Color.CYAN, 2f, entry.description);
        }
    }

    private MapMarker addMarker(MapScene scene, Vector3 position, MapObjectKind kind,
                                Color color, float size, String label) {
        return addMarker(scene, position, kind, color, size, label, null);
    }

    private MapMarker addMarker(MapScene scene, Vector3 position, MapObjectKind kind,
                                Color color, float size, String label, SceneObjectRef ref) {
        MapMarker marker = new MapMarker();
        marker.kind = kind;
        marker.position.set(position);
        marker.color.set(color);
        marker.size = size;
        marker.label = label != null ? label : "";
        marker.pickRef = ref;
        scene.markers.add(marker);
        return marker;
    }

    private static String npcMarkerLabel(GameData data, Ifo.NpcEntry entry) {
        if (entry.description != null && !entry.description.isBlank()) {
            return entry.description.trim();
        }
        Stb listNpc = data.stbs.get("LIST_NPC");
        Stl listNpcS = data.stls.get("LIST_NPC_S");
        if (listNpc != null && listNpcS != null
            && entry.objectId >= 0 && entry.objectId < listNpc.cells.size()) {
            try {
                String nameId = listNpc.cell(entry.objectId, 41).trim();
                if (!nameId.isEmpty()) {
                    return listNpcS.search(nameId);
                }
            } catch (Exception ignored) {
            }
        }
        return entry.objectId >= 0 ? "[" + entry.objectId + "]" : "";
    }

    private Zms loadZms(GameData data, Zsc zsc, Zsc.SceneObject object) throws IOException {
        if (object.models.isEmpty()) {
            throw new IOException("ZSC object has no model parts");
        }
        short modelId = object.models.get(0).modelId;
        return loadZms(data, zsc.models.get(modelId));
    }

    private Zms loadZms(GameData data, String relativePath) throws IOException {
        return data.getOrLoadZms(relativePath);
    }

    private static void applyTextureState(MeshInstance inst, Zsc.TextureEntry entry) {
        if (entry == null) {
            return;
        }
        inst.diffusePath = entry.path;
        inst.alphaBlend = entry.alphaEnabled;
        inst.twoSided = entry.twoSided;
        inst.alphaTest = entry.alphaTestEnabled;
        inst.alphaReference = entry.alphaReference & 0xFF;
        inst.alpha = entry.alpha;
    }

    private static void applyTextureState(MeshInstance inst, String diffusePath) {
        inst.diffusePath = diffusePath;
    }

    private static boolean expandMeshBounds(BoundingBox target, Zms zms, Matrix4 world, boolean hasBounds) {
        if (zms.vertices == null || zms.vertices.length == 0) {
            return hasBounds;
        }
        Vector3 tmp = new Vector3();
        for (Zms.Vertex vertex : zms.vertices) {
            tmp.set(vertex.position).mul(world);
            if (!hasBounds) {
                target.inf();
                hasBounds = true;
            }
            target.ext(tmp);
        }
        return hasBounds;
    }
}
