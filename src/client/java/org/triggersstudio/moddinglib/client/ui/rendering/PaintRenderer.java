package org.triggersstudio.moddinglib.client.ui.rendering;

import net.minecraft.client.gui.DrawContext;
import org.triggersstudio.moddinglib.client.ui.styling.Paint;

/**
 * Renders {@link Paint} fills (solid + linear / radial / conic gradients)
 * into a (possibly rounded) rectangle, using the same scanline-fill style
 * as {@link Shapes}.
 *
 * <p>For gradients, each row of the rect is walked from left to right; the
 * paint is sampled at every pixel and adjacent pixels whose sampled colors
 * agree (after coarse quantization) get coalesced into a single
 * {@code DrawContext.fill} call. With smooth multi-stop gradients the worst
 * case is roughly one fill per quantization bin per row, which keeps the
 * fill-call count bounded regardless of the rect's width.
 */
public final class PaintRenderer {

    private PaintRenderer() {}

    /**
     * Fill the rect with the given paint, respecting {@code borderRadius}
     * for the rounded-corner case. Solid paints short-circuit to
     * {@link Shapes#fillRoundRect} (no scanline walk).
     */
    public static void fillRect(DrawContext ctx, int x, int y, int width, int height,
                                Paint paint, int borderRadius) {
        if (paint == null || width <= 0 || height <= 0) return;

        if (paint instanceof Paint.Solid solid) {
            Shapes.fillRoundRect(ctx, x, y, width, height, borderRadius, solid.argb());
            return;
        }

        int r = clampRadius(borderRadius, width, height);

        if (paint instanceof Paint.LinearGradient lin) {
            fillLinear(ctx, x, y, width, height, r, lin);
        } else if (paint instanceof Paint.RadialGradient rad) {
            fillRadial(ctx, x, y, width, height, r, rad);
        } else if (paint instanceof Paint.ConicGradient cn) {
            fillConic(ctx, x, y, width, height, r, cn);
        }
    }

    // -------------- Linear --------------

    /**
     * Linear gradient. The axis runs along {@code (cosθ, sinθ)} where θ is
     * the angle in radians (CSS-like, screen-Y-down). The four corners of
     * the rect are projected onto that axis to find the min/max projection,
     * and per-pixel {@code t} is rescaled so {@code t=0} sits at the corner
     * furthest along the negative axis direction and {@code t=1} at the
     * one furthest along the positive axis direction. This matches the
     * "fill the rect exactly" behavior most users expect.
     */
    private static void fillLinear(DrawContext ctx, int x0, int y0, int w, int h, int r,
                                   Paint.LinearGradient g) {
        double rad = Math.toRadians(g.angleDegrees());
        double dx = Math.cos(rad);
        double dy = Math.sin(rad);

        // Corner projections — pixel coords are local to the rect (0..w, 0..h).
        double p00 = 0;
        double p10 = w * dx;
        double p01 = h * dy;
        double p11 = w * dx + h * dy;
        double pMin = Math.min(Math.min(p00, p10), Math.min(p01, p11));
        double pMax = Math.max(Math.max(p00, p10), Math.max(p01, p11));
        double pSpan = pMax - pMin;
        if (pSpan <= 0) {
            // Degenerate: gradient axis collapses to a point. Fill with the first stop.
            Shapes.fillRoundRect(ctx, x0, y0, w, h, r, g.representativeColor());
            return;
        }

        for (int row = 0; row < h; row++) {
            int[] span = rowSpan(row, w, h, r);
            int xStart = span[0];
            int xEnd = span[1];
            if (xEnd <= xStart) continue;

            // Per-pixel projection along the row decomposes as base + xStep*x.
            // Pre-compute both so we don't redo the multiply per pixel.
            double base = (row + 0.5) * dy;
            double xStep = dx;
            double t0 = (base + (xStart + 0.5) * xStep - pMin) / pSpan;

            int runStart = xStart;
            int runColor = quantizedColor(g, t0);
            for (int px = xStart + 1; px < xEnd; px++) {
                double t = (base + (px + 0.5) * xStep - pMin) / pSpan;
                int c = quantizedColor(g, t);
                if (c != runColor) {
                    ctx.fill(x0 + runStart, y0 + row, x0 + px, y0 + row + 1, runColor);
                    runStart = px;
                    runColor = c;
                }
            }
            ctx.fill(x0 + runStart, y0 + row, x0 + xEnd, y0 + row + 1, runColor);
        }
    }

    // -------------- Radial --------------

