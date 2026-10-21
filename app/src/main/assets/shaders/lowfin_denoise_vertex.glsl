attribute vec4 aPosition;
varying highp vec2 vTex;
void main() {
    gl_Position = aPosition;
    vTex = aPosition.xy * 0.5 + 0.5;
}
