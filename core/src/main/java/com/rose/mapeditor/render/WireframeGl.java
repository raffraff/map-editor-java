package com.rose.mapeditor.render;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;

import java.lang.reflect.Method;

/** Toggles desktop OpenGL wireframe polygon mode when available. */
public final class WireframeGl {

    private static final int GL_FRONT_AND_BACK = 0x0408;
    private static final int GL_LINE = 0x1B01;
    private static final int GL_FILL = 0x1B02;

    private static Method polygonMode;
    private static Method lineWidth;
    private static boolean unsupported;

    private WireframeGl() {
    }

    public static void enable() {
        invokePolygonMode(GL_LINE);
        invokeLineWidth(1.5f);
    }

    public static void disable() {
        invokePolygonMode(GL_FILL);
    }

    private static void invokePolygonMode(int mode) {
        Method method = polygonModeMethod();
        if (method == null) {
            return;
        }
        try {
            method.invoke(null, GL_FRONT_AND_BACK, mode);
        } catch (ReflectiveOperationException e) {
            unsupported = true;
            Gdx.app.debug("WireframeGl", "Wireframe not available", e);
        }
    }

    private static void invokeLineWidth(float width) {
        Method method = lineWidthMethod();
        if (method == null) {
            return;
        }
        try {
            method.invoke(null, width);
        } catch (ReflectiveOperationException ignored) {
            // Some drivers ignore line width; wireframe still works at 1px.
        }
    }

    private static Method polygonModeMethod() {
        if (unsupported) {
            return null;
        }
        if (polygonMode != null) {
            return polygonMode;
        }
        if (Gdx.app.getType() != Application.ApplicationType.Desktop) {
            unsupported = true;
            return null;
        }
        try {
            Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11");
            polygonMode = gl11.getMethod("glPolygonMode", int.class, int.class);
            return polygonMode;
        } catch (ReflectiveOperationException e) {
            unsupported = true;
            return null;
        }
    }

    private static Method lineWidthMethod() {
        if (unsupported) {
            return null;
        }
        if (lineWidth != null) {
            return lineWidth;
        }
        try {
            Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11");
            lineWidth = gl11.getMethod("glLineWidth", float.class);
            return lineWidth;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
