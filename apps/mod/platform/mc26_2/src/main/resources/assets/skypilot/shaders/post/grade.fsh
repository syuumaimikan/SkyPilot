#version 330
uniform sampler2D InSampler;
in vec2 texCoord;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(std140) uniform GradeConfig { vec4 Tint; vec4 Settings; };
out vec4 fragColor;
void main() {
    vec4 source = texture(InSampler, texCoord);
    float luma = dot(source.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 color = mix(vec3(luma), source.rgb, Settings.x) * Tint.rgb;
    color = (color - 0.5) * Settings.y + 0.5;
    vec2 uv = texCoord * 2.0 - 1.0;
    color *= 1.0 - Settings.z * smoothstep(0.25, 1.4, dot(uv, uv));
    if (Settings.w > 0.5) {
        vec2 pixel = 1.0 / InSize;
        vec3 dx = texture(InSampler, texCoord + vec2(pixel.x, 0)).rgb - texture(InSampler, texCoord - vec2(pixel.x, 0)).rgb;
        vec3 dy = texture(InSampler, texCoord + vec2(0, pixel.y)).rgb - texture(InSampler, texCoord - vec2(0, pixel.y)).rgb;
        color = vec3(1.0 - clamp(length(dx) + length(dy), 0.0, 1.0));
    }
    fragColor = vec4(clamp(color, 0.0, 1.0), source.a);
}
