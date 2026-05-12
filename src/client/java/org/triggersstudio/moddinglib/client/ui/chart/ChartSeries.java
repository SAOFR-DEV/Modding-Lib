package org.triggersstudio.moddinglib.client.ui.chart;

import java.util.List;
import java.util.function.Supplier;

/**
 * One named, colored series of values for {@code LineChart} / {@code BarChart}.
 * The values are pulled through a {@link Supplier} so the chart re-reads them
 * each frame — pass a state-backed supplier (or {@code () -> myList}) to get
 * live updates without rebuilding the component tree.
 *
 * <p>Color is ARGB. Label is shown in the legend and tooltip; pass
 * {@code null} or empty to hide it.
 */
public record ChartSeries(String label, int color, Supplier<List<Double>> values) {

    public static ChartSeries of(String label, int color, List<Double> values) {
        List<Double> snapshot = values != null ? List.copyOf(values) : List.of();
        return new ChartSeries(label, color, () -> snapshot);
    }

    public static ChartSeries of(String label, int color, Supplier<List<Double>> supplier) {
        return new ChartSeries(label, color, supplier != null ? supplier : List::of);
    }
}
