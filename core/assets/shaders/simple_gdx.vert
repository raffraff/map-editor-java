#version 120

attribute vec3 a_position;
attribute vec2 a_texCoord0;

uniform mat4 u_projView;

varying vec2 v_texCoord;

void main() {
    gl_Position = u_projView * vec4(a_position, 1.0);
    v_texCoord = a_texCoord0;
}
