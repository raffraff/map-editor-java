#version 120

varying vec2 v_texCoord;
varying vec2 v_lightmap;
varying vec3 v_normal;
varying float v_vertexAlpha;

uniform sampler2D u_texture;
uniform sampler2D u_lightmap;

uniform int u_masterLightmapEnabled;
uniform int u_lightmapEnabled;
uniform int u_softDiffuseEnabled;
uniform int u_texturesEnabled;
uniform int u_alphaTestEnabled;
uniform float u_alpha;
uniform float u_alphaReference;
uniform vec3 u_lightDir;
uniform float u_softDiffuseMin;
uniform float u_softDiffuseMax;

uniform vec2 u_textureAdd;
uniform vec2 u_textureMultiply;

void main() {
    vec4 finalColour;
    if (u_texturesEnabled == 0) {
        float ndl = max(dot(normalize(v_normal), normalize(u_lightDir)), 0.0);
        finalColour.rgb = vec3(0.72, 0.74, 0.78) * mix(0.45, 1.0, ndl);
        finalColour.a = u_alpha * v_vertexAlpha;
    } else {
        finalColour = texture2D(u_texture, v_texCoord);
        finalColour.a *= u_alpha;
    }

    bool hasLightmap = u_masterLightmapEnabled != 0 && u_lightmapEnabled != 0;
    if (hasLightmap) {
        vec2 lm = (v_lightmap + u_textureAdd) * u_textureMultiply;
        finalColour.rgb *= 2.0 * texture2D(u_lightmap, lm).rgb;
    }

    if (u_softDiffuseEnabled != 0 && !hasLightmap && u_texturesEnabled != 0) {
        float ndl = max(dot(normalize(v_normal), normalize(u_lightDir)), 0.0);
        finalColour.rgb *= mix(u_softDiffuseMin, u_softDiffuseMax, ndl);
    }

    if (u_texturesEnabled == 0) {
        if (finalColour.a < 0.01) {
            discard;
        }
    } else if (u_alphaTestEnabled != 0) {
        if (finalColour.a * 255.0 < u_alphaReference) {
            discard;
        }
    } else if (finalColour.a < 0.01) {
        discard;
    }

    if (u_texturesEnabled != 0) {
        finalColour.a *= v_vertexAlpha;
    }

    gl_FragColor = finalColour;
}
