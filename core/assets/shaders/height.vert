#version 120



attribute vec3 a_position;

attribute vec2 a_texCoord0;

attribute vec2 a_texCoord1;

attribute vec2 a_texCoord2;



uniform mat4 u_projViewMatrix;



varying vec2 v_texBottom;

varying vec2 v_texTop;

varying vec2 v_texShadow;



void main() {

    gl_Position = u_projViewMatrix * vec4(a_position, 1.0);

    v_texBottom = a_texCoord0;

    v_texTop = a_texCoord1;

    v_texShadow = a_texCoord2;

}

