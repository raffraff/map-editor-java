#version 120

attribute vec3 a_position;
attribute vec2 a_texCoord0;

uniform mat4 u_projView;

varying vec2 v_texCoord;

void main() {
    vec3 gdxPos = vec3(a_position.x, a_position.z, -a_position.y);
    vec4 clipPos = u_projView * vec4(gdxPos, 1.0);
    // Draw at far plane so the dome meets the horizon behind terrain
    gl_Position = clipPos.xyww;
    v_texCoord = a_texCoord0;
}
