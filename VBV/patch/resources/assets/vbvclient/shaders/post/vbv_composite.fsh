#version 330

// VBV Vibrant / Shadows, final pass: shadow buffer (Shadows preset only), then a cartoon look -
// strong vibrance, a toon luminance band, a small contrast curve, a little warmth and a vignette.
//
// Cost note: this pass does one scene fetch, the data lookups and (only at High quality) four
// neighbour taps for a cheap outline. The previous version added nine taps of bloom per pixel,
// which was most of the cost of the whole shader on integrated graphics; the band gives a similar
// "glowing, painted" read from the colour curve alone.
//
// If the data texture was never uploaded the frame is passed through untouched.

uniform sampler2D InSampler;
uniform sampler2D ShadeSampler;
uniform sampler2D DataSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;
out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
const float BANDS = 6.0;

// Quantises brightness into a few soft-edged levels, like cel shading, while keeping the hue.
vec3 toonify(vec3 color, float amount) {
    float l = dot(color, LUMA);
    if (l < 0.002) {
        return color;
    }
    float scaled = l * BANDS;
    float band = (floor(scaled) + smoothstep(0.34, 0.66, fract(scaled))) / BANDS;
    float gain = mix(1.0, band / l, amount);
    return clamp(color * gain, 0.0, 1.0);
}

void main() {
    vec4 scene = texture(InSampler, texCoord);
    vec4 d7 = texelFetch(DataSampler, ivec2(7, 0), 0);
    if (d7.a < 0.5) {
        fragColor = scene;
        return;
    }
    vec4 d1 = texelFetch(DataSampler, ivec2(1, 0), 0);
    vec4 d2 = texelFetch(DataSampler, ivec2(2, 0), 0);
    vec4 d3 = texelFetch(DataSampler, ivec2(3, 0), 0);

    // d1.a is the quality level as a 0..1 fraction; d2 holds saturation, contrast, vignette, toon.
    float quality = d1.a;
    float saturation = d2.r * 2.0;
    float contrast = d2.g;
    float vignette = d2.b;
    float toon = d2.a;
    float warmth = (d3.a - 0.5) * 0.5;

    vec3 col = scene.rgb;

    if (d3.b > 0.5) {
        vec2 shade = texture(ShadeSampler, texCoord).rg;
        col *= shade.r;
        col *= mix(vec3(0.66, 0.74, 0.95), vec3(1.0), shade.g);
    }

    // Saturate first so the bands are clearly coloured instead of grey steps.
    float luma = dot(col, LUMA);
    col = mix(vec3(luma), col, saturation);
    col = toonify(col, toon);

    // Cartoon outline: four taps, High quality only, so the lower levels stay nearly free.
    if (quality > 0.9) {
        vec2 texel = 1.0 / max(InSize, vec2(1.0));
        // The neighbours below are raw scene pixels, so the centre has to be one too. Comparing the
        // shaded / banded colour against them drew an outline over every shadow and every toon step.
        float centre = dot(scene.rgb, LUMA);
        float east = dot(texture(InSampler, texCoord + vec2(texel.x, 0.0)).rgb, LUMA);
        float west = dot(texture(InSampler, texCoord - vec2(texel.x, 0.0)).rgb, LUMA);
        float north = dot(texture(InSampler, texCoord + vec2(0.0, texel.y)).rgb, LUMA);
        float south = dot(texture(InSampler, texCoord - vec2(0.0, texel.y)).rgb, LUMA);
        float edge = max(max(abs(east - centre), abs(west - centre)),
                         max(abs(north - centre), abs(south - centre)));
        col *= 1.0 - smoothstep(0.10, 0.32, edge) * 0.45;
    }

    // Contrast: blend toward a smoothstep S-curve so highlights and shadows are not clipped.
    col = mix(col, col * col * (3.0 - 2.0 * col), contrast);

    col *= vec3(1.0 + warmth, 1.0 + warmth * 0.35, 1.0 - warmth);

    vec2 q = (texCoord - 0.5) * vec2(OutSize.x / max(OutSize.y, 1.0), 1.0);
    col *= 1.0 - vignette * smoothstep(0.45, 1.05, length(q));

    fragColor = vec4(clamp(col, 0.0, 1.0), scene.a);
}
