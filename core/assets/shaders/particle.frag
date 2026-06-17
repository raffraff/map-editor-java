#version 120

varying vec2 v_texCoord;

uniform sampler2D u_texture;
uniform vec4 u_color;

void main() {
    vec4 tex = texture2D(u_texture, v_texCoord);
    gl_FragColor = tex * u_color;
    if (gl_FragColor.a < 0.01) {
        discard;
    }
}
