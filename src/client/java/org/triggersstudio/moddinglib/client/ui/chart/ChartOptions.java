package org.triggersstudio.moddinglib.client.ui.chart;

import java.util.Collections;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * Cosmetic / layout options shared by all chart types. Use
 * {@link #builder()} to compose, then {@code .build()}.
 *
 * <p>Defaults give a dark-themed chart that fits a typical
 * {@code 0xFF_1A_1A_1A} screen background. Override what you need;
 * everything else stays sane.
 */
public final class ChartOptions {

    public static final ChartOptions DEFAULT = builder().build();

    private final List<String> xLabels;
    private final boolean showLegend;
    private final boolean showGrid;
    private final boolean showAxis;
    private final Double yMin;
    private final Double yMax;
    private final DoubleFunction<String> valueFormatter;
    private final int textColor;
    private final int gridColor;
    private final int axisColor;
    private final int tooltipBgColor;
    private final int tooltipTextColor;

    private ChartOptions(Builder b) {
        this.xLabels = b.xLabels;
        this.showLegend = b.showLegend;
        this.showGrid = b.showGrid;
        this.showAxis = b.showAxis;
        this.yMin = b.yMin;
        this.yMax = b.yMax;
        this.valueFormatter = b.valueFormatter;
        this.textColor = b.textColor;
        this.gridColor = b.gridColor;
        this.axisColor = b.axisColor;
        this.tooltipBgColor = b.tooltipBgColor;
        this.tooltipTextColor = b.tooltipTextColor;
    }

    public List<String> xLabels() { return xLabels; }
    public boolean showLegend() { return showLegend; }
    public boolean showGrid() { return showGrid; }
    public boolean showAxis() { return showAxis; }
    public Double yMin() { return yMin; }
    public Double yMax() { return yMax; }
    public DoubleFunction<String> valueFormatter() { return valueFormatter; }
    public int textColor() { return textColor; }
    public int gridColor() { return gridColor; }
    public int axisColor() { return axisColor; }
    public int tooltipBgColor() { return tooltipBgColor; }
    public int tooltipTextColor() { return tooltipTextColor; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private List<String> xLabels = Collections.emptyList();
        private boolean showLegend = true;
        private boolean showGrid = true;
        private boolean showAxis = true;
        private Double yMin = null;
        private Double yMax = null;
        private DoubleFunction<String> valueFormatter = v -> {
            if (v == Math.floor(v) && Math.abs(v) < 1e9) return Long.toString((long) v);
            return String.format("%.2f", v);
        };
        private int textColor = 0xFF_AA_AA_AA;
        private int gridColor = 0xFF_33_33_33;
        private int axisColor = 0xFF_55_55_55;
        private int tooltipBgColor = 0xEE_15_15_15;
        private int tooltipTextColor = 0xFF_FF_FF_FF;

        public Builder xLabels(List<String> labels) {
            this.xLabels = labels != null ? List.copyOf(labels) : Collections.emptyList();
            return this;
        }

        public Builder showLegend(boolean v) { this.showLegend = v; return this; }
        public Builder showGrid(boolean v) { this.showGrid = v; return this; }
        public Builder showAxis(boolean v) { this.showAxis = v; return this; }
        public Builder yMin(double v) { this.yMin = v; return this; }
        public Builder yMax(double v) { this.yMax = v; return this; }
        public Builder yRange(double min, double max) { this.yMin = min; this.yMax = max; return this; }
        public Builder valueFormatter(DoubleFunction<String> f) {
            if (f != null) this.valueFormatter = f;
            return this;
        }
        public Builder textColor(int c) { this.textColor = c; return this; }
        public Builder gridColor(int c) { this.gridColor = c; return this; }
        public Builder axisColor(int c) { this.axisColor = c; return this; }
        public Builder tooltipBgColor(int c) { this.tooltipBgColor = c; return this; }
        public Builder tooltipTextColor(int c) { this.tooltipTextColor = c; return this; }

        public ChartOptions build() { return new ChartOptions(this); }
    }
}
