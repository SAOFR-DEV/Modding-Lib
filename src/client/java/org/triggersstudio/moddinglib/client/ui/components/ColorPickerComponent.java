package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.triggersstudio.moddinglib.client.ui.context.UIContext;
import org.triggersstudio.moddinglib.client.ui.state.State;
import org.triggersstudio.moddinglib.client.ui.styling.Style;

import java.awt.Color;

/**
 * HSV color picker bound to a {@code State<Integer>} ARGB value.
 *
 * <p>Layout (top → bottom):
 * <ul>
 *   <li>Saturation/Value pad (square): drag to set S and V at once.</li>
 *   <li>Hue slider (horizontal): drag to set H.</li>
 *   <li>Alpha slider (horizontal, optional): drag to set the alpha channel.</li>
 *   <li>Preview swatch + hex readout.</li>
 * </ul>
 *
 * <p>Internal HSV is the source of truth so that hue/saturation are preserved
 * when the user drags V to 0 (black) or S to 0 (white) — converting back from
 * RGB would lose this information. The state is updated on every change and
 * an {@code ignoreNextSync} flag prevents the resulting onChange notification
 * from clobbering the in-flight HSV.
 */
public class ColorPickerComponent extends UIComponent {

    private static final int DEFAULT_PAD_SIZE = 140;
    private static final int SLIDER_HEIGHT = 14;
    private static final int GAP = 6;
    private static final int PREVIEW_HEIGHT = 22;
    private static final int CHECKER_SIZE = 6;

    private final State<Integer> state;
    private final int padSize;
    private final boolean withAlpha;

    private float h = 0f, s = 1f, v = 1f;
    private int alpha = 255;
    private boolean ignoreNextSync = false;

    private boolean draggingSV = false, draggingH = false, draggingA = false;

    public ColorPickerComponent(State<Integer> state, int padSize, boolean withAlpha, Style style) {
        super(style);
        if (state == null) throw new IllegalArgumentException("ColorPicker requires a non-null State");
        this.state = state;
        this.padSize = padSize > 0 ? padSize : DEFAULT_PAD_SIZE;
        this.withAlpha = withAlpha;
        syncFromState(state.get());
    }

    @Override
    public void onAttach(UIContext ctx) {
        super.onAttach(ctx);
        track(state.onChange(this::syncFromState));
    }

    private void syncFromState(Integer argb) {
        if (ignoreNextSync) {
            ignoreNextSync = false;
            return;
        }
        if (argb == null) return;
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float[] hsb = new float[3];
        Color.RGBtoHSB(r, g, b, hsb);
        // Preserve hue when saturation/value collapse to 0 (otherwise HSB lib resets to 0).
        if (hsb[1] > 0f) this.h = hsb[0];
        if (hsb[2] > 0f) this.s = hsb[1];
        this.v = hsb[2];
        this.alpha = a;
    }

    private void pushToState() {
        int rgb = Color.HSBtoRGB(h, s, v) & 0x00_FF_FF_FF;
        int argb = (alpha << 24) | rgb;
        ignoreNextSync = true;
        state.set(argb);
    }

    @Override
    public MeasureResult measure(int maxWidth, int maxHeight) {
        int sliders = SLIDER_HEIGHT + (withAlpha ? GAP + SLIDER_HEIGHT : 0);
        int total = padSize + GAP + sliders + GAP + PREVIEW_HEIGHT;
        return new MeasureResult(padSize, total);
    }

    @Override
    public void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public void render(DrawContext ctx) {
        int padX = x;
        int padY = y;
        renderSVPad(ctx, padX, padY, padSize, padSize);

        int hueY = padY + padSize + GAP;
        renderHueSlider(ctx, padX, hueY, padSize, SLIDER_HEIGHT);

        int alphaY = hueY + SLIDER_HEIGHT + GAP;
        if (withAlpha) {
            renderAlphaSlider(ctx, padX, alphaY, padSize, SLIDER_HEIGHT);
        }

        int previewY = (withAlpha ? alphaY + SLIDER_HEIGHT : hueY + SLIDER_HEIGHT) + GAP;
        renderPreview(ctx, padX, previewY, padSize, PREVIEW_HEIGHT);
    }