    /**
     * Radial gradient. {@code rx, ry} are radii expressed as fractions of
     * the rect's width / height — so {@code rx=ry=1.0} on a centered
     * gradient covers the inscribed-ish ellipse and pixels past it clamp to
     * the last stop. {@code (cx, cy)} are fractional center coordinates.
     */
    private static void fillRadial(DrawContext ctx, int x0, int y0, int w, int h, int r,
                                   Paint.RadialGradient g) {
        double cxAbs = g.cx() * w;
        double cyAbs = g.cy() * h;
        double rxAbs = Math.max(g.rx() * w, 1e-6);
        double ryAbs = Math.max(g.ry() * h, 1e-6);

        for (int row = 0; row < h; row++) {
            int[] span = rowSpan(row, w, h, r);
            int xStart = span[0];
            int xEnd = span[1];
            if (xEnd <= xStart) continue;

            double dyN = ((row + 0.5) - cyAbs) / ryAbs;
            double dyN2 = dyN * dyN;

            double t0 = Math.sqrt(dyN2 + sq(((xStart + 0.5) - cxAbs) / rxAbs));
            int runStart = xStart;
            int runColor = quantizedColor(g, t0);
            for (int px = xStart + 1; px < xEnd; px++) {
                double dxN = ((px + 0.5) - cxAbs) / rxAbs;
                double t = Math.sqrt(dyN2 + dxN * dxN);
                int c = quantizedColor(g, t);
                if (c != runColor) {
                    ctx.fill(x0 + runStart, y0 + row, x0 + px, y0 + row + 1, runColor);
                    runStart = px;
                    runColor = c;
                }
            }
            ctx.fill(x0 + runStart, y0 + row, x0 + xEnd, y0 + row + 1, runColor);
        }
    }

    // -------------- Conic --------------

    /**
     * Conic gradient. {@code t} is the fractional sweep angle measured
     * clockwise from {@code startAngleDegrees}. With screen-Y-down,
     * {@code Math.atan2(dy, dx)} already increases clockwise, so no sign
     * flip is needed.
     */
    private static void fillConic(DrawContext ctx, int x0, int y0, int w, int h, int r,
                                  Paint.ConicGradient g) {
        double cxAbs = g.cx() * w;
        double cyAbs = g.cy() * h;
        double startRad = Math.toRadians(g.startAngleDegrees());
        double TWO_PI = Math.PI * 2.0;

        for (int row = 0; row < h; row++) {
            int[] span = rowSpan(row, w, h, r);
            int xStart = span[0];
            int xEnd = span[1];
            if (xEnd <= xStart) continue;

            double dyAbs = (row + 0.5) - cyAbs;

            double a0 = Math.atan2(dyAbs, (xStart + 0.5) - cxAbs);
            double t0 = ((a0 - startRad) % TWO_PI + TWO_PI) % TWO_PI / TWO_PI;
            int runStart = xStart;
            int runColor = quantizedColor(g, t0);
            for (int px = xStart + 1; px < xEnd; px++) {
                double a = Math.atan2(dyAbs, (px + 0.5) - cxAbs);
                double t = ((a - startRad) % TWO_PI + TWO_PI) % TWO_PI / TWO_PI;
                int c = quantizedColor(g, t);
                if (c != runColor) {
                    ctx.fill(x0 + runStart, y0 + row, x0 + px, y0 + row + 1, runColor);
                    runStart = px;
                    runColor = c;
                }
            }
            ctx.fill(x0 + runStart, y0 + row, x0 + xEnd, y0 + row + 1, runColor);
        }
    }

    // -------------- Helpers --------------

    /**
     * Sample the paint at parameter {@code t} (clamped to {@code [0, 1]}),
     * then quantize the returned ARGB by rounding each channel to a 5-bit
     * step. This collapses near-identical colors into the same value so the
     * adjacent-pixel run-length compression actually fires.
     *
     * <p>5 bits per channel = 32 levels = 32^4 ≈ 1M distinct colors total,
     * which is plenty visually and aggressive enough that smooth gradients
     * collapse into ~32 runs along their primary axis.
     */
    private static int quantizedColor(Paint p, double t) {
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        int argb = p.sample(t);
        return (argb & 0xF8F8F8F8) | 0x07070707;
    }

    private static int[] rowSpan(int row, int width, int height, int radius) {
        if (radius <= 0) return new int[]{0, width};
        // Top corner band
        if (row < radius) {
            int inset = cornerInset(radius, row);
            return new int[]{inset, width - inset};
        }
        // Bottom corner band
        if (row >= height - radius) {
            int rowFromBottom = height - 1 - row;
            int inset = cornerInset(radius, rowFromBottom);
            return new int[]{inset, width - inset};
        }
        // Middle band
        return new int[]{0, width};
    }

    /**
     * Same circle-equation inset used by {@link Shapes}. Duplicated rather
     * than exposed because {@code Shapes.cornerInset} is private and the
     * rendering pipeline shouldn't reach across packages for a 5-line math
     * primitive.
     */
    private static int cornerInset(int radius, int row) {
        double dy = radius - row - 0.5;
        double inside = (double) radius * radius - dy * dy;
        if (inside <= 0) return radius;
        double half = Math.sqrt(inside);
        return Math.max(0, radius - (int) Math.round(half));
    }

    private static int clampRadius(int requested, int width, int height) {
        if (requested <= 0) return 0;
        return Math.min(requested, Math.min(width / 2, height / 2));
    }

    private static double sq(double v) { return v * v; }
}
