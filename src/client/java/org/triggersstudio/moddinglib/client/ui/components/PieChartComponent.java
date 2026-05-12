package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.triggersstudio.moddinglib.client.ui.chart.ChartOptions;
import org.triggersstudio.moddinglib.client.ui.chart.PieSlice;
import org.triggersstudio.moddinglib.client.ui.rendering.PaintRenderer;
import org.triggersstudio.moddinglib.client.ui.rendering.Shapes;
import org.triggersstudio.moddinglib.client.ui.styling.Size;
import org.triggersstudio.moddinglib.client.ui.styling.Style;

import java.util.List;

/**
 * Pie chart with optional side legend. Each slice's angular extent is its
 * value over the total of all slice values; angles use the standard pie
 * convention (0 = top, growing clockwise). Negative or NaN values clamp
 * to 0.
 *
 * <p>Hover: atan2 + distance from center identifies the slice; tooltip
 * lists label, raw value, and percentage.
 */
public class PieChartComponent extends UIComponent {

    private static final int DEFAULT_SIZE = 180;
    private static final double TWO_PI = Math.PI * 2.0;

    private final List<PieSlice> slices;
    private final ChartOptions options;

    public PieChartComponent(List<PieSlice> slices, ChartOptions options, Style style) {
        super(style);
        this.slices = slices != null ? List.copyOf(slices) : List.of();
        this.options = options != null ? options : ChartOptions.DEFAULT;
    }

    @Override
    public MeasureResult measure(int maxWidth, int maxHeight) {
        int w = applyConstraint(DEFAULT_SIZE * 2, style.getWidth(), maxWidth);
        int h = applyConstraint(DEFAULT_SIZE, style.getHeight(), maxHeight);
        return new MeasureResult(w, h);
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
        if (style.getBackgroundColor() != 0) {
            PaintRenderer.fillRect(ctx, x, y, width, height,
                    style.getBackgroundPaint(), style.getBorderRadius());
        }
        int innerX = x + style.getPadding().left;
        int innerY = y + style.getPadding().top;
        int innerW = width - style.getPadding().getHorizontal();
        int innerH = height - style.getPadding().getVertical();
        if (innerW <= 0 || innerH <= 0) return;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // Legend takes the right third of the inner box when enabled.
        int legendW = options.showLegend() && !slices.isEmpty()
                ? Math.min(innerW / 2, longestLegendWidth(tr) + 24)
                : 0;
        int pieW = innerW - legendW;

        double total = totalValue();
        int radius = Math.max(8, Math.min(pieW, innerH) / 2 - 4);
        int cx = innerX + pieW / 2;
        int cy = innerY + innerH / 2;

        if (total <= 0 || slices.isEmpty()) {
            // Empty stub — draw a hollow ring so the slot is visible.
            ctx.drawBorder(cx - radius, cy - radius, radius * 2, radius * 2,
                    options.gridColor());
        } else {
            double cursor = 0.0;
            for (PieSlice slice : slices) {
                double v = Math.max(0, slice.value().getAsDouble());
                if (v <= 0) continue;
                double sweep = v / total * TWO_PI;
                Shapes.fillCircleSlice(ctx, cx, cy, radius,
                        cursor, cursor + sweep, slice.color());
                cursor += sweep;
            }
        }

        if (legendW > 0) {
            renderLegend(ctx, tr, innerX + pieW + 4, innerY + 4, legendW - 4, total);
        }

        renderHover(ctx, tr, cx, cy, radius, total);
    }

    private double totalValue() {
        double t = 0;
        for (PieSlice s : slices) {
            double v = s.value().getAsDouble();
            if (Double.isFinite(v) && v > 0) t += v;
        }
        return t;
    }

    private int longestLegendWidth(TextRenderer tr) {
        int max = 0;
        for (PieSlice s : slices) {
            String label = s.label() != null ? s.label() : "";
            int w = tr.getWidth(label);
            if (w > max) max = w;
        }
        return max;
    }

    private void renderLegend(DrawContext ctx, TextRenderer tr,
                              int x0, int y0, int maxW, double total) {
        int rowY = y0;
        for (PieSlice s : slices) {
            if (rowY + 11 > y + height - 2) break;
            ctx.fill(x0, rowY + 2, x0 + 8, rowY + 10, s.color());
            String label = s.label() != null ? s.label() : "";
            double v = Math.max(0, s.value().getAsDouble());
            String pct = total > 0 ? String.format(" (%.1f%%)", v / total * 100.0) : "";
            String full = label + pct;
            // Truncate if needed.
            while (tr.getWidth(full) > maxW - 14 && full.length() > 1) {
                full = full.substring(0, full.length() - 1);
            }
            ctx.drawText(tr, full, x0 + 12, rowY + 2, options.textColor(), false);
            rowY += 11;
        }
    }

    private void renderHover(DrawContext ctx, TextRenderer tr,
                             int cx, int cy, int radius, double total) {
        if (total <= 0) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int ww = mc.getWindow().getWidth();
        int wh = mc.getWindow().getHeight();
        if (ww <= 0 || wh <= 0) return;
        double mx = mc.mouse.getX() * sw / (double) ww;
        double my = mc.mouse.getY() * sh / (double) wh;
        double dx = mx - cx;
        double dy = my - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist > radius) return;

        // Pie angle: atan2(dx, -dy) → 0 at top, CW.
        double a = Math.atan2(dx, -dy);
        if (a < 0) a += TWO_PI;

        double cursor = 0.0;
        PieSlice hovered = null;
        double hoveredValue = 0;
        for (PieSlice s : slices) {
            double v = Math.max(0, s.value().getAsDouble());
            if (v <= 0) continue;
            double sweep = v / total * TWO_PI;
            if (a >= cursor && a < cursor + sweep) {
                hovered = s;
                hoveredValue = v;
                break;
            }
            cursor += sweep;
        }
        if (hovered == null) return;

        String label = hovered.label() != null ? hovered.label() : "";
        String value = options.valueFormatter().apply(hoveredValue)
                + String.format(" (%.1f%%)", hoveredValue / total * 100.0);
        int boxW = Math.max(tr.getWidth(label), tr.getWidth(value) + 12) + 8;
        int boxH = 12 + 11;

        int boxX = (int) mx + 10;
        int boxY = (int) my - boxH - 4;
        if (boxX + boxW > x + width) boxX = (int) mx - boxW - 6;
        if (boxY < y + 2) boxY = (int) my + 12;

        ctx.fill(boxX, boxY, boxX + boxW, boxY + boxH, options.tooltipBgColor());
        ctx.drawText(tr, label, boxX + 4, boxY + 3, options.tooltipTextColor(), false);
        ctx.fill(boxX + 4, boxY + 16, boxX + 10, boxY + 22, hovered.color());
        ctx.drawText(tr, value, boxX + 14, boxY + 15, options.tooltipTextColor(), false);
    }

    private static int applyConstraint(int measured, int constraint, int max) {
        if (Size.isMatchParent(constraint)) return max;
        if (Size.isWrapContent(constraint)) return Math.min(measured, max);
        if (constraint <= 0) return Math.min(measured, max);
        return Math.min(constraint, max);
    }
}
