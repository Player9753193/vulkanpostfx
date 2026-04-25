#version 330

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

out vec2 texCoord0;

void main() {
/*
     * 强判别版：
     * 完全绕开 Projection / ChunkSection / ChunkPosition / shadow matrices。
     *
     * 目标不是正确阴影，而是确认：
     * 这条 custom terrain pipeline 是否能真正产生片元并写入 depth attachment。
     *
     * 做法：
     * - 只用本地顶点 Position
     * - 映射到一个固定的屏幕内区域
     * - Z 固定在 0
     *
     * 如果这样右半边仍然纯红，
     * 那问题就已经不在矩阵/UBO，而在更底层的 depth pass 语义。
     */
    vec2 p = fract(Position.xz * 0.0625) * 2.0 - 1.0;
    p *= 0.8;

    gl_Position = vec4(p, 0.0, 1.0);
    texCoord0 = UV0;
}