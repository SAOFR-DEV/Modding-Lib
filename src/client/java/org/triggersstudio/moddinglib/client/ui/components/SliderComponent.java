package org.triggersstudio.moddinglib.client.ui.components;

import org.triggersstudio.moddinglib.client.ui.api.Components;
import org.triggersstudio.moddinglib.client.ui.styling.Size;
import org.triggersstudio.moddinglib.client.ui.styling.Style;
import net.minecraft.client.gui.DrawContext;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Horizontal slider. Works entirely in {@code double}; typed bindings to
 * {@code State<Integer>}, {@code State<Double>}, etc. are provided by the
 * factories on {@link Components}.
 *
 * <p>Visual layers (bottom to top):
 * <ol>
 *   <li>Track — the inactive portion. Comes from {@code trackStyle.backgroundColor}
 *       if an explicit track style is provided; otherwise derived from
 *       {@code barStyle.backgroundColor} at ~25% alpha.</li>
 *   <li>Fill — the active portion to the left of the thumb, painted with
 *       {@code barStyle.backgroundColor}.</li>
 *   <li>Thumb — the draggable handle, painted with {@code thumbStyle.backgroundColor}.</li>
 * </ol>
 *
 * <p>Reads the current value via a {@link DoubleSupplier} on every frame and
 * writes through a {@link DoubleConsumer} on drag. No subscription to the
 * underlying state is required; the render loop already polls each frame.
 */
public class SliderComponent extends UIComponent {

    private static final Style DEFAULT_BAR_STYLE = Style.backgroundColor(0xFF_55_88_FF)
            .width(200).height(4).build();
    private static final Style DEFAULT_THUMB_STYLE = Style.backgroundColor(0xFF_FF_FF_FF)
            .width(8).height(16).build();

    private static final int DEFAULT_BAR_WIDTH = 200;
    private static final int DEFAULT_BAR_HEIGHT = 4;
    private static final int DEFAULT_THUMB_WIDTH = 8;
    private static final int DEFAULT_THUMB_HEIGHT = 16;

    private final Style thumbStyle;
    private final Style trackStyle; // nullable — derive from barStyle when null
    private final double min;
    private final double max;
    private final double step;
    private final DoubleSupplier reader;
    private final DoubleConsumer writer;

    private boolean dragging = false;

    public SliderComponent(Style barStyle, Style thumbStyle, Style trackStyle,
                           double min, double max, double step,
                           DoubleSupplier reader, DoubleConsumer writer) {
        super(barStyle != null ? barStyle : DEFAULT_BAR_STYLE);
        this.thumbStyle = thumbStyle != null ? thumbStyle : DEFAULT_THUMB_STYLE;
        this.trackStyle = trackStyle; // null ⇒ derived
        if (max <= min) {
            throw new IllegalArgumentException("Slider max (" + max + ") must be > min (" + min + ")");
        }
        if (step < 0) {
            throw new IllegalArgumentException("Slider step must be >= 0, got " + step);
        }
        this.min = min;
        this.max = max;
        this.step = step;
        this.reader = reader;
        this.writer = writer;
    }

    @Override
    public MeasureResult measure(int maxWidth, int maxHeight) {
        int w = resolveSize(style.getWidth(), DEFAULT_BAR_WIDTH, maxWidth);
        int barH = resolveSize(style.getHeight(), DEFAULT_BAR_HEIGHT, maxHeight);
        int thumbH = resolveSize(thumbStyle.getHeight(), DEFAULT_THUMB_HEIGHT, maxHeight);
        int totalH = Math.max(barH, thumbH);
        return new MeasureResult(w, totalH);
    }

    @Override
    public void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public void render(DrawContext drawContext) {
        int fillColor = style.getBackgroundColor();
        int trackColor = resolveTrackColor(fillColor);
        int thumbColor = thumbStyle.getBackgroundColor();

        int barH = resolveSize(style.getHeight(), DEFAULT_BAR_HEIGHT, height);
        int thumbW = resolveSize(thumbStyle.getWidth(), DEFAULT_THUMB_WIDTH, width);
        int thumbH = resolveSize(thumbStyle.getHeight(), DEFAULT_THUMB_HEIGHT, height);

        int barY = y + (height - barH) / 2;

        // Thumb center travels within [x + thumbW/2, x + width - thumbW/2]
        // so the thumb never clips the outer bounding box.
        double ratio = currentRatio();
        int halfThumb = thumbW / 2;
        int travel = Math.max(0, width - thumbW);
        int thumbCenterX = x + halfThumb + (int) Math.round(ratio * travel);

        // Track (full width)
        if (trackColor != 0) {
            drawContext.fill(x, barY, x + width, barY + barH, trackColor);
        }
        // Fill (left of thumb center)
        if (fillColor != 0) {
            drawContext.fill(x, barY, thumbCenterX, barY + barH, fillColor);
        }
        // Thumb
        if (thumbColor != 0) {
            int thumbX = thumbCenterX - halfThumb;
            int thumbY = y + (height - thumbH) / 2;
            drawContext.fill(thumbX, thumbY, thumbX + thumbW, thumbY + thumbH, thumbColor);
        }
    }

    @Override
    public boolean onMouseClick(double mx, double my, int button) {
        if (button != 0) return false;
        if (!isPointInside(mx, my)) return false;
        dragging = true;
        updateFromMouse(mx);
        return true;
    }

    @Override
    public boolean onMouseDrag(double mx, double my, double dragX, double dragY, int button) {
        if (!dragging) return false;
        updateFromMouse(mx);
        return true;
    }

    @Override
    public boolean onMouseRelease(double mx, double my, int button) {
        if (!dragging) return false;
        dragging = false;
        return true;
    }

    private int resolveTrackColor(int fillColor) {
        if (trackStyle != null) {
            return trackStyle.getBackgroundColor();
        }
        // Derived: same RGB, alpha forced to ~25%. If fill is fully transparent,
        // leave the track invisible too (opt-in minimalism).
        if (fillColor == 0) return 0;
        return (fillColor & 0x00_FF_FF_FF) | 0x40_00_00_00;
    }

    private double currentRatio() {
        double v = reader.getAsDouble();
        double r = (v - min) / (max - min);
        if (r < 0) return 0;
        if (r > 1) return 1;
        return r;
    }

    private void updateFromMouse(double mx) {
        int thumbW = resolveSize(thumbStyle.getWidth(), DEFAULT_THUMB_WIDTH, width);
        double halfThumb = thumbW / 2.0;
        double barStart = x + halfThumb;
        double barEnd = x + width - halfThumb;
        double span = Math.max(1.0, barEnd - barStart);
        double ratio = (mx - barStart) / span;
        if (ratio < 0) ratio = 0;
        if (ratio > 1) ratio = 1;
        double raw = min + ratio * (max - min);
        writer.accept(snap(raw));
    }

    private double snap(double raw) {
        double snapped = raw;
        if (step > 0) {
            snapped = min + Math.round((raw - min) / step) * step;
        }
        if (snapped < min) snapped = min;
        if (snapped > max) snapped = max;
        return snapped;
    }

    private static int resolveSize(int constraint, int fallback, int max) {
        if (Size.isWrapContent(constraint)) return Math.min(fallback, max);
        if (Size.isMatchParent(constraint)) return max;
        if (constraint <= 0) return Math.min(fallback, max);
        return Math.min(constraint, max);
    }
}