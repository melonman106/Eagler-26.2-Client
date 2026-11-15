#version 330

// VBV Vibrant / Shadows, final pass: applies the shadow buffer (Shadows preset only), then
// vibrance, an S-curve for contrast, a little warmth, a soft glow on bright areas and a vignette.
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

vec3 highlights(vec3 color) {
    float l = dot(color, LUMA);
    return color * max(l - 0.72, 0.0) * 2.6;
}

void main() {
    vec4 scene = texture(InSampler, texCoord);
    vec4 d7 = texelFetch(DataSampler, ivec2(7, 0), 0);
    if (d7.a < 0.5) {
        fragColor = scene;
        return;
    }
    vec4 d2 = texelFetch(DataSampler, ivec2(2, 0), 0);
    vec4 d3 = texelFetch(DataSampler, ivec2(3, 0), 0);

    float saturation = d2.r * 2.0;
    float contrast = d2.g;
    float vignette = d2.b;
    float bloom = d2.a;
    float warmth = (d3.a - 0.5) * 0.5;

    vec3 col = scene.rgb;

    if (d3.b > 0.5) {
        vec2 shade = texture(ShadeSampler, texCoord).rg;
        col *= shade.r;
        col *= mix(vec3(0.66, 0.74, 0.95), vec3(1.0), shade.g);
    }

    if (bloom > 0.001) {
        vec2 texel = 1.0 / max(InSize, vec2(1.0));
        vec3 glow = highlights(col) * 0.24;
        glow += highlights(texture(InSampler, texCoord + texel * vec2(-3.0, -3.0)).rgb) * 0.08;
        glow += highlights(texture(InSampler, texCoord + texel * vec2( 0.0, -3.0)).rgb) * 0.12;
        glow += highlights(texture(InSampler, texCoord + texel * vec2( 3.0, -3.0)).rgb) * 0.08;
        glow += highlights(texture(InSampler, texCoord + texel * vec2(-3.0,  0.0)).rgb) * 0.12;
        glow += highlights(texture(InSampler, texCoord + texel * vec2( 3.0,  0.0)).rgb) * 0.12;
        glow += highlights(texture(InSampler, texCoord + texel * vec2(-3.0,  3.0)).rgb) * 0.08;
        glow += highlights(texture(InSampler, texCoord + texel * vec2( 0.0,  3.0)).rgb) * 0.12;
        glow += highlights(texture(InSampler, texCoord + texel * vec2( 3.0,  3.0)).rgb) * 0.08;
        col += glow * bloom;
    }

    col = clamp(col, 0.0, 1.0);

    // Vibrance: muted colours get more of the boost than ones that are already vivid.
    float luma = dot(col, LUMA);
    float chroma = max(col.r, max(col.g, col.b)) - min(col.r, min(col.g, col.b));
    float boost = 1.0 + (saturation - 1.0) * (1.0 - chroma * 0.5);
    col = mix(vec3(luma), col, boost);
    col = clamp(col, 0.0, 1.0);

    // Contrast: blend toward a smoothstep S-curve so highlights and shadows are not clipped.
    col = mix(col, col * col * (3.0 - 2.0 * col), contrast);

    col *= vec3(1.0 + warmth, 1.0 + warmth * 0.35, 1.0 - warmth);

    vec2 q = (texCoord - 0.5) * vec2(OutSize.x / max(OutSize.y, 1.0), 1.0);
    col *= 1.0 - vignette * smoothstep(0.45, 1.05, length(q));

    fragColor = vec4(clamp(col, 0.0, 1.0), scene.a);
}
