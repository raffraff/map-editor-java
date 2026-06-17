package com.rose.mapeditor.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Matrix4;
import com.rose.mapeditor.GameData;
import com.rose.mapeditor.map.Ifo;
import com.rose.mapeditor.model.Eft;
import com.rose.mapeditor.model.Ptl;
import com.rose.mapeditor.model.Zmo;
import com.rose.mapeditor.model.ZmoAnimationMode;
import com.rose.mapeditor.model.Zms;
import com.rose.mapeditor.model.Zsc;
import com.rose.mapeditor.render.AnimatedMeshFactory;
import com.rose.mapeditor.render.PtlParticleSimulator;
import com.rose.mapeditor.render.RoseTransform;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/** Builds mesh and particle instances from EFT files (IFO placements and ZSC attachments). */
public final class EffectSceneBuilder {

    private EffectSceneBuilder() {
    }

    public static boolean addEffect(MapScene scene, GameData data, Ifo.EffectEntry entry, SceneObjectRef ref) {
        if (entry.path == null || entry.path.isBlank()) {
            return false;
        }
        Matrix4 ifoWorld = RoseTransform.fromRose(entry.position, entry.rotation, entry.scale);
        return addEftAtRoot(scene, data, entry.path.trim(), ifoWorld, ref);
    }

    /** Spawns EFT mesh/particle content from a ZSC object effect slot. */
    public static int addZscAttachedEffects(MapScene scene, GameData data, Zsc zsc, Zsc.SceneObject zscObject,
                                            Matrix4 objectWorld, List<Matrix4> partWorlds, SceneObjectRef ref) {
        if (zsc == null || zscObject == null || zscObject.effects.isEmpty()) {
            return 0;
        }

        int built = 0;
        for (Zsc.PartEffect partEffect : zscObject.effects) {
            if (partEffect.effectId < 0 || partEffect.effectId >= zsc.effects.size()) {
                continue;
            }
            if (partEffect.effectType == 2) {
                continue;
            }

            String eftPath = zsc.effects.get(partEffect.effectId);
            if (eftPath == null || eftPath.isBlank()) {
                continue;
            }

            Matrix4 effectLocal = RoseTransform.fromRose(
                partEffect.position,
                RoseTransform.zscEffectRotationForEditor(partEffect.rotation),
                RoseTransform.remapScaleForEditorAxisSwap(partEffect.scale)
            );
            Matrix4 parentWorld = objectWorld;
            if (partEffect.parent >= 0 && partWorlds != null && partEffect.parent < partWorlds.size()) {
                parentWorld = partWorlds.get(partEffect.parent);
            }

            Matrix4 effectWorld = new Matrix4();
            RoseTransform.combinePartObject(effectWorld, effectLocal, parentWorld);

            if (addEftAtRoot(scene, data, eftPath.trim(), effectWorld, ref)) {
                built++;
            }
        }
        return built;
    }

    public static boolean addEftAtRoot(MapScene scene, GameData data, String eftPath, Matrix4 rootWorld,
                                       SceneObjectRef ref) {
        if (!eftFileExists(data, eftPath)) {
            Gdx.app.error("EffectSceneBuilder", "EFT not found: " + eftPath);
            return false;
        }

        Eft eft;
        try {
            eft = data.getOrLoadEft(eftPath);
        } catch (IOException e) {
            Gdx.app.error("EffectSceneBuilder", "EFT load failed: " + eftPath, e);
            return false;
        }

        int built = 0;
        for (Eft.MeshEntry meshEntry : eft.meshes) {
            if (addMeshEffect(scene, data, meshEntry, rootWorld, ref)) {
                built++;
            }
        }
        for (Eft.ParticleEntry particleEntry : eft.particles) {
            built += addParticleEffect(scene, data, particleEntry, rootWorld, ref, eftPath);
        }
        return built > 0;
    }

