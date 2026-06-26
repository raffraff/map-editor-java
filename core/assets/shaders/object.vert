#version 120

attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec2 a_texCoord0;
attribute vec2 a_texCoord1;
attribute float a_vertexAlpha;

uniform mat4 u_world;
uniform mat4 u_projView;

varying vec2 v_texCoord;
varying vec2 v_lightmap;
varying vec3 v_normal;
varying float v_vertexAlpha;

void main() {
    vec4 roseWorld = u_world * vec4(a_position, 1.0);

    vec3 gdxWorld = vec3(roseWorld.x, roseWorld.z, -roseWorld.y);
    gl_Position = u_projView * vec4(gdxWorld, 1.0);

    mat3 normalMatrix = mat3(u_world);
    vec3 roseNormal = normalMatrix * a_normal;
    v_normal = normalize(vec3(roseNormal.x, roseNormal.z, -roseNormal.y));

    v_texCoord = a_texCoord0;
    v_lightmap = a_texCoord1;
    v_vertexAlpha = a_vertexAlpha;
}
