#version 330

// VBV Sun Rays: the only post effect in this client.
//
// Each pixel walks a few taps along the line towards the sun and keeps the taps that land on sky
// (the game renders with reversed-Z, so the far plane - the sky - reads exactly 0). The average
// coverage becomes a warm glow, with a soft saturated core around the sun.
//
// Cost: 6 / 10 / 14 depth taps plus one scene fetch, depending on the quality setting, and the
// whole pass returns before the loop when the sun is behind the camera, below the horizon, in
// rain or in a cave (the Java side puts the strength in the data texture).
//
// If the data texture was never uploaded the frame is passed through untouched.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D DataSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;
out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

bool onScreen(vec2 uv) {
    return uv.x > 0.0 && uv.x < 1.0 && uv.y > 0.0 && uv.y < 1.0;
}

// 1 where the depth buffer holds the far plane (sky), 0 on any real geometry.
float skyMask(vec2 uv) {
    if (!onScreen(uv)) {
        return 0.0;
    }
    return step(texture(DepthSampler, uv).r, 0.00002);
}

void main() {
    vec4 scene = texture(InSampler, texCoord);
    vec4 d7 = texelFetch(DataSampler, ivec2(7, 0), 0);
    if (d7.a < 0.5) {
        fragColor = scene;
        return;
    }

    vec4 d0 = texelFetch(DataSampler, ivec2(0, 0), 0);
    vec4 d1 = texelFetch(DataSampler, ivec2(1, 0), 0);
    vec4 d4 = texelFetch(DataSampler, ivec2(4, 0), 0);

    float strength = d0.a;
    vec3 sunView = d0.rgb * 2.0 - 1.0;
    // z points towards the viewer, so a negative z is in front of the camera.
    if (strength < 0.02 || sunView.z > -0.02) {
        fragColor = scene;
        return;
    }

    float tanHalf = max((floor(d4.r * 255.0 + 0.5) * 256.0 + floor(d4.g * 255.0 + 0.5)) / 65535.0 * 4.0, 0.05);
    float aspect = OutSize.x / max(OutSize.y, 1.0);
    vec2 sunUv = vec2(sunView.x / (tanHalf * aspect), sunView.y / tanHalf) / (-sunView.z) * 0.5 + 0.5;

    int quality = int(floor(d1.a * 3.0 + 0.5));
    int taps = quality <= 1 ? 6 : (quality == 2 ? 10 : 14);

    // Walk towards the sun, weighting the taps closer to the camera more heavily, and average the
    // sky coverage so the result does not get brighter just because the quality setting is higher.
    vec2 delta = (sunUv - texCoord) / float(taps) * 0.96;
    vec2 uv = texCoord;
    float rays = 0.0;
    float weight = 1.0;
    float total = 0.0;
    for (int i = 0; i < 14; i++) {
        if (i >= taps) {
            break;
        }
        uv += delta;
        rays += skyMask(uv) * weight;
        total += weight;
        weight *= 0.90;
    }
    rays = total > 0.0 ? rays / total : 0.0;

    // Distance from this pixel to the sun on screen, corrected for the aspect ratio.
    float dist = length((texCoord - sunUv) * vec2(aspect, 1.0));
    float halo = pow(max(0.0, 1.0 - dist / 0.5), 3.0);

    // Vibrant sunlight: a hot gold core shading into a deep orange edge, saturation pushed up.
    vec3 core = vec3(1.0, 0.90, 0.62);
    vec3 edge = vec3(1.0, 0.40, 0.10);
    vec3 rayColor = mix(core, edge, smoothstep(0.0, 0.65, dist));
    rayColor = mix(vec3(dot(rayColor, LUMA)), rayColor, 1.4);

    float amount = clamp(rays * (0.85 + 1.7 * halo), 0.0, 1.7) * strength * 0.9;
    vec3 add = clamp(rayColor * amount, 0.0, 1.0);

    // Screen blend: the glow brightens the frame without washing the shadows out to grey.
    vec3 col = 1.0 - (1.0 - scene.rgb) * (1.0 - add);
    fragColor = vec4(clamp(col, 0.0, 1.0), scene.a);
}
