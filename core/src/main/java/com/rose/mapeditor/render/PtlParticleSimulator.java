package com.rose.mapeditor.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.model.Ptl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Runtime PTL particle simulation */
public final class PtlParticleSimulator {

    /** Rose game ticks per second of real time. */
    private static final float TICKS_PER_SECOND = 4800f;

    private static final class RuntimeEvent {
        float time;
        Ptl.KeyframeType type;
        boolean fade;
        RuntimeEvent fadeTarget;
        Ptl.Vector2Range size;
        Ptl.FloatRange timer;
        Ptl.FloatRange red;
        Ptl.FloatRange green;
        Ptl.FloatRange blue;
        Ptl.FloatRange alpha;
        Ptl.ColorRange color;
        Ptl.FloatRange velocityX;
        Ptl.FloatRange velocityY;
        Ptl.FloatRange velocityZ;
        Ptl.Vector3Range velocity;
        Ptl.FloatRange textureIndex;
        Ptl.FloatRange rotation;
    }

    public static final class LiveParticle {
        public float age;
        public float lifetime = 1f;
        public int eventIndex;
        public float eventTimer;
        public final Vector3 position = new Vector3();
        public final Vector3 gravity = new Vector3();
        public final Vector2 size = new Vector2(10f, 10f);
        public final Vector2 sizeStep = new Vector2();
        public float textureIndex;
        public float textureIndexStep;
        public int textureColumns = 1;
        public int textureRows = 1;
        public final Color color = new Color(1f, 1f, 1f, 1f);
        public final Color colorStep = new Color();
        public final Vector3 velocity = new Vector3();
        public final Vector3 velocityStep = new Vector3();
        public float rotation;
        public float rotationStep;
        public Ptl.AlignType alignType = Ptl.AlignType.BILLBOARD;
        public boolean visible = true;
        /** When true, {@link #worldRotation} reorients velocity (PTL world coords). */
        public boolean worldMotion;
        public final Matrix4 worldRotation = new Matrix4();
        public final Vector3 gravityLocal = new Vector3();
    }

    private final Ptl.Sequence sequence;
    private final Random random;
    private final List<RuntimeEvent> events = new ArrayList<>();
    private final List<LiveParticle> particles = new ArrayList<>();
    private final Vector2 scratchSize = new Vector2();
    private final Vector3 scratchVelocity = new Vector3();
    private final Color scratchColor = new Color();
    private final Matrix4 rootWorld = new Matrix4();
    private final Matrix4 rotationMatrix = new Matrix4();
    private final Matrix4 inverseRotation = new Matrix4();
    private final Vector3 motionScratch = new Vector3();
    private final Vector3 originScratch = new Vector3();

    private float emitAccumulator;
    private int totalParticleLives;
    private boolean running = true;

    public PtlParticleSimulator(Ptl.Sequence sequence, long seed) {
        this.sequence = sequence;
        this.random = new Random(seed);
        buildEvents();
    }

    public List<LiveParticle> particles() {
        return particles;
    }

