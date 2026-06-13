#version 120

varying vec2 v_texBottom;
varying vec2 v_texTop;
varying vec2 v_texShadow;

uniform sampler2D u_bottomTexture;
uniform sampler2D u_topTexture;
uniform sampler2D u_shadowTexture;
uniform int u_gridOutlineEnabled;
uniform int u_lightmapEnabled;
uniform int u_texturesEnabled;

void main() {
    vec4 finalColour;
    if (u_texturesEnabled == 0) {
        finalColour = vec4(0.52, 0.48, 0.40, 1.0);
    } else {
        vec4 bottom = texture2D(u_bottomTexture, v_texBottom);
        bottom.a = 1.0;

        vec4 top = texture2D(u_topTexture, v_texTop);
        float alphaAmount = top.a;
        top.a = 1.0;

        finalColour = mix(bottom, top, alphaAmount);
    }

    if (u_gridOutlineEnabled != 0 &&
        (v_texShadow.x < 0.001 || v_texShadow.y < 0.001 ||
         v_texShadow.x > 0.999 || v_texShadow.y > 0.999)) {
        finalColour.rg *= 1.5;
    }

    if (u_lightmapEnabled != 0) {
        finalColour.rgb *= texture2D(u_shadowTexture, v_texShadow).rgb * 2.0;
    }

    gl_FragColor = finalColour;
}
