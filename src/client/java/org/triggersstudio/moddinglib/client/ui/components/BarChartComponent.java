package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.triggersstudio.moddinglib.client.ui.chart.ChartOptions;
import org.triggersstudio.moddinglib.client.ui.chart.ChartSeries;
import org.triggersstudio.moddinglib.client.ui.rendering.PaintRenderer;
import org.triggersstudio.moddinglib.client.ui.styling.Size;
import org.triggersstudio.moddinglib.client.ui.styling.Style;

import java.util.List;

/**
 * Multi-series bar chart with grouped bars (one bar per series within each
 * category slot). The category index drives X position; the series index
 * spreads bars side-by-side inside the slot. Bars grow up from the
 * baseline at {@code y = max(0, yMin)} (so negative values render below
 * the axis when 0 sits inside the Y range).
 *
 * <p>Hover detection: each bar's rect is hit-tested against the cursor;
 * the matching {@code (category, series)} pair drives the tooltip.
 */
public class BarChartComponent extends UIComponent {

    private static final int DEFAULT_W = 240;
    private static final int DEFAULT_H = 160;

    private final List<ChartSeries> series;
    private final ChartOptions options;

    public BarChartComponent(List<ChartSeries> series, ChartOptions options, Style style) {
        super(style);
        this.series = series != null ? List.copyOf(series) : List.of();
        this.options = options != null ? options : ChartOptions.DEFAULT;
    }

    @Override
    public MeasureResult measure(int maxWidth, int maxHeight) {
        int w = applyConstraint(DEFAULT_W, style.getWidth(), maxWidth);
        int h = applyConstraint(DEFAULT_H, style.getHeight(), maxHeight);
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

        int padL = options.showAxis() ? 32 : 4;
        int padR = 6;
        int padT = options.showLegend() && !series.isEmpty() ? 18 : 4;
        int padB = options.showAxis() ? 14 : 4;
        int plotX = innerX + padL;
        int plotY = innerY + padT;
        int plotW = innerW - padL - padR;
        int plotH = innerH - padT - padB;
        if (plotW <= 4 || plotH <= 4) return;

        double[] yRange = computeYRange();
        double yMin = yRange[0];
        double yMax = yRange[1];
        if (yMax - yMin < 1e-9) yMax = yMin + 1.0;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        if (options.showGrid() || options.showAxis()) {
            int ticks = 5;
            for (int i = 0; i <= ticks; i++) {
                int gy = plotY + plotH - (i * plotH / ticks);
                if (options.showGrid()) {
                    ctx.fill(plotX, gy, plotX + plotW, gy + 1, options.gridColor());
                }
                if (options.showAxis()) {
                    double val = yMin + (yMax - yMin) * i / ticks;
                    String label = options.valueFormatter().apply(val);
                    int labelW = tr.getWidth(label);
                    ctx.drawText(tr, label, plotX - labelW - 3, gy - 3,
                            options.textColor(), false);
                }
            }
        }

        // Baseline = visual Y of value=0 (clamped into the plot range).
        double baseVal = Math.max(yMin, Math.min(yMax, 0.0));
        int baseY = valueToY(baseVal, yMin, yMax, plotY, plotH);

        if (options.showAxis()) {
            ctx.fill(plotX, plotY, plotX + 1, plotY + plotH + 1, options.axisColor());
            ctx.fill(plotX, baseY, plotX + plotW + 1, baseY + 1, options.axisColor());
        }

        int categories = maxSeriesLen();
        int bars = series.size();
        if (categories == 0 || bars == 0) {
            renderLegendIfAny(ctx, tr, innerX + padL, innerY + 2, innerW - padL - padR);
            return;
        }

        double slotW = plotW / (double) categories;
        // 80% of the slot is bars, 10% padding on each side.
        double groupW = slotW * 0.8;
        double barW = Math.max(1, groupW / bars - 1);

        int hoverCategory = -1;
        int hoverSeries = -1;
        MouseLocal m = mouseInPlot(plotX, plotY, plotW, plotH);

        for (int i = 0; i < categories; i++) {
            double slotLeft = plotX + i * slotW + slotW * 0.1;
            for (int k = 0; k < bars; k++) {
                ChartSeries s = series.get(k);
                List<Double> vs = s.values().get();
                if (vs == null || i >= vs.size()) continue;
                double v = vs.get(i);
                if (Double.isNaN(v)) continue;

                int bx0 = (int) Math.round(slotLeft + k * (barW + 1));
                int bx1 = (int) Math.round(slotLeft + k * (barW + 1) + barW);
                if (bx1 <= bx0) bx1 = bx0 + 1;
                int by = valueToY(v, yMin, yMax, plotY, plotH);
                int top = Math.min(baseY, by);
                int bot = Math.max(baseY, by);
                ctx.fill(bx0, top, bx1, bot, s.color());

                if (m != null && m.x >= bx0 && m.x <= bx1 && m.y >= top && m.y <= bot) {
                    hoverCategory = i;
                    hoverSeries = k;
                }
            }
        }

        if (options.showAxis()) {
            int targetCount = Math.min(8, categories);
            int step = Math.max(1, (int) Math.ceil(categories / (double) targetCount));
            List<String> xLabels = options.xLabels();
            for (int i = 0; i < categories; i += step) {
                int gx = (int) (plotX + (i + 0.5) * slotW);
                String label = i < xLabels.size() ? xLabels.get(i) : Integer.toString(i);
                int labelW = tr.getWidth(label);
                ctx.drawText(tr, label, gx - labelW / 2, plotY + plotH + 3,
                        options.textColor(), false);
            }
        }

        renderLegendIfAny(ctx, tr, innerX + padL, innerY + 2, innerW - padL - padR);

        if (m != null && hoverCategory >= 0) {
            renderHoverTooltip(ctx, tr, m, hoverCategory, hoverSeries);
        }
    }

