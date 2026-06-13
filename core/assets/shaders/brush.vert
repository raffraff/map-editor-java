#version 120

attribute vec3 a_position;
attribute vec4 a_color;

uniform mat4 u_projViewMatrix;

varying vec4 v_color;

void main() {
    gl_Position = u_projViewMatrix * vec4(a_position, 1.0);
    v_color = a_color;
}
