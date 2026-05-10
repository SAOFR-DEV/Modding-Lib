package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.triggersstudio.moddinglib.client.ui.chart.ChartOptions;
import org.triggersstudio.moddinglib.client.ui.chart.ChartSeries;
import org.triggersstudio.moddinglib.client.ui.rendering.PaintRenderer;
import org.triggersstudio.moddinglib.client.ui.rendering.Shapes;
import org.triggersstudio.moddinglib.client.ui.styling.Size;
import org.triggersstudio.moddinglib.client.ui.styling.Style;

import java.util.List;

/**
 * Multi-series line chart. Each {@link ChartSeries} contributes one
 * polyline; X positions are evenly spaced over the series' index range, Y
 * positions map data value to plot height (auto bounds, or
 * {@code options.yMin/yMax} when set).
 *
 * <p>Hover behavior: when the cursor enters the plot area a vertical
 * guide locks to the nearest data index and a tooltip lists all series'
 * values at that index. Index detection is by horizontal proximity, so it
 * works even if series have different lengths (the longest one drives
 * spacing; shorter series simply skip past their last index).
 */
public class LineChartComponent extends UIComponent {

    private static final int DEFAULT_W = 240;
    private static final int DEFAULT_H = 160;

    private final List<ChartSeries> series;
    private final ChartOptions options;

    public LineChartComponent(List<ChartSeries> series, ChartOptions options, Style style) {
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

        // Inset by Style padding for the whole chart frame.
        int innerX = x + style.getPadding().left;
        int innerY = y + style.getPadding().top;
        int innerW = width - style.getPadding().getHorizontal();
        int innerH = height - style.getPadding().getVertical();
        if (innerW <= 0 || innerH <= 0) return;

        // Plot-area margins for legend (top), Y labels (left), X labels (bottom).
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

        if (options.showAxis()) {
            ctx.fill(plotX, plotY, plotX + 1, plotY + plotH + 1, options.axisColor());
            ctx.fill(plotX, plotY + plotH, plotX + plotW + 1, plotY + plotH + 1, options.axisColor());
        }

        int maxLen = maxSeriesLen();
        if (options.showAxis() && maxLen > 0) {
            List<String> xLabels = options.xLabels();
            int targetCount = Math.min(6, maxLen);
            int step = Math.max(1, (int) Math.ceil(maxLen / (double) targetCount));
            for (int i = 0; i < maxLen; i += step) {
                int gx = plotX + (maxLen == 1 ? plotW / 2 : i * plotW / (maxLen - 1));
                String label = i < xLabels.size() ? xLabels.get(i) : Integer.toString(i);
                int labelW = tr.getWidth(label);
                ctx.drawText(tr, label, gx - labelW / 2, plotY + plotH + 3,
                        options.textColor(), false);
            }
        }

        for (ChartSeries s : series) {
            List<Double> values = s.values().get();
            if (values == null || values.size() < 2) {
                if (values != null && values.size() == 1) {
                    int px = plotX + plotW / 2;
                    int py = pointY(values.get(0), yMin, yMax, plotY, plotH);
                    ctx.fill(px - 2, py - 2, px + 2, py + 2, s.color());
                }
                continue;
            }
            int n = values.size();
            int denom = n - 1;
            int prevX = -1;
            int prevY = -1;
            for (int i = 0; i < n; i++) {
                double v = values.get(i);
                int px = plotX + (denom == 0 ? plotW / 2 : i * plotW / denom);
                int py = pointY(v, yMin, yMax, plotY, plotH);
                if (prevX >= 0) {
                    Shapes.drawLine(ctx, prevX, prevY, px, py, 2, s.color());
                }
                prevX = px;
                prevY = py;
            }
        }

        if (options.showLegend() && !series.isEmpty()) {
            renderLegend(ctx, tr, innerX + padL, innerY + 2, innerW - padL - padR);
        }

        // Hover tooltip — query mouse directly so we always have a fresh
        // value (UIComponent.onMouseMove only fires when the cursor moves).
        renderHoverIfAny(ctx, tr, plotX, plotY, plotW, plotH, yMin, yMax, maxLen);
    }