    // ===== Render helpers =====

    private void renderSVPad(DrawContext ctx, int px, int py, int pw, int ph) {
        int hueRgb = 0xFF_00_00_00 | (Color.HSBtoRGB(h, 1f, 1f) & 0x00_FF_FF_FF);
        // Horizontal white → hue: per-column fill.
        for (int col = 0; col < pw; col++) {
            float t = (pw == 1) ? 0f : col / (float) (pw - 1);
            int color = lerp(0xFF_FF_FF_FF, hueRgb, t);
            ctx.fill(px + col, py, px + col + 1, py + ph, color);
        }
        // Vertical transparent → black overlay.
        ctx.fillGradient(px, py, px + pw, py + ph, 0x00_00_00_00, 0xFF_00_00_00);

        // 1px frame.
        drawFrame(ctx, px, py, pw, ph, 0xFF_00_00_00);

        // SV cursor: small ring (black outer, white inner).
        int cx = px + Math.round(s * (pw - 1));
        int cy = py + Math.round((1f - v) * (ph - 1));
        drawRingMarker(ctx, cx, cy);
    }

    private void renderHueSlider(DrawContext ctx, int sx, int sy, int sw, int sh) {
        for (int col = 0; col < sw; col++) {
            float t = (sw == 1) ? 0f : col / (float) (sw - 1);
            int color = 0xFF_00_00_00 | (Color.HSBtoRGB(t, 1f, 1f) & 0x00_FF_FF_FF);
            ctx.fill(sx + col, sy, sx + col + 1, sy + sh, color);
        }
        drawFrame(ctx, sx, sy, sw, sh, 0xFF_00_00_00);
        int cx = sx + Math.round(h * (sw - 1));
        drawSliderMarker(ctx, cx, sy, sh);
    }

    private void renderAlphaSlider(DrawContext ctx, int sx, int sy, int sw, int sh) {
        // Checkerboard background.
        for (int yy = 0; yy < sh; yy += CHECKER_SIZE) {
            for (int xx = 0; xx < sw; xx += CHECKER_SIZE) {
                boolean dark = ((xx / CHECKER_SIZE) + (yy / CHECKER_SIZE)) % 2 == 0;
                int c = dark ? 0xFF_88_88_88 : 0xFF_BB_BB_BB;
                ctx.fill(sx + xx, sy + yy,
                        Math.min(sx + xx + CHECKER_SIZE, sx + sw),
                        Math.min(sy + yy + CHECKER_SIZE, sy + sh), c);
            }
        }
        // Current color with varying alpha left → right.
        int rgb = Color.HSBtoRGB(h, s, v) & 0x00_FF_FF_FF;
        for (int col = 0; col < sw; col++) {
            float t = (sw == 1) ? 0f : col / (float) (sw - 1);
            int a = Math.round(t * 255);
            int color = (a << 24) | rgb;
            ctx.fill(sx + col, sy, sx + col + 1, sy + sh, color);
        }
        drawFrame(ctx, sx, sy, sw, sh, 0xFF_00_00_00);
        int cx = sx + Math.round((alpha / 255f) * (sw - 1));
        drawSliderMarker(ctx, cx, sy, sh);
    }

    private void renderPreview(DrawContext ctx, int sx, int sy, int sw, int sh) {
        int rgb = Color.HSBtoRGB(h, s, v) & 0x00_FF_FF_FF;
        int argb = (alpha << 24) | rgb;

        // Left half: solid current color (alpha applied over checkerboard).
        int half = sw / 2;
        for (int yy = 0; yy < sh; yy += CHECKER_SIZE) {
            for (int xx = 0; xx < half; xx += CHECKER_SIZE) {
                boolean dark = ((xx / CHECKER_SIZE) + (yy / CHECKER_SIZE)) % 2 == 0;
                int c = dark ? 0xFF_88_88_88 : 0xFF_BB_BB_BB;
                ctx.fill(sx + xx, sy + yy,
                        Math.min(sx + xx + CHECKER_SIZE, sx + half),
                        Math.min(sy + yy + CHECKER_SIZE, sy + sh), c);
            }
        }
        ctx.fill(sx, sy, sx + half, sy + sh, argb);

        // Right half: hex readout.
        ctx.fill(sx + half, sy, sx + sw, sy + sh, 0xFF_15_15_18);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String hex = withAlpha
                ? String.format("#%08X", argb)
                : String.format("#%06X", rgb);
        int textY = sy + (sh - 8) / 2;
        ctx.drawText(tr, hex, sx + half + 6, textY, 0xFF_DD_DD_DD, false);

        drawFrame(ctx, sx, sy, sw, sh, 0xFF_00_00_00);
    }

