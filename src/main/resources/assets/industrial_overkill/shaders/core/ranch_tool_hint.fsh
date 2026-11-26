#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 itemUv;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, itemUv) * vertexColor;
    if (color.a < 0.1) discard;
    float gray = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    fragColor = vec4(vec3(gray), color.a) * ColorModulator;
}
