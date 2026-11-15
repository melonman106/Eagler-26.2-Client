#version 330

// VBV Shadows, pass 2: depth-aware blur of the occlusion/shadow buffer so the sampling noise
// of pass 1 disappears without smearing light across edges.

uniform sampler2D ShadeSampler;
uniform sampler2D InDepthSampler;
uniform sampler2D DataSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 ShadeSize;
};

in vec2 texCoord;
out vec4 fragColor;

const float NEAR = 0.05;

float sceneT(vec2 uv, float farPlane) {
    float d = texture(InDepthSampler, uv).r;
    return NEAR * farPlane / (NEAR + d * (farPlane - NEAR));
}

void main() {
    vec4 d1 = texelFetch(DataSampler, ivec2(1, 0), 0);
    float farPlane = floor(d1.g * 255.0 + 0.5) * 256.0 + floor(d1.b * 255.0 + 0.5);
    farPlane = max(farPlane, 8.0);

    vec2 px = 1.0 / OutSize;
    float tc = sceneT(texCoord, farPlane);
    vec2 sum = vec2(0.0);
    float weightSum = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 uv = texCoord + vec2(float(x), float(y)) * 1.5 * px;
            float ts = sceneT(uv, farPlane);
            float w = exp(-abs(ts - tc) / (0.03 * tc + 0.15));
            sum += texture(ShadeSampler, uv).rg * w;
            weightSum += w;
        }
    }
    fragColor = vec4(sum / max(weightSum, 0.0001), 0.0, 1.0);
}
