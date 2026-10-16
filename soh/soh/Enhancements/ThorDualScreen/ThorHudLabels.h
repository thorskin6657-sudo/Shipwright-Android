#pragma once
// Code-drawn IA8 controller glyphs: no replacement game/texture-pack assets.
#ifdef __ANDROID__
static u8 sThorHudLabels[3][32 * 32] __attribute__((aligned(8)));
static int sThorHudLabelsReady;

static void Thor_InitHudLabels(void) {
    if (sThorHudLabelsReady) return;
    static const u8 glyphs[4][7] = {
        {17, 17, 10, 4, 10, 17, 17}, // X
        {17, 17, 10, 4, 4, 4, 4},   // Y
        {30, 17, 17, 30, 20, 18, 17}, // R
        {30, 17, 17, 30, 17, 17, 30}  // B
    };
    for (int label = 0; label < 3; ++label) {
        const int scale = label == 2 ? 2 : 3;
        const int count = label == 2 ? 2 : 1;
        const int width = (count * 6 - 1) * scale;
        const int left = (32 - width) / 2;
        const int top = (32 - 7 * scale) / 2;
        for (int y = top - 2; y < top + 7 * scale + 2; ++y)
            for (int x = left - 2; x < left + width + 2; ++x)
                sThorHudLabels[label][y * 32 + x] = 0x0D;
        for (int letter = 0; letter < count; ++letter) {
            const int glyph = label == 2 ? letter + 2 : label;
            for (int y = 0; y < 7; ++y)
                for (int x = 0; x < 5; ++x)
                    if (glyphs[glyph][y] & (1 << (4 - x)))
                        for (int sy = 0; sy < scale; ++sy)
                            for (int sx = 0; sx < scale; ++sx)
                                sThorHudLabels[label][(top + y * scale + sy) * 32 +
                                    left + (letter * 6 + x) * scale + sx] = 0xFF;
        }
    }
    sThorHudLabelsReady = 1;
}

static Gfx* Thor_DrawHudLabel(Gfx* display, int label, int x, int y, int size, int alpha) {
    Thor_InitHudLabels();
    gDPPipeSync(display++);
    gDPSetPrimColor(display++, 0, 0, 246, 212, 60, alpha);
    gDPSetEnvColor(display++, 0, 0, 0, 0);
    gDPSetCombineMode(display++, G_CC_MODULATEIA_PRIM, G_CC_MODULATEIA_PRIM);
    return Gfx_TextureIA8(display, sThorHudLabels[label], 32, 32, x, y,
                          size, size, (32 << 10) / size, (32 << 10) / size);
}
#endif
