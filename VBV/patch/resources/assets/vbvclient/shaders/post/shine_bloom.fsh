#version 330

uniform sampler2D SceneSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;
out vec4 fragColor;

vec3 extractHighlights(vec3 color) {
    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float contribution = max(luminance - 0.72, 0.0);
    return color * contribution * 2.8;
}

void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    vec2 texel = 1.0 / max(InSize, vec2(1.0));

    // A compact 3x3 bright-pass blur. This is a real screen-space bloom pass:
    // only highlights contribute, and neighbouring highlights spread softly.
    vec3 glow = extractHighlights(scene.rgb) * 0.24;
    glow += extractHighlights(texture(SceneSampler, texCoord + texel * vec2(-2.0, -2.0)).rgb) * 0.08;
    glow += extractHighlights(texture(SceneSampler, texCoord + texel * vec2( 0.0, -2.0)).rgb) * 0.12;
    glow += extractHighlights(texture(SceneSampler, texCoord + texel * vec2( 2.0, -2.0)).rgb) * 0.08;
    glow += extractHighlights(texture(SceneSampler, texCoord + texel * vec2(-2.0,  0.0)).rgb) * 0.12;
    glow += extractHighlights(texture(SceneSampler, texCoord + texel * vec2( 2.0,  0.0)).rgb) * 0.12;
    glow += extractHighlights(texture(SceneSampler, texCoord + texel * vec2(-2.0,  2.0)).rgb) * 0.08;
    glow += extractHighlights(texture(SceneSampler, texCoord + texel * vec2( 0.0,  2.0)).rgb) * 0.12;
    glow += extractHighlights(texture(SceneSampler, texCoord + texel * vec2( 2.0,  2.0)).rgb) * 0.08;

    fragColor = vec4(clamp(scene.rgb + glow * 0.22, 0.0, 1.0), scene.a);
}
