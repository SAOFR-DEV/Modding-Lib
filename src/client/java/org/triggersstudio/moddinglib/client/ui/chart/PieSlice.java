package org.triggersstudio.moddinglib.client.ui.chart;

import java.util.function.DoubleSupplier;

/**
 * One wedge of a pie chart. Negative or NaN values are clamped to 0; the
 * total of all slices' current values defines the full circle.
 *
 * <p>The value is pulled each frame via a {@link DoubleSupplier} for
 * reactive updates. Color is ARGB, label drives the legend and tooltip.
 */
public record PieSlice(String label, int color, DoubleSupplier value) {

    public static PieSlice of(String label, int color, double value) {
        return new PieSlice(label, color, () -> value);
    }

    public static PieSlice of(String label, int color, DoubleSupplier supplier) {
        return new PieSlice(label, color, supplier != null ? supplier : () -> 0.0);
    }
}