    public Ptl.Sequence sequence() {
        return sequence;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRootWorld(Matrix4 world) {
        rootWorld.set(world);
    }

    public void update(float deltaSeconds) {
        if (!running) {
            return;
        }

        float dt = deltaSeconds * (TICKS_PER_SECOND / 1000f);

        for (int i = particles.size() - 1; i >= 0; i--) {
            LiveParticle particle = particles.get(i);
            if (stepParticle(particle, dt)) {
                applyEvents(particle);
            } else {
                particles.remove(i);
            }
        }

        float emitRate = sequence.emitRate.pick(random);
        int numNew = (int) Math.floor(emitRate * dt);
        emitAccumulator += (emitRate * dt) - numNew;
        if (emitAccumulator >= 1f) {
            numNew += (int) Math.floor(emitAccumulator);
            emitAccumulator -= (int) Math.floor(emitAccumulator);
        }

        int maxAlive = Math.max(0, sequence.particleCount);
        numNew = Math.min(numNew, maxAlive - particles.size());

        if (sequence.loopCount > 0) {
            int loopCap = sequence.loopCount * sequence.particleCount;
            if (totalParticleLives + numNew > loopCap) {
                numNew = loopCap - totalParticleLives;
                numNew = Math.max(0, numNew);
            }
            if (numNew == 0 && particles.isEmpty() && totalParticleLives >= loopCap) {
                running = false;
                return;
            }
        }

        for (int i = 0; i < numNew; i++) {
            spawnParticle();
        }

        if (particles.isEmpty() && numNew == 0 && sequence.loopCount > 0
            && totalParticleLives >= sequence.loopCount * sequence.particleCount) {
            running = false;
        }
    }

    /** Restarts a finished finite loop for continuous editor preview. */
    public void restartIfFinished() {
        if (!running && sequence.loopCount > 0) {
            particles.clear();
            totalParticleLives = 0;
            emitAccumulator = 0f;
            running = true;
        }
    }

    private void spawnParticle() {
        LiveParticle particle = new LiveParticle();
        particle.lifetime = sequence.lifeTime.pick(random);
        sequence.emitRadius.pick(random, particle.position);
        particle.textureColumns = sequence.spriteCols;
        particle.textureRows = sequence.spriteRows;
        particle.alignType = sequence.alignType;
        particle.eventIndex = 0;
        particle.eventTimer = 0f;

        if (sequence.coordType == Ptl.CoordType.WORLD) {
            extractRotation(rootWorld, rotationMatrix);
            particle.worldRotation.set(rotationMatrix);
            particle.worldMotion = true;
            particle.position.mul(rotationMatrix);
            rootWorld.getTranslation(originScratch);
            particle.position.add(originScratch);
            sequence.gravity.pick(random, motionScratch);
            inverseRotation.set(rotationMatrix).inv();
            particle.gravityLocal.set(motionScratch).mul(inverseRotation);
        } else {
            particle.worldMotion = false;
            sequence.gravity.pick(random, particle.gravity);
        }

        applyEvents(particle);
        stepParticle(particle, 0f);
        particles.add(particle);
        totalParticleLives++;
    }

    private boolean stepParticle(LiveParticle particle, float dt) {
        particle.age += dt;
        particle.eventTimer += dt;

        if (particle.age >= particle.lifetime) {
            return false;
        }

        if (particle.worldMotion) {
            motionScratch.set(particle.velocity).mul(particle.worldRotation);
            particle.position.mulAdd(motionScratch, dt);
            particle.velocity.mulAdd(particle.gravityLocal, dt);
        } else {
            particle.position.mulAdd(particle.velocity, dt);
            if (sequence.coordType != Ptl.CoordType.WORLD) {
                sequence.gravity.pick(random, particle.gravity);
            }
            particle.velocity.mulAdd(particle.gravity, dt);
        }
        particle.color.r += particle.colorStep.r * dt;
        particle.color.g += particle.colorStep.g * dt;
        particle.color.b += particle.colorStep.b * dt;
        particle.color.a += particle.colorStep.a * dt;
        particle.velocity.mulAdd(particle.velocityStep, dt);
        particle.size.x += particle.sizeStep.x * dt;
        particle.size.y += particle.sizeStep.y * dt;
        particle.textureIndex += particle.textureIndexStep * dt;
        particle.rotation += particle.rotationStep * dt;

        while (particle.rotation >= 360f) {
            particle.rotation -= 360f;
        }
        while (particle.rotation < 0f) {
            particle.rotation += 360f;
        }

        particle.visible = particle.size.x != 0f && particle.size.y != 0f;
        return true;
    }

    private void applyEvents(LiveParticle particle) {
        for (; particle.eventIndex < events.size(); particle.eventIndex++) {
            RuntimeEvent event = events.get(particle.eventIndex);
            if (event.time > particle.eventTimer) {
                break;
            }

            float fadeDuration = 0f;
            if (event.fadeTarget != null) {
                fadeDuration = event.fadeTarget.time - event.time;
                if (fadeDuration <= 0f) {
                    fadeDuration = 1f;
                }
            }

            switch (event.type) {
                case SIZE:
                    if (!event.fade) {
                        event.size.pick(random, particle.size);
                    }
                    if (event.fadeTarget != null) {
                        event.fadeTarget.size.pick(random, scratchSize);
                        particle.sizeStep.x = (scratchSize.x - particle.size.x) / fadeDuration;
                        particle.sizeStep.y = (scratchSize.y - particle.size.y) / fadeDuration;
                    }
                    break;
                case RED:
                    if (!event.fade) {
                        particle.color.r = event.red.pick(random);
                    }
                    if (event.fadeTarget != null) {
                        float next = event.fadeTarget.red.pick(random);
                        particle.colorStep.r = (next - particle.color.r) / fadeDuration;
                    }
                    break;
                case GREEN:
                    if (!event.fade) {
                        particle.color.g = event.green.pick(random);
                    }
                    if (event.fadeTarget != null) {
                        float next = event.fadeTarget.green.pick(random);
                        particle.colorStep.g = (next - particle.color.g) / fadeDuration;
                    }
                    break;
                case BLUE:
                    if (!event.fade) {
                        particle.color.b = event.blue.pick(random);
                    }
                    if (event.fadeTarget != null) {
                        float next = event.fadeTarget.blue.pick(random);
                        particle.colorStep.b = (next - particle.color.b) / fadeDuration;
                    }
                    break;
                case ALPHA:
                    if (!event.fade) {
                        particle.color.a = event.alpha.pick(random);
                    }
                    if (event.fadeTarget != null) {
                        float next = event.fadeTarget.alpha.pick(random);
                        particle.colorStep.a = (next - particle.color.a) / fadeDuration;
                    }
                    break;
                case COLOR:
                    if (!event.fade) {
                        event.color.pick(random, particle.color);
                    }
                    if (event.fadeTarget != null) {
                        event.fadeTarget.color.pick(random, scratchColor);
                        particle.colorStep.r = (scratchColor.r - particle.color.r) / fadeDuration;
                        particle.colorStep.g = (scratchColor.g - particle.color.g) / fadeDuration;
                        particle.colorStep.b = (scratchColor.b - particle.color.b) / fadeDuration;
                        particle.colorStep.a = (scratchColor.a - particle.color.a) / fadeDuration;
                    }
                    break;
                case VELOCITY_X:
                    if (!event.fade) {
                        particle.velocity.x = event.velocityX.pick(random);
                    }
                    if (event.fadeTarget != null) {
                        float next = event.fadeTarget.velocityX.pick(random);
                        particle.velocityStep.x = (next - particle.velocity.x) / fadeDuration;
                    }
                    break;
                case VELOCITY_Y:
                    if (!event.fade) {
                        particle.velocity.y = event.velocityY.pick(random);
                    }
                    if (event.fadeTarget != null) {
                        float next = event.fadeTarget.velocityY.pick(random);
                        particle.velocityStep.y = (next - particle.velocity.y) / fadeDuration;
                    }
                    break;
                case VELOCITY_Z:
                    if (!event.fade) {
                        particle.velocity.z = event.velocityZ.pick(random);
                    }
                    if (event.fadeTarget != null) {
                        float next = event.fadeTarget.velocityZ.pick(random);
                        particle.velocityStep.z = (next - particle.velocity.z) / fadeDuration;
                    }
                    break;
                case VELOCITY:
                    if (!event.fade) {
                        event.velocity.pick(random, particle.velocity);
                    }
                    if (event.fadeTarget != null) {
                        event.fadeTarget.velocity.pick(random, scratchVelocity);
                        particle.velocityStep.x = (scratchVelocity.x - particle.velocity.x) / fadeDuration;
                        particle.velocityStep.y = (scratchVelocity.y - particle.velocity.y) / fadeDuration;
                        particle.velocityStep.z = (scratchVelocity.z - particle.velocity.z) / fadeDuration;
                    }
                    break;
                case TEXTURE:
                    if (!event.fade) {
                        particle.textureIndex = event.textureIndex.pick(random);
                    }
                    if (event.fadeTarget != null) {
                        float next = event.fadeTarget.textureIndex.pick(random);
                        particle.textureIndexStep = (next - particle.textureIndex) / fadeDuration;
                    }
                    break;
                case ROTATION:
                    if (!event.fade) {
                        particle.rotation = event.rotation.pick(random);
                    }
                    if (event.fadeTarget != null) {
                        float next = event.fadeTarget.rotation.pick(random);
                        particle.rotationStep = (next - particle.rotation) / fadeDuration;
                    }
                    break;
                case TIMER:
                    particle.eventTimer = event.timer.pick(random);
                    particle.eventIndex = 0;
                    for (; particle.eventIndex < events.size(); particle.eventIndex++) {
                        if (events.get(particle.eventIndex).time >= particle.eventTimer) {
                            break;
                        }
                    }
                    particle.eventIndex--;
                    break;
                default:
                    break;
            }
        }
    }

    private void buildEvents() {
        events.clear();
        for (Ptl.Keyframe keyframe : sequence.keyframes) {
            RuntimeEvent event = new RuntimeEvent();
            event.type = keyframe.type;
            event.time = keyframe.startTime.pick(random);
            event.fade = keyframe.fade;
            event.size = keyframe.size;
            event.timer = keyframe.timer;
            event.red = keyframe.red;
            event.green = keyframe.green;
            event.blue = keyframe.blue;
            event.alpha = keyframe.alpha;
            event.color = keyframe.color;
            event.velocityX = keyframe.velocityX;
            event.velocityY = keyframe.velocityY;
            event.velocityZ = keyframe.velocityZ;
            event.velocity = keyframe.velocity;
            event.textureIndex = keyframe.textureIndex;
            event.rotation = keyframe.rotation;
            events.add(event);
        }

        events.sort(Comparator.comparingDouble(event -> event.time));

        for (int i = 0; i < events.size(); i++) {
            RuntimeEvent event = events.get(i);
            for (int j = i + 1; j < events.size(); j++) {
                RuntimeEvent other = events.get(j);
                if (other.fade && other.type == event.type) {
                    event.fadeTarget = other;
                    break;
                }
            }
        }
    }

    private static void extractRotation(Matrix4 world, Matrix4 out) {
        out.set(world);
        out.val[Matrix4.M03] = 0f;
        out.val[Matrix4.M13] = 0f;
        out.val[Matrix4.M23] = 0f;
    }
}
