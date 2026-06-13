package com.rose.mapeditor;

import com.badlogic.gdx.math.Vector2;
import com.rose.mapeditor.map.Zone;

/** Terrain texture coordinate helpers */
public final class TerrainUtil {

    private TerrainUtil() {
    }

    public static Vector2 rotationToCoordinates(Zone.RotationType rotation, Vector2 texCoord) {
        Vector2 result = new Vector2(texCoord);
        switch (rotation) {
            case LEFT_RIGHT:
                result.x = 1.0f - result.x;
                break;
            case TOP_BOTTOM:
                result.y = 1.0f - result.y;
                break;
            case LEFT_RIGHT_TOP_BOTTOM:
                result.x = 1.0f - result.x;
                result.y = 1.0f - result.y;
                break;
            case ROTATE_90_CLOCKWISE: {
                float temp = result.x;
                result.x = result.y;
                result.y = 1.0f - temp;
                break;
            }
            case ROTATE_90_COUNTER_CLOCKWISE: {
                float temp = result.x;
                result.x = result.y;
                result.y = temp;
                break;
            }
            default:
                break;
        }
        return result;
    }
}
