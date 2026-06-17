package com.rose.mapeditor.tool;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import com.rose.mapeditor.RoseCoords;
import com.rose.mapeditor.model.Zms;
import com.rose.mapeditor.render.PtlParticleSimulator;
import com.rose.mapeditor.scene.EffectParticleEmitter;
import com.rose.mapeditor.scene.MapMarker;
import com.rose.mapeditor.scene.MapObjectKind;
import com.rose.mapeditor.scene.MapScene;
import com.rose.mapeditor.scene.MeshInstance;
import com.rose.mapeditor.scene.SceneObjectRef;

/** Raycasts against map meshes and markers to find a clickable object. */
public final class SceneObjectPicker {

    private static final MapObjectKind[] PICKABLE = {
        MapObjectKind.DECORATION,
        MapObjectKind.CONSTRUCTION,
        MapObjectKind.NPC,
        MapObjectKind.MONSTER,
        MapObjectKind.SOUND,
        MapObjectKind.EFFECT
    };

    private final Vector3 hitPoint = new Vector3();
    private final Vector3 v0 = new Vector3();
    private final Vector3 v1 = new Vector3();
    private final Vector3 v2 = new Vector3();
    private final BoundingBox bounds = new BoundingBox();
    private final BoundingBox markerBounds = new BoundingBox();
    private final Vector3 boundsMin = new Vector3();
    private final Vector3 boundsMax = new Vector3();
    private final Matrix4 pickWorld = new Matrix4();
    private final Vector3 roseScratch = new Vector3();

    public static final class PickResult {
        public SceneObjectRef ref;
        public float distance;
    }

    public PickResult pick(MapScene scene, Ray ray, PickResult out) {
        if (scene == null || ray == null || out == null) {
            return null;
        }

        float bestDistance = Float.MAX_VALUE;
        SceneObjectRef bestRef = null;

        for (int i = 0; i < scene.meshes.size(); i++) {
            MeshInstance mesh = scene.meshes.get(i);
            if (!isPickable(mesh.kind) || mesh.pickRef == null || mesh.mesh == null) {
                continue;
            }
            if (intersectMesh(ray, mesh, hitPoint)) {
                float distance = distanceAlongRay(ray, hitPoint);
                if (distance > 0f && distance < bestDistance) {
                    bestDistance = distance;
                    bestRef = mesh.pickRef;
                }
            }
        }

        for (MapMarker marker : scene.markers) {
            if (!isPickable(marker.kind) || marker.pickRef == null) {
                continue;
            }
            if (intersectMarker(ray, marker, hitPoint)) {
                float distance = distanceAlongRay(ray, hitPoint);
                if (distance > 0f && distance < bestDistance) {
                    bestDistance = distance;
                    bestRef = marker.pickRef;
                }
            }
        }

        for (EffectParticleEmitter emitter : scene.effectEmitters) {
            if (emitter.pickRef == null) {
                continue;
            }
            for (PtlParticleSimulator.LiveParticle particle : emitter.simulator.particles()) {
                if (!particle.visible) {
                    continue;
                }
                if (intersectLiveParticle(emitter, particle, ray, hitPoint)) {
                    float distance = distanceAlongRay(ray, hitPoint);
                    if (distance > 0f && distance < bestDistance) {
                        bestDistance = distance;
                        bestRef = emitter.pickRef;
                    }
                }
            }
        }

        for (EffectParticleEmitter emitter : scene.effectEmitters) {
            if (emitter.pickRef == null) {
                continue;
            }
            if (intersectEmitterOrigin(emitter, ray, hitPoint)) {
                float distance = distanceAlongRay(ray, hitPoint);
                if (distance > 0f && distance < bestDistance) {
                    bestDistance = distance;
                    bestRef = emitter.pickRef;
                }
            }
        }

        if (bestRef == null) {
            return null;
        }

        out.ref = bestRef;
        out.distance = bestDistance;
        return out;
    }

    private static boolean isPickable(MapObjectKind kind) {
        for (MapObjectKind pickable : PICKABLE) {
            if (pickable == kind) {
                return true;
            }
        }
        return false;
    }

