#version 330

// VBV Shadows, pass 1: screen-space ambient occlusion plus sun contact shadows.
// Everything comes from the world depth buffer, so these are not real shadow maps: they only
// see what is on screen. Output: r = ambient light (1 = open), g = sun visibility (1 = lit).

uniform sampler2D InDepthSampler;
uniform sampler2D DataSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;
out vec4 fragColor;

const float NEAR = 0.05;

float gTanHalf;
float gAspect;
float gFar;

// The game uses a reversed-Z perspective: depth 1 at the near plane, 0 at the far plane.
// d = NEAR * (FAR / t - 1) / (FAR - NEAR)  =>  t = NEAR * FAR / (NEAR + d * (FAR - NEAR))
float sceneT(vec2 uv) {
    float d = texture(InDepthSampler, uv).r;
    return NEAR * gFar / (NEAR + d * (gFar - NEAR));
}

vec3 viewPos(vec2 uv, float t) {
    vec2 ndc = uv * 2.0 - 1.0;
    return vec3(ndc.x * gTanHalf * gAspect * t, ndc.y * gTanHalf * t, -t);
}

vec2 project(vec3 p) {
    float inv = 1.0 / max(-p.z, 0.0001);
    return vec2(p.x * inv / (gTanHalf * gAspect), p.y * inv / gTanHalf) * 0.5 + 0.5;
}

float interleavedNoise(vec2 fragCoord) {
    return fract(52.9829189 * fract(dot(fragCoord, vec2(0.06711056, 0.00583715))));
}

bool insideScreen(vec2 uv) {
    return uv.x > 0.0 && uv.x < 1.0 && uv.y > 0.0 && uv.y < 1.0;
}

vec3 faceNormal(vec2 uv, vec3 p, vec2 px) {
    vec2 uvL = uv - vec2(px.x, 0.0);
    vec2 uvR = uv + vec2(px.x, 0.0);
    vec2 uvD = uv - vec2(0.0, px.y);
    vec2 uvU = uv + vec2(0.0, px.y);
    vec3 pl = viewPos(uvL, sceneT(uvL));
    vec3 pr = viewPos(uvR, sceneT(uvR));
    vec3 pd = viewPos(uvD, sceneT(uvD));
    vec3 pu = viewPos(uvU, sceneT(uvU));
    // Take the neighbour on the same surface (smaller depth step) on each axis.
    vec3 dx = abs(p.z - pl.z) < abs(pr.z - p.z) ? p - pl : pr - p;
    vec3 dy = abs(p.z - pd.z) < abs(pu.z - p.z) ? p - pd : pu - p;
    vec3 c = cross(dx, dy);
    float len = length(c);
    if (len < 1.0e-8) {
        return vec3(0.0, 0.0, 1.0);
    }
    return c / len;
}

void main() {
    vec4 d0 = texelFetch(DataSampler, ivec2(0, 0), 0);
    vec4 d1 = texelFetch(DataSampler, ivec2(1, 0), 0);
    vec4 d3 = texelFetch(DataSampler, ivec2(3, 0), 0);
    vec4 d4 = texelFetch(DataSampler, ivec2(4, 0), 0);
    vec4 d7 = texelFetch(DataSampler, ivec2(7, 0), 0);

    // Half the field of view's tangent, 16 bits over 0..4.
    gTanHalf = max((floor(d4.r * 255.0 + 0.5) * 256.0 + floor(d4.g * 255.0 + 0.5)) / 65535.0 * 4.0, 0.05);
    gFar = floor(d1.g * 255.0 + 0.5) * 256.0 + floor(d1.b * 255.0 + 0.5);
    gAspect = OutSize.x / max(OutSize.y, 1.0);

    vec2 uv = texCoord;
    float t0 = sceneT(uv);
    // Sky and anything at the far plane stays untouched. Also leave the frame alone if the
    // data texture was never uploaded.
    if (d7.a < 0.5 || gFar < 8.0 || t0 > gFar * 0.97) {
        fragColor = vec4(1.0, 1.0, 0.0, 1.0);
        return;
    }

    int quality = int(floor(d1.a * 3.0 + 0.5));
    int aoSteps = quality <= 1 ? 6 : (quality == 2 ? 10 : 14);
    int shadowSteps = quality <= 1 ? 8 : (quality == 2 ? 12 : 18);

    vec2 px = 1.0 / OutSize;
    vec3 p = viewPos(uv, t0);
    vec3 n = faceNormal(uv, p, px);
    if (dot(n, -p) < 0.0) {
        n = -n;
    }
    float jitter = interleavedNoise(gl_FragCoord.xy);
    float distFade = 1.0 - smoothstep(40.0, 100.0, t0);

    // ---- ambient occlusion ----
    float radius = 1.0;                                   // blocks
    float focal = 0.5 * OutSize.y / gTanHalf;             // pixels per block at distance 1
    float radiusPx = clamp(radius * focal / t0, 3.0, 56.0);
    float occlusion = 0.0;
    for (int i = 0; i < 14; i++) {
        if (i >= aoSteps) {
            break;
        }
        float angle = (float(i) + jitter) * 2.39996323;
        float r = sqrt((float(i) + 0.5) / float(aoSteps));
        vec2 suv = uv + vec2(cos(angle), sin(angle)) * (r * radiusPx) * px;
        if (!insideScreen(suv)) {
            continue;
        }
        vec3 s = viewPos(suv, sceneT(suv));
        vec3 v = s - p;
        float dist = length(v);
        float facing = max(dot(n, v) / max(dist, 0.0001) - 0.15, 0.0);
        float range = 1.0 - smoothstep(radius * 0.7, radius * 1.8, dist);
        occlusion += facing * range;
    }
    float ao = clamp(1.0 - occlusion * 1.9 / float(aoSteps), 0.0, 1.0);
    ao = mix(1.0, ao, d3.r * distFade);

    // ---- sun contact shadows ----
    float sunStrength = d0.a;
    float visibility = 1.0;
    if (sunStrength > 0.01 && d3.g > 0.01) {
        vec3 l = normalize(d0.rgb * 2.0 - 1.0);
        float ndl = dot(n, l);
        float backFace = 1.0 - smoothstep(-0.05, 0.2, ndl);
        float blocked = 0.0;
        if (backFace < 0.999) {
            float maxLen = 5.0 + 2.0 * float(quality);
            vec3 origin = p + n * (0.03 + 0.002 * t0);
            float minDiff = 0.04 + 0.004 * t0;
            for (int i = 0; i < 18; i++) {
                if (i >= shadowSteps) {
                    break;
                }
                float f = (float(i) + jitter) / float(shadowSteps);
                float s = maxLen * f * f + 0.06;
                vec3 q = origin + l * s;
                if (q.z > -NEAR) {
                    break;
                }
                vec2 quv = project(q);
                if (!insideScreen(quv)) {
                    break;
                }
                float diff = -q.z - sceneT(quv);
                float thickness = 0.3 + s * 0.4;
                if (diff > minDiff && diff < thickness) {
                    blocked = 1.0;
                    break;
                }
            }
        }
        float darkness = clamp(blocked * (1.0 - backFace) + backFace * 0.4, 0.0, 1.0);
        visibility = 1.0 - darkness * sunStrength * d3.g * distFade;
    }

    fragColor = vec4(ao, visibility, 0.0, 1.0);
}
