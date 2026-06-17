#version 120

attribute vec3 a_position;
attribute vec2 a_texCoord0;

uniform mat4 u_projView;
uniform vec3 u_worldPos;
uniform vec3 u_camRight;
uniform vec3 u_camUp;
uniform vec2 u_size;
uniform vec4 u_uvRect;
uniform float u_rotation;

varying vec2 v_texCoord;

void main() {
    float cosR = cos(u_rotation);
    float sinR = sin(u_rotation);
    vec2 local = a_position.xy;
    vec2 rotated = vec2(
        cosR * local.x - sinR * local.y,
        sinR * local.x + cosR * local.y
    );

    vec3 corner = u_worldPos
        + u_camRight * (rotated.x * u_size.x)
        + u_camUp * (rotated.y * u_size.y);
    gl_Position = u_projView * vec4(corner, 1.0);

    if (a_position.x < 0.0) {
        v_texCoord.x = u_uvRect.x;
    } else {
        v_texCoord.x = u_uvRect.z;
    }
    if (a_position.y > 0.0) {
        v_texCoord.y = u_uvRect.y;
    } else {
        v_texCoord.y = u_uvRect.w;
    }
}