    private boolean intersectMesh(Ray ray, MeshInstance instance, Vector3 hitOut) {
        Zms zms = instance.mesh;
        if (zms.vertices == null || zms.indices == null || zms.indices.length < 3) {
            return false;
        }

        if (!meshBounds(zms, instance, bounds)) {
            return false;
        }
        if (!Intersector.intersectRayBounds(ray, bounds, hitOut)) {
            return false;
        }

        float bestDistance = Float.MAX_VALUE;
        Vector3 bestHit = null;
        MeshPickTransforms.worldMatrix(instance, pickWorld);

        for (int i = 0; i < zms.indices.length; i += 3) {
            MeshPickTransforms.vertexInGdx(zms, zms.indices[i], pickWorld, v0);
            MeshPickTransforms.vertexInGdx(zms, zms.indices[i + 1], pickWorld, v1);
            MeshPickTransforms.vertexInGdx(zms, zms.indices[i + 2], pickWorld, v2);
            if (!Intersector.intersectRayTriangle(ray, v0, v1, v2, hitPoint)) {
                continue;
            }

            float distance = distanceAlongRay(ray, hitPoint);
            if (distance > 0f && distance < bestDistance) {
                bestDistance = distance;
                bestHit = hitPoint.cpy();
            }
        }

        if (bestHit == null) {
            return false;
        }
        hitOut.set(bestHit);
        return true;
    }

    private boolean intersectMarker(Ray ray, MapMarker marker, Vector3 hitOut) {
        if (marker.useWorldBounds) {
            Vector3 minGdx = RoseCoords.toGdx(marker.boundsMin);
            Vector3 maxGdx = RoseCoords.toGdx(marker.boundsMax);
            boundsMin.set(
                Math.min(minGdx.x, maxGdx.x),
                Math.min(minGdx.y, maxGdx.y),
                Math.min(minGdx.z, maxGdx.z)
            );
            boundsMax.set(
                Math.max(minGdx.x, maxGdx.x),
                Math.max(minGdx.y, maxGdx.y),
                Math.max(minGdx.z, maxGdx.z)
            );
            markerBounds.set(boundsMin, boundsMax);
        } else {
            Vector3 gdx = RoseCoords.toGdx(marker.position);
            float half = marker.size * 0.5f;
            boundsMin.set(gdx.x - half, gdx.y, gdx.z - half);
            boundsMax.set(gdx.x + half, gdx.y + marker.size, gdx.z + half);
            markerBounds.set(boundsMin, boundsMax);
        }

        if (marker.range > 0f) {
            Vector3 gdx = RoseCoords.toGdx(marker.position);
            float range = marker.range;
            markerBounds.ext(gdx.x - range, gdx.y, gdx.z - range);
            markerBounds.ext(gdx.x + range, gdx.y + 0.5f, gdx.z + range);
        }

        return Intersector.intersectRayBounds(ray, markerBounds, hitOut);
    }

    private boolean intersectLiveParticle(EffectParticleEmitter emitter,
                                          PtlParticleSimulator.LiveParticle particle,
                                          Ray ray, Vector3 hitOut) {
        transformParticlePosition(emitter, particle.position, roseScratch);
        Vector3 gdx = RoseCoords.toGdx(roseScratch);
        float halfW = particle.size.x * 0.5f;
        float halfH = particle.size.y * 0.5f;
        boundsMin.set(gdx.x - halfW, gdx.y - halfH, gdx.z - halfW);
        boundsMax.set(gdx.x + halfW, gdx.y + halfH, gdx.z + halfW);
        markerBounds.set(boundsMin, boundsMax);
        return Intersector.intersectRayBounds(ray, markerBounds, hitOut);
    }

    private boolean intersectEmitterOrigin(EffectParticleEmitter emitter, Ray ray, Vector3 hitOut) {
        Vector3 gdx = RoseCoords.toGdx(emitter.origin(roseScratch));
        float half = 2f;
        boundsMin.set(gdx.x - half, gdx.y, gdx.z - half);
        boundsMax.set(gdx.x + half, gdx.y + half * 2f, gdx.z + half);
        markerBounds.set(boundsMin, boundsMax);
        return Intersector.intersectRayBounds(ray, markerBounds, hitOut);
    }

    private void transformParticlePosition(EffectParticleEmitter emitter, Vector3 localRose, Vector3 outRose) {
        emitter.worldPosition(localRose, outRose);
    }

    private boolean meshBounds(Zms zms, MeshInstance instance, BoundingBox out) {
        if (zms.vertices == null || zms.vertices.length == 0) {
            return false;
        }
        MeshPickTransforms.worldMatrix(instance, pickWorld);
        out.inf();
        for (Zms.Vertex vertex : zms.vertices) {
            roseScratch.set(vertex.position).mul(pickWorld);
            v0.set(RoseCoords.toGdx(roseScratch));
            out.ext(v0);
        }
        return true;
    }

    private static float distanceAlongRay(Ray ray, Vector3 hit) {
        return (hit.x - ray.origin.x) * ray.direction.x
            + (hit.y - ray.origin.y) * ray.direction.y
            + (hit.z - ray.origin.z) * ray.direction.z;
    }
}
