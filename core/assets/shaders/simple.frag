#version 120

varying vec2 v_texCoord;

uniform sampler2D u_texture;
uniform float u_alpha;
uniform vec3 u_addition;

void main() {
    vec4 colour = texture2D(u_texture, v_texCoord);
    colour.rgb += u_addition;
    colour.a = u_alpha;
    gl_FragColor = colour;
}