    private static int pointY(double v, double yMin, double yMax, int plotY, int plotH) {
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
        if (options.yMin() != null) mn = options.yMin();
        if (options.yMax() != null) mx = options.yMax();
        // Slight padding on top so the highest point doesn't clip.
        if (options.yMin() == null) mn -= (mx - mn) * 0.05;
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

    private void renderLegend(DrawContext ctx, TextRenderer tr, int x0, int y0, int maxW) {
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

    private void renderHoverIfAny(DrawContext ctx, TextRenderer tr,
                                  int plotX, int plotY, int plotW, int plotH,
                                  double yMin, double yMax, int maxLen) {
        if (maxLen <= 0) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int ww = mc.getWindow().getWidth();
        int wh = mc.getWindow().getHeight();
        if (ww <= 0 || wh <= 0) return;
        double mx = mc.mouse.getX() * sw / (double) ww;
        double my = mc.mouse.getY() * sh / (double) wh;
        if (mx < plotX || mx > plotX + plotW || my < plotY || my > plotY + plotH) return;

        int denom = Math.max(1, maxLen - 1);
        double rel = (mx - plotX) / (double) plotW;
        int idx = (int) Math.round(rel * denom);
        if (idx < 0) idx = 0;
        if (idx >= maxLen) idx = maxLen - 1;
        int gx = plotX + idx * plotW / denom;

        // Vertical guide.
        ctx.fill(gx, plotY, gx + 1, plotY + plotH + 1, 0x55_FF_FF_FF);

        // Tooltip lines: header (x label) + each series with a value at idx.
        List<String> xLabels = options.xLabels();
        String header = idx < xLabels.size() ? xLabels.get(idx) : ("#" + idx);
        int linesH = 0;
        int linesW = tr.getWidth(header);
        int valid = 0;
        for (ChartSeries s : series) {
            List<Double> vs = s.values().get();
            if (vs == null || idx >= vs.size()) continue;
            valid++;
            String line = (s.label() != null ? s.label() + ": " : "")
                    + options.valueFormatter().apply(vs.get(idx));
            int w = tr.getWidth(line) + 12;
            if (w > linesW) linesW = w;
        }
        linesH = 12 + valid * 11;
        if (valid == 0) return;

        int boxX = (int) mx + 10;
        int boxY = (int) my - linesH - 4;
        int boxW = linesW + 8;
        int boxH = linesH;
        if (boxX + boxW > x + width) boxX = (int) mx - boxW - 6;
        if (boxY < y + 2) boxY = (int) my + 12;

        ctx.fill(boxX, boxY, boxX + boxW, boxY + boxH, options.tooltipBgColor());
        ctx.drawText(tr, header, boxX + 4, boxY + 3, options.tooltipTextColor(), false);

        // Per-series row: colored dot + label/value, plus mark the data
        // point on the chart so the user sees what the tooltip is reading.
        int row = 0;
        for (ChartSeries s : series) {
            List<Double> vs = s.values().get();
            if (vs == null || idx >= vs.size()) continue;
            int rowY = boxY + 13 + row * 11;
            ctx.fill(boxX + 4, rowY + 2, boxX + 10, rowY + 8, s.color());
            String line = (s.label() != null ? s.label() + ": " : "")
                    + options.valueFormatter().apply(vs.get(idx));
            ctx.drawText(tr, line, boxX + 14, rowY + 1, options.tooltipTextColor(), false);

            int dy = pointY(vs.get(idx), yMin, yMax, plotY, plotH);
            ctx.fill(gx - 2, dy - 2, gx + 3, dy + 3, s.color());
            row++;
        }
    }

    private static int applyConstraint(int measured, int constraint, int max) {
        if (Size.isMatchParent(constraint)) return max;
        if (Size.isWrapContent(constraint)) return Math.min(measured, max);
        if (constraint <= 0) return Math.min(measured, max);
        return Math.min(constraint, max);
    }
}
