precision mediump float;
uniform sampler2D uTexture;
uniform highp vec2 uStep;
uniform float uStrength;
varying highp vec2 vTex;
// A light spatial filter: nearby, similar colors contribute; edges keep their contrast.
// No frame history, CPU readback, resizing or timestamp changes.
float weight(vec3 color, vec3 center) {
    vec3 d = color - center;
    return max(0.0, 1.0 - dot(d, d) * 160.0);
}
void main() {
    vec4 center = texture2D(uTexture, vTex);
    vec3 left = texture2D(uTexture, clamp(vTex - vec2(uStep.x, 0.0), 0.0, 1.0)).rgb;
    vec3 right = texture2D(uTexture, clamp(vTex + vec2(uStep.x, 0.0), 0.0, 1.0)).rgb;
    vec3 up = texture2D(uTexture, clamp(vTex + vec2(0.0, uStep.y), 0.0, 1.0)).rgb;
    vec3 down = texture2D(uTexture, clamp(vTex - vec2(0.0, uStep.y), 0.0, 1.0)).rgb;
    float l = weight(left, center.rgb), r = weight(right, center.rgb);
    float u = weight(up, center.rgb), d = weight(down, center.rgb);
    vec3 filtered = (center.rgb * 2.0 + left*l + right*r + up*u + down*d) / (2.0+l+r+u+d);
    gl_FragColor = vec4(mix(center.rgb, filtered, uStrength), center.a);
}