    private static void drawFrame(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void drawRingMarker(DrawContext ctx, int cx, int cy) {
        // 5×5 outer black, 3×3 inner white.
        ctx.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFF_00_00_00);
        ctx.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF_FF_FF_FF);
        ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFF_00_00_00);
    }

    private static void drawSliderMarker(DrawContext ctx, int cx, int sy, int sh) {
        // White vertical bar with black border, 3px wide.
        ctx.fill(cx - 2, sy - 2, cx + 3, sy + sh + 2, 0xFF_00_00_00);
        ctx.fill(cx - 1, sy - 1, cx + 2, sy + sh + 1, 0xFF_FF_FF_FF);
    }

    private static int lerp(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = Math.round(aa + (ba - aa) * t);
        int rr = Math.round(ar + (br - ar) * t);
        int rg = Math.round(ag + (bg - ag) * t);
        int rb = Math.round(ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    // ===== Mouse =====

    private boolean withinSVPad(double mx, double my) {
        return mx >= x && mx < x + padSize && my >= y && my < y + padSize;
    }

    private boolean withinHueSlider(double mx, double my) {
        int hueY = y + padSize + GAP;
        return mx >= x && mx < x + padSize && my >= hueY && my < hueY + SLIDER_HEIGHT;
    }

    private boolean withinAlphaSlider(double mx, double my) {
        if (!withAlpha) return false;
        int alphaY = y + padSize + GAP + SLIDER_HEIGHT + GAP;
        return mx >= x && mx < x + padSize && my >= alphaY && my < alphaY + SLIDER_HEIGHT;
    }

    private void updateSVFromMouse(double mx, double my) {
        float ns = clamp01((float) ((mx - x) / (padSize - 1)));
        float nv = 1f - clamp01((float) ((my - y) / (padSize - 1)));
        s = ns;
        v = nv;
        pushToState();
    }

    private void updateHueFromMouse(double mx) {
        h = clamp01((float) ((mx - x) / (padSize - 1)));
        pushToState();
    }

    private void updateAlphaFromMouse(double mx) {
        alpha = Math.round(clamp01((float) ((mx - x) / (padSize - 1))) * 255f);
        pushToState();
    }

    private static float clamp01(float t) {
        if (t < 0f) return 0f;
        if (t > 1f) return 1f;
        return t;
    }

    @Override
    public boolean onMouseClick(double mx, double my, int button) {
        if (button != 0) return false;
        if (withinSVPad(mx, my))         { draggingSV = true; updateSVFromMouse(mx, my);   return true; }
        if (withinHueSlider(mx, my))     { draggingH  = true; updateHueFromMouse(mx);      return true; }
        if (withinAlphaSlider(mx, my))   { draggingA  = true; updateAlphaFromMouse(mx);    return true; }
        return false;
    }

    @Override
    public boolean onMouseDrag(double mx, double my, double dragX, double dragY, int button) {
        if (draggingSV) { updateSVFromMouse(mx, my); return true; }
        if (draggingH)  { updateHueFromMouse(mx);    return true; }
        if (draggingA)  { updateAlphaFromMouse(mx);  return true; }
        return false;
    }

    @Override
    public boolean onMouseRelease(double mx, double my, int button) {
        boolean had = draggingSV || draggingH || draggingA;
        draggingSV = draggingH = draggingA = false;
        return had;
    }
}
