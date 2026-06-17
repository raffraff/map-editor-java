package com.rose.mapeditor.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.rose.mapeditor.scene.MeshInstance;

/** Maps Rose/Znzin blend enums from PTL/EFT files to OpenGL constants. */
public final class RoseBlend {

    private RoseBlend() {
    }

    public static int toGlFactor(int znzinFactor) {
        switch (znzinFactor) {
            case 1:
                return GL20.GL_ZERO;
            case 2:
                return GL20.GL_ONE;
            case 3:
                return GL20.GL_SRC_COLOR;
            case 4:
                return GL20.GL_ONE_MINUS_SRC_COLOR;
            case 5:
                return GL20.GL_SRC_ALPHA;
            case 6:
                return GL20.GL_ONE_MINUS_SRC_ALPHA;
            case 7:
                return GL20.GL_DST_ALPHA;
            case 8:
                return GL20.GL_ONE_MINUS_DST_ALPHA;
            case 9:
                return GL20.GL_DST_COLOR;
            case 10:
                return GL20.GL_ONE_MINUS_DST_COLOR;
            case 11:
                return GL20.GL_SRC_ALPHA_SATURATE;
            default:
                return GL20.GL_ZERO;
        }
    }

    public static int toGlEquation(int znzinOp) {
        switch (znzinOp) {
            case 2:
                return GL20.GL_FUNC_SUBTRACT;
            case 3:
                return 0x800B; // GL_FUNC_REVERSE_SUBTRACT
            case 1:
            default:
                return GL20.GL_FUNC_ADD;
        }
    }

    public static void applyParticleBlend(int blendSrc, int blendDst, int blendOp) {
        if (blendSrc <= 0 || blendDst <= 0) {
            applyDefaultParticleBlend();
            return;
        }
        Gdx.gl.glBlendFunc(toGlFactor(blendSrc), toGlFactor(blendDst));
        Gdx.gl.glBlendEquation(toGlEquation(blendOp));
    }

    /** Applies GL blend state for a mesh draw call. */
    public static void applyMeshBlend(MeshInstance instance) {
        if (instance.blendSrc > 0 && instance.blendDst > 0) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            applyParticleBlend(instance.blendSrc, instance.blendDst, instance.blendOp);
            Gdx.gl.glDepthMask(instance.depthWriteEnabled);
            return;
        }

        if (instance.alphaBlend) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
            Gdx.gl.glDepthMask(false);
            return;
        }

        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
        Gdx.gl.glDepthMask(instance.depthWriteEnabled);
    }

    /** Standard alpha-over draw when PTL omits usable blend factors. */
    public static void applyDefaultParticleBlend() {
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
    }
}