    private static int valueToY(double v, double yMin, double yMax, int plotY, int plotH) {
        double t = (v - yMin) / (yMax - yMin);
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        return plotY + plotH - (int) Math.round(t * plotH);
    }

    private double[] computeYRange() {
        if (options.yMin() != null && options.yMax() != null) {
            return new double[]{options.yMin(), options.yMax()};
        }
        double mn = Double.POSITIVE_INFINITY;
        double mx = Double.NEGATIVE_INFINITY;
        for (ChartSeries s : series) {
            List<Double> vs = s.values().get();
            if (vs == null) continue;
            for (Double v : vs) {
                if (v == null || v.isNaN()) continue;
                if (v < mn) mn = v;
                if (v > mx) mx = v;
            }
        }
        if (mn == Double.POSITIVE_INFINITY) return new double[]{0.0, 1.0};
        // Bars look natural starting from 0 — extend the range to include it.
        if (mn > 0) mn = 0;
        if (mx < 0) mx = 0;
        if (options.yMin() != null) mn = options.yMin();
        if (options.yMax() != null) mx = options.yMax();
        if (options.yMax() == null) mx += (mx - mn) * 0.05;
        if (mx == mn) mx = mn + 1.0;
        return new double[]{mn, mx};
    }

    private int maxSeriesLen() {
        int max = 0;
        for (ChartSeries s : series) {
            List<Double> vs = s.values().get();
            if (vs != null && vs.size() > max) max = vs.size();
        }
        return max;
    }

    private void renderLegendIfAny(DrawContext ctx, TextRenderer tr, int x0, int y0, int maxW) {
        if (!options.showLegend() || series.isEmpty()) return;
        int xCursor = x0;
        for (ChartSeries s : series) {
            String label = s.label() != null ? s.label() : "";
            int labelW = tr.getWidth(label);
            int entryW = 8 + 4 + labelW + 12;
            if (xCursor - x0 + entryW > maxW) break;
            ctx.fill(xCursor, y0 + 4, xCursor + 8, y0 + 12, s.color());
            ctx.drawText(tr, label, xCursor + 12, y0 + 4, options.textColor(), false);
            xCursor += entryW;
        }
    }

    private MouseLocal mouseInPlot(int plotX, int plotY, int plotW, int plotH) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int ww = mc.getWindow().getWidth();
        int wh = mc.getWindow().getHeight();
        if (ww <= 0 || wh <= 0) return null;
        double mx = mc.mouse.getX() * sw / (double) ww;
        double my = mc.mouse.getY() * sh / (double) wh;
        if (mx < plotX || mx > plotX + plotW || my < plotY || my > plotY + plotH) return null;
        return new MouseLocal((int) mx, (int) my);
    }

    private void renderHoverTooltip(DrawContext ctx, TextRenderer tr, MouseLocal m,
                                    int category, int seriesIdx) {
        ChartSeries s = series.get(seriesIdx);
        List<Double> vs = s.values().get();
        if (vs == null || category >= vs.size()) return;

        List<String> xLabels = options.xLabels();
        String header = category < xLabels.size() ? xLabels.get(category) : ("#" + category);
        String value = (s.label() != null ? s.label() + ": " : "")
                + options.valueFormatter().apply(vs.get(category));
        int wHeader = tr.getWidth(header);
        int wValue = tr.getWidth(value) + 12;
        int boxW = Math.max(wHeader, wValue) + 8;
        int boxH = 12 + 11;

        int boxX = m.x + 10;
        int boxY = m.y - boxH - 4;
        if (boxX + boxW > x + width) boxX = m.x - boxW - 6;
        if (boxY < y + 2) boxY = m.y + 12;

        ctx.fill(boxX, boxY, boxX + boxW, boxY + boxH, options.tooltipBgColor());
        ctx.drawText(tr, header, boxX + 4, boxY + 3, options.tooltipTextColor(), false);
        ctx.fill(boxX + 4, boxY + 16, boxX + 10, boxY + 22, s.color());
        ctx.drawText(tr, value, boxX + 14, boxY + 15, options.tooltipTextColor(), false);
    }

    private static int applyConstraint(int measured, int constraint, int max) {
        if (Size.isMatchParent(constraint)) return max;
        if (Size.isWrapContent(constraint)) return Math.min(measured, max);
        if (constraint <= 0) return Math.min(measured, max);
        return Math.min(constraint, max);
    }

    private record MouseLocal(int x, int y) {}
}
