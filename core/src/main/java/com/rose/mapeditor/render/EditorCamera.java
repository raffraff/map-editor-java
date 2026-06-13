package com.rose.mapeditor.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.rose.mapeditor.RoseCoords;

/**
 * Free-fly camera for the Rose map editor.
 * WASD + Q/E move in look direction; movement stops when keys are released.
 */
public final class EditorCamera {

    private static final float MOVE_SPEED = 300f;
    private static final float FAST_MULTIPLIER = 3f;
    private static final float MOUSE_SENSITIVITY = 0.003f;

    private final Vector3 rosePosition = new Vector3(5650f, 5250f, 10f);
    private final Vector3 gdxPosition = new Vector3();

    private float pitch = 1.54f;
    private float yaw = -3.150001f;

    private final Vector3 forward = new Vector3();
    private final Vector3 right = new Vector3();
    private final Vector3 up = new Vector3(0, 1, 0);
    private final Vector3 moveDelta = new Vector3();

    private final PerspectiveCamera camera = new PerspectiveCamera();
    private int lastMouseX;
    private int lastMouseY;
    private boolean initialized;

    public EditorCamera() {
        camera.near = 1f;
        camera.far = 50000f;
        camera.fieldOfView = 45f;
        syncCamera();
    }

    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    public void update(float delta) {
        if (!initialized) {
            lastMouseX = Gdx.input.getX();
            lastMouseY = Gdx.input.getY();
            initialized = true;
        }

        if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
            int mouseX = Gdx.input.getX();
            int mouseY = Gdx.input.getY();
            yaw += (lastMouseX - mouseX) * MOUSE_SENSITIVITY;
            pitch += (lastMouseY - mouseY) * MOUSE_SENSITIVITY;
            pitch = MathUtils.clamp(pitch, -MathUtils.PI / 2f + 0.01f, MathUtils.PI / 2f - 0.01f);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        } else {
            lastMouseX = Gdx.input.getX();
            lastMouseY = Gdx.input.getY();
        }

        syncCamera();

        float speed = MOVE_SPEED * delta;
        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
            speed *= FAST_MULTIPLIER;
        }

        float forwardAmt = 0f;
        float strafeAmt = 0f;
        float liftAmt = 0f;

        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            forwardAmt += speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            forwardAmt -= speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            strafeAmt += speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            strafeAmt -= speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.E)) {
            liftAmt += speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.Q)) {
            liftAmt -= speed;
        }

        if (forwardAmt != 0f || strafeAmt != 0f || liftAmt != 0f) {
            moveDelta.setZero();
            moveDelta.add(forward.x * forwardAmt, forward.y * forwardAmt, forward.z * forwardAmt);
            moveDelta.add(right.x * strafeAmt, right.y * strafeAmt, right.z * strafeAmt);
            moveDelta.add(0f, liftAmt, 0f);
            gdxPosition.add(moveDelta);
            rosePosition.set(RoseCoords.toRose(gdxPosition));
            syncCamera();
        }
    }

    private void syncCamera() {
        gdxPosition.set(RoseCoords.toGdx(rosePosition));

        float cosYaw = MathUtils.cos(yaw);
        float sinYaw = MathUtils.sin(yaw);
        float cosPitch = MathUtils.cos(pitch);
        float sinPitch = MathUtils.sin(pitch);

        forward.set(cosPitch * sinYaw, sinPitch, cosPitch * cosYaw).nor();
        right.set(cosYaw, 0f, -sinYaw).nor();

        camera.position.set(gdxPosition);
        camera.direction.set(forward);
        camera.up.set(up);
        camera.update();
    }

    public Matrix4 combined() {
        return camera.combined;
    }

    /**
     * Sky mesh is ~100 units across, centered at the origin. Apply view rotation only;
     * translating by map coordinates first makes V rotate about world origin and pushes
     * the dome thousands of units away from the camera.
     */
    public Matrix4 skyCombined(Matrix4 out) {
        Matrix4 viewRot = new Matrix4(camera.view);
        viewRot.val[12] = 0f;
        viewRot.val[13] = 0f;
        viewRot.val[14] = 0f;
        out.set(camera.projection).mul(viewRot);
        return out;
    }

    public Vector3 position() {
        return rosePosition;
    }

    public Vector3 gdxPosition(Vector3 out) {
        return out.set(gdxPosition);
    }

    public Ray getPickRay(float screenX, float screenY) {
        return camera.getPickRay(screenX, screenY);
    }

    /** Projects a libGDX world point to screen pixels (origin bottom-left). */
    public Vector3 projectGdx(Vector3 gdxWorld, Vector3 out) {
        out.set(gdxWorld);
        camera.project(out);
        return out;
    }

    public void setPosition(Vector3 rosePosition) {
        this.rosePosition.set(rosePosition);
        syncCamera();
    }
}
