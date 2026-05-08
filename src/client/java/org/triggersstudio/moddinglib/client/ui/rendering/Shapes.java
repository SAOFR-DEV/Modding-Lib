package org.triggersstudio.moddinglib.client.ui.rendering;

import net.minecraft.client.gui.DrawContext;

/**
 * Rendering helpers for shapes that {@link DrawContext} doesn't expose
 * directly. {@code DrawContext.fill} only does axis-aligned rectangles, so
 * rounded corners are emulated by per-row horizontal segments computed
 * against the corner circle equation.
 *
 * <p>The cost is N extra fill calls per corner where N = radius (typically
 * 2–8). Acceptable for menu UIs.
 *
 * <p>All methods take {@code (x, y, width, height)} (not {@code x2, y2}) for
 * consistency with the rest of the codebase. A radius of 0 short-circuits
 * to the equivalent flat-edge call.
 */
public final class Shapes {

    private Shapes() {}

    /**
     * Fill a rectangle whose four corners are rounded with the given radius.
     * Radius is clamped to {@code min(width, height) / 2} so the shape
     * degrades to a stadium (rounded ends) when radius equals half the
     * shorter side.
     */
    public static void fillRoundRect(DrawContext ctx, int x, int y, int width, int height,
                                     int radius, int color) {
        if (radius <= 0 || width <= 0 || height <= 0) {
            ctx.fill(x, y, x + width, y + height, color);
            return;
        }
        int r = Math.min(radius, Math.min(width / 2, height / 2));
        if (r <= 0) {
            ctx.fill(x, y, x + width, y + height, color);
            return;
        }
        // Middle band — full width, between the corner zones.
        ctx.fill(x, y + r, x + width, y + height - r, color);
        // Top + bottom corner bands — one fill per row, narrowed by the circle equation.
        for (int row = 0; row < r; row++) {
            int inset = cornerInset(r, row);
            ctx.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            ctx.fill(x + inset, y + height - 1 - row, x + width - inset, y + height - row, color);
        }
    }

    /**
     * Draw a {@code borderWidth}-thick border with rounded corners. Just the
     * outline — the interior is left untouched.
     */
    public static void drawRoundRectBorder(DrawContext ctx, int x, int y, int width, int height,
                                           int radius, int borderWidth, int color) {
        if (borderWidth <= 0 || width <= 0 || height <= 0) return;
        if (radius <= 0) {
            ctx.fill(x, y, x + width, y + borderWidth, color);
            ctx.fill(x, y + height - borderWidth, x + width, y + height, color);
            ctx.fill(x, y + borderWidth, x + borderWidth, y + height - borderWidth, color);
            ctx.fill(x + width - borderWidth, y + borderWidth, x + width, y + height - borderWidth, color);
            return;
        }
        int r = Math.min(radius, Math.min(width / 2, height / 2));
        int bw = Math.min(borderWidth, r);

        // Straight edges between corner zones.
        ctx.fill(x + r, y, x + width - r, y + bw, color);
        ctx.fill(x + r, y + height - bw, x + width - r, y + height, color);
        ctx.fill(x, y + r, x + bw, y + height - r, color);
        ctx.fill(x + width - bw, y + r, x + width, y + height - r, color);

        // Corner rings — for each row in the corner zone, fill from the outer
        // arc inset up to (but not into) the inner arc inset.
        double innerR = r - bw;
        for (int row = 0; row < r; row++) {
            int outerInset = cornerInset(r, row);
            // Inner arc reaches this row only when the y-distance from the
            // shared center fits inside the inner circle. For row < bw the
            // inner arc is "above" → ring spans the full corner width.
            int innerInset;
            if (innerR <= 0 || row < bw) {
                innerInset = r;
            } else {
                double dy = r - row - 0.5;
                double inside = innerR * innerR - dy * dy;
                innerInset = inside <= 0 ? r : (int) Math.round(r - Math.sqrt(inside));
            }
            if (innerInset <= outerInset) continue;

            // Top-left
            ctx.fill(x + outerInset, y + row, x + innerInset, y + row + 1, color);
            // Top-right (mirror x)
            ctx.fill(x + width - innerInset, y + row, x + width - outerInset, y + row + 1, color);
            // Bottom-left (mirror y)
            ctx.fill(x + outerInset, y + height - 1 - row, x + innerInset, y + height - row, color);
            // Bottom-right (mirror both)
            ctx.fill(x + width - innerInset, y + height - 1 - row, x + width - outerInset, y + height - row, color);
        }
    }

    /**
     * Horizontal inset for a given row of a rounded corner zone. Row 0 is
     * the topmost row (greatest inset), row r-1 is the row meeting the
     * straight edge below it (zero inset).
     */
    private static int cornerInset(int radius, int row) {
        double dy = radius - row - 0.5;
        double inside = (double) radius * radius - dy * dy;
        if (inside <= 0) return radius;
        double half = Math.sqrt(inside);
        return Math.max(0, radius - (int) Math.round(half));
    }
}
