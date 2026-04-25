#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    #ifdef ALPHA_CUTOUT
    vec4 albedo = texture(Sampler0, texCoord0);
    if (albedo.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif


/*
     * 强制写一个极近深度。
     *
     * 目的不是正确阴影，而是判别：
     * - 片元是否真的到达 fragment stage
     * - depth attachment 是否真的接受来自这条 pipeline 的写入
     */
    gl_FragDepth = 0.0;

/*
     * 颜色无关紧要，只是满足 color attachment 存在时的输出。
     */
    fragColor = vec4(1.0);
}