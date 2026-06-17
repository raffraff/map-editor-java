package com.rose.mapeditor.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.rose.mapeditor.RoseCoords;
import com.rose.mapeditor.model.Ptl;
import com.rose.mapeditor.scene.EffectParticleEmitter;
import com.rose.mapeditor.scene.MapScene;

/** Updates and renders simulated PTL particle emitters. */
public final class EffectRenderer implements Disposable {

    private static final float[] QUAD_VERTICES = {
        -0.5f, -0.5f, 0f, 0f, 0f,
         0.5f, -0.5f, 0f, 1f, 0f,
         0.5f,  0.5f, 0f, 1f, 1f,
        -0.5f,  0.5f, 0f, 0f, 1f
    };
    private static final short[] QUAD_INDICES = {0, 1, 2, 0, 2, 3};

    private final ShaderProgram shader;
    private final Mesh quad;
    private final TextureCache textures = new TextureCache();
    private final Vector3 gdxPos = new Vector3();
    private final Vector3 rosePos = new Vector3();
    private final Vector3 axisRight = new Vector3();
    private final Vector3 axisUp = new Vector3();
    private RenderOptions renderOptions = new RenderOptions();

    public EffectRenderer() {
        shader = new ShaderProgram(
            Gdx.files.internal("shaders/particle.vert"),
            Gdx.files.internal("shaders/particle.frag")
        );
        if (!shader.isCompiled()) {
            throw new IllegalStateException("Particle shader compile error: " + shader.getLog());
        }

        quad = new Mesh(true, 4, 6, new VertexAttributes(
            VertexAttribute.Position(),
            VertexAttribute.TexCoords(0)
        ));
        quad.setVertices(QUAD_VERTICES);
        quad.setIndices(QUAD_INDICES);
    }

    public void setRenderOptions(RenderOptions renderOptions) {
        this.renderOptions = renderOptions != null ? renderOptions : new RenderOptions();
    }

    public void update(MapScene scene, float deltaSeconds) {
        if (scene == null || !renderOptions.isEffectsEnabled()) {
            return;
        }
        for (EffectParticleEmitter emitter : scene.effectEmitters) {
            emitter.simulator.setRootWorld(emitter.rootWorld);
            emitter.simulator.update(deltaSeconds);
            emitter.simulator.restartIfFinished();
        }
    }

    public void render(MapScene scene, Matrix4 projView, EditorCamera camera) {
        if (scene == null || scene.effectEmitters.isEmpty() || !renderOptions.isEffectsEnabled() || camera == null) {
            return;
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);

        shader.bind();
        shader.setUniformi("u_texture", 0);
        shader.setUniformMatrix("u_projView", projView);

        int lastBlendSrc = -1;
        int lastBlendDst = -1;
        int lastBlendEq = -1;

        for (EffectParticleEmitter emitter : scene.effectEmitters) {
            if (emitter.texturePath == null || emitter.texturePath.isBlank()) {
                continue;
            }
            Texture texture = textures.get(emitter.texturePath);
            if (texture == null) {
                continue;
            }

            Ptl.Sequence sequence = emitter.simulator.sequence();
            int blendSrc = sequence.blendSrc;
            int blendDst = sequence.blendDst;
            int blendEq = sequence.blendOp;
            if (blendSrc != lastBlendSrc || blendDst != lastBlendDst || blendEq != lastBlendEq) {
                RoseBlend.applyParticleBlend(blendSrc, blendDst, blendEq);
                lastBlendSrc = blendSrc;
                lastBlendDst = blendDst;
                lastBlendEq = blendEq;
            }

            texture.bind(0);
            camera.particleBillboardAxes(sequence.alignType, axisRight, axisUp);

            for (PtlParticleSimulator.LiveParticle particle : emitter.simulator.particles()) {
                if (!particle.visible) {
                    continue;
                }

                emitter.worldPosition(particle.position, rosePos);
                gdxPos.set(RoseCoords.toGdx(rosePos));

                shader.setUniformf("u_worldPos", gdxPos.x, gdxPos.y, gdxPos.z);
                shader.setUniformf("u_camRight", axisRight.x, axisRight.y, axisRight.z);
                shader.setUniformf("u_camUp", axisUp.x, axisUp.y, axisUp.z);
                shader.setUniformf("u_size", particle.size.x, particle.size.y);
                shader.setUniformf("u_color", particle.color.r, particle.color.g, particle.color.b, particle.color.a);
                shader.setUniformf("u_rotation", particle.rotation * MathUtils.degreesToRadians);

                int frameIndex = Math.max(0, (int) Math.floor(particle.textureIndex));
                int cols = Math.max(1, particle.textureColumns);
                int rows = Math.max(1, particle.textureRows);
                int col = frameIndex % cols;
                int row = frameIndex / cols;
                if (row >= rows) {
                    row = rows - 1;
                    col = cols - 1;
                }
                float uvW = 1f / cols;
                float uvH = 1f / rows;
                float minU = col * uvW;
                float minV = row * uvH;
                shader.setUniformf("u_uvRect", minU, minV, minU + uvW, minV + uvH);

                quad.render(shader, GL20.GL_TRIANGLES);
            }
        }

        Gdx.gl.glDepthMask(true);
        RoseBlend.applyDefaultParticleBlend();
    }

    public void clearMapResources() {
        textures.clear();
    }

    @Override
    public void dispose() {
        quad.dispose();
        shader.dispose();
        textures.dispose();
    }
}