    private static boolean eftFileExists(GameData data, String relativePath) {
        try {
            return Files.isRegularFile(data.resolve(relativePath.trim()));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean addMeshEffect(MapScene scene, GameData data, Eft.MeshEntry meshEntry,
                                         Matrix4 rootWorld, SceneObjectRef ref) {
        if (meshEntry.meshFile == null || meshEntry.meshFile.isBlank()) {
            return false;
        }

        Zms zms;
        try {
            zms = data.getOrLoadZms(meshEntry.meshFile.trim());
        } catch (IOException e) {
            Gdx.app.error("EffectSceneBuilder", "Effect mesh load failed: " + meshEntry.meshFile, e);
            return false;
        }

        Matrix4 local = RoseTransform.fromRose(
            meshEntry.position,
            RoseTransform.fromEftMeshRotationDegrees(meshEntry.yaw, meshEntry.pitch, meshEntry.roll)
        );
        Matrix4 world = new Matrix4();
        RoseTransform.combinePartObject(world, local, rootWorld);

        MeshInstance inst = new MeshInstance();
        inst.kind = MapObjectKind.EFFECT;
        inst.mesh = zms;
        inst.pickRef = ref;
        inst.ifoWorld.set(rootWorld);
        inst.world.set(world);
        inst.diffusePath = meshEntry.meshTextureFile;
        inst.alphaBlend = meshEntry.alphaEnabled || !meshEntry.depthWriteEnabled;
        inst.twoSided = meshEntry.twoSided;
        inst.alphaTest = meshEntry.alphaTestEnabled;
        inst.blendSrc = meshEntry.srcBlendFactor;
        inst.blendDst = meshEntry.dstBlendFactor;
        inst.blendOp = meshEntry.blendOp;
        inst.depthWriteEnabled = meshEntry.depthWriteEnabled;
        inst.alpha = 1f;

        try {
            if (meshEntry.meshAnimationFile != null && !meshEntry.meshAnimationFile.isBlank()) {
                Zmo zmo = data.getOrLoadZmo(meshEntry.meshAnimationFile.trim());
                inst.animation = AnimatedMeshFactory.prepareMorphCpu(zms, zmo);
            } else if (meshEntry.animationFile != null && !meshEntry.animationFile.isBlank()) {
                Zmo zmo = data.getOrLoadZmo(meshEntry.animationFile.trim());
                Zsc.PartModel part = effectPartAnchor(meshEntry);
                if (ZmoAnimationMode.isVertexMorph(zmo)) {
                    inst.animation = AnimatedMeshFactory.prepareMorphCpu(zms, zmo);
                } else {
                    inst.animation = AnimatedMeshFactory.prepareObjectMotionCpu(zmo, part);
                }
            }
        } catch (IOException e) {
            Gdx.app.error("EffectSceneBuilder", "Effect animation load failed", e);
        }

        scene.meshes.add(inst);
        return true;
    }

    private static int addParticleEffect(MapScene scene, GameData data, Eft.ParticleEntry particleEntry,
                                         Matrix4 rootWorld, SceneObjectRef ref, String eftPath) {
        if (particleEntry.particleFile == null || particleEntry.particleFile.isBlank()) {
            return 0;
        }

        Ptl ptl;
        try {
            ptl = data.getOrLoadPtl(particleEntry.particleFile.trim());
        } catch (IOException e) {
            Gdx.app.error("EffectSceneBuilder", "PTL load failed: " + particleEntry.particleFile, e);
            return 0;
        }

        Matrix4 local = RoseTransform.fromRose(
            particleEntry.position,
            RoseTransform.fromEftMeshRotationDegrees(particleEntry.yaw, particleEntry.pitch, particleEntry.roll)
        );
        Matrix4 world = new Matrix4();
        RoseTransform.combinePartObject(world, local, rootWorld);

        int built = 0;
        for (int i = 0; i < ptl.sequences.size(); i++) {
            Ptl.Sequence sequence = ptl.sequences.get(i);
            if (sequence.texturePath == null || sequence.texturePath.isBlank()) {
                continue;
            }

            long seed = eftPath.hashCode() * 31L + particleEntry.particleFile.hashCode() * 17L + i;
            PtlParticleSimulator simulator = new PtlParticleSimulator(sequence, seed);
            EffectParticleEmitter emitter = new EffectParticleEmitter(simulator, sequence.texturePath);
            emitter.rootWorld.set(world);
            emitter.pickRef = ref;
            scene.effectEmitters.add(emitter);
            built++;
        }
        return built;
    }

    private static Zsc.PartModel effectPartAnchor(Eft.MeshEntry meshEntry) {
        Zsc.PartModel part = new Zsc.PartModel();
        part.position.set(meshEntry.position);
        part.rotation.set(RoseTransform.fromEftMeshRotationDegrees(
            meshEntry.yaw, meshEntry.pitch, meshEntry.roll
        ));
        part.scale.set(1f, 1f, 1f);
        return part;
    }
}
