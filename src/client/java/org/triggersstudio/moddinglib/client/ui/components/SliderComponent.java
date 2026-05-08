package org.triggersstudio.moddinglib.client.ui.components;

import org.triggersstudio.moddinglib.client.ui.api.Components;
import org.triggersstudio.moddinglib.client.ui.styling.Size;
import org.triggersstudio.moddinglib.client.ui.styling.Style;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Slider with horizontal or vertical orientation. Works entirely in
 * {@code double}; typed bindings to {@code State<Integer>}, {@code State<Double>},
 * etc. are provided by the factories on {@link Components}.
 *
 * <p>Vertical layout convention: minimum at bottom, maximum at top (matches
 * volume sliders, scroll thumbs, etc.).
 *
 * <p>Visual layers (bottom to top):
 * <ol>
 *   <li>Track — the inactive portion. Comes from {@code trackStyle.backgroundColor}
 *       if an explicit track style is provided; otherwise derived from
 *       {@code barStyle.backgroundColor} at ~25% alpha.</li>
 *   <li>Fill — the active portion to the left of (or below) the thumb.</li>
 *   <li>Thumb — the draggable handle. Brightens on hover/drag.</li>
 * </ol>
 *
 * <p>Keyboard: when focused (click to focus), arrow keys move the value by
 * {@code step} (or 1% of the range when step is 0). Home/End jump to min/max.
 * Shift multiplies the arrow step by 10. For vertical sliders, ↑ increases
 * and ↓ decreases (matching the visual orientation).
 */
public class SliderComponent extends UIComponent {

    public enum Orientation { HORIZONTAL, VERTICAL }

    private static final Style DEFAULT_BAR_STYLE = Style.backgroundColor(0xFF_55_88_FF)
            .width(200).height(4).build();
    private static final Style DEFAULT_THUMB_STYLE = Style.backgroundColor(0xFF_FF_FF_FF)
            .width(8).height(16).build();

    private static final int DEFAULT_BAR_LENGTH = 200;
    private static final int DEFAULT_BAR_THICKNESS = 4;
    private static final int DEFAULT_THUMB_THICKNESS = 8;
    private static final int DEFAULT_THUMB_LENGTH = 16;
    private static final int HOVER_LIGHTEN = 30;

    private final Style thumbStyle;
    private final Style trackStyle; // nullable — derive from barStyle when null
    private final Orientation orientation;
    private final double min;
    private final double max;
    private final double step;
    private final DoubleSupplier reader;
    private final DoubleConsumer writer;

    private boolean dragging = false;
    private double lastMouseX, lastMouseY;

    public SliderComponent(Style barStyle, Style thumbStyle, Style trackStyle,
                           double min, double max, double step,
                           DoubleSupplier reader, DoubleConsumer writer) {
        this(barStyle, thumbStyle, trackStyle, min, max, step, reader, writer, Orientation.HORIZONTAL);
    }

    public SliderComponent(Style barStyle, Style thumbStyle, Style trackStyle,
                           double min, double max, double step,
                           DoubleSupplier reader, DoubleConsumer writer,
                           Orientation orientation) {
        super(barStyle != null ? barStyle : DEFAULT_BAR_STYLE);
        this.thumbStyle = thumbStyle != null ? thumbStyle : DEFAULT_THUMB_STYLE;
        this.trackStyle = trackStyle;
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
        this.orientation = orientation != null ? orientation : Orientation.HORIZONTAL;
    }

    @Override
    public MeasureResult measure(int maxWidth, int maxHeight) {
        if (orientation == Orientation.HORIZONTAL) {
            int w = resolveSize(style.getWidth(), DEFAULT_BAR_LENGTH, maxWidth);
            int barH = resolveSize(style.getHeight(), DEFAULT_BAR_THICKNESS, maxHeight);
            int thumbH = resolveSize(thumbStyle.getHeight(), DEFAULT_THUMB_LENGTH, maxHeight);
            int totalH = Math.max(barH, thumbH);
            return new MeasureResult(w, totalH);
        } else {
            int h = resolveSize(style.getHeight(), DEFAULT_BAR_LENGTH, maxHeight);
            int barW = resolveSize(style.getWidth(), DEFAULT_BAR_THICKNESS, maxWidth);
            int thumbW = resolveSize(thumbStyle.getWidth(), DEFAULT_THUMB_LENGTH, maxWidth);
            int totalW = Math.max(barW, thumbW);
            return new MeasureResult(totalW, h);
        }
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
        boolean thumbActive = dragging || isMouseOverThumb();
        if (thumbActive && thumbColor != 0) {
            thumbColor = lighten(thumbColor, HOVER_LIGHTEN);
        }

        if (orientation == Orientation.HORIZONTAL) {
            renderHorizontal(drawContext, fillColor, trackColor, thumbColor);
        } else {
            renderVertical(drawContext, fillColor, trackColor, thumbColor);
        }
    }

    private void renderHorizontal(DrawContext drawContext, int fillColor, int trackColor, int thumbColor) {
        int barH = resolveSize(style.getHeight(), DEFAULT_BAR_THICKNESS, height);
        int thumbW = resolveSize(thumbStyle.getWidth(), DEFAULT_THUMB_THICKNESS, width);
        int thumbH = resolveSize(thumbStyle.getHeight(), DEFAULT_THUMB_LENGTH, height);

        int barY = y + (height - barH) / 2;
        double ratio = currentRatio();
        int halfThumb = thumbW / 2;
        int travel = Math.max(0, width - thumbW);
        int thumbCenterX = x + halfThumb + (int) Math.round(ratio * travel);

        if (trackColor != 0) {
            drawContext.fill(x, barY, x + width, barY + barH, trackColor);
        }
        if (fillColor != 0) {
            drawContext.fill(x, barY, thumbCenterX, barY + barH, fillColor);
        }
        if (thumbColor != 0) {
            int thumbX = thumbCenterX - halfThumb;
            int thumbY = y + (height - thumbH) / 2;
            drawContext.fill(thumbX, thumbY, thumbX + thumbW, thumbY + thumbH, thumbColor);
        }
    }

    private void renderVertical(DrawContext drawContext, int fillColor, int trackColor, int thumbColor) {
        int barW = resolveSize(style.getWidth(), DEFAULT_BAR_THICKNESS, width);
        int thumbW = resolveSize(thumbStyle.getWidth(), DEFAULT_THUMB_LENGTH, width);
        int thumbH = resolveSize(thumbStyle.getHeight(), DEFAULT_THUMB_THICKNESS, height);

        int barX = x + (width - barW) / 2;
        double ratio = currentRatio();
        int halfThumb = thumbH / 2;
        int travel = Math.max(0, height - thumbH);
        // Convention: max at top, so y decreases with ratio.
        int thumbCenterY = y + height - halfThumb - (int) Math.round(ratio * travel);

        if (trackColor != 0) {
            drawContext.fill(barX, y, barX + barW, y + height, trackColor);
        }
        if (fillColor != 0) {
            // Fill grows up from the bottom to the thumb center.
            drawContext.fill(barX, thumbCenterY, barX + barW, y + height, fillColor);
        }
        if (thumbColor != 0) {
            int thumbX = x + (width - thumbW) / 2;
            int thumbY = thumbCenterY - halfThumb;
            drawContext.fill(thumbX, thumbY, thumbX + thumbW, thumbY + thumbH, thumbColor);
        }
    }

    private boolean isMouseOverThumb() {
        if (orientation == Orientation.HORIZONTAL) {
            int thumbW = resolveSize(thumbStyle.getWidth(), DEFAULT_THUMB_THICKNESS, width);
            int thumbH = resolveSize(thumbStyle.getHeight(), DEFAULT_THUMB_LENGTH, height);
            int halfThumb = thumbW / 2;
            int travel = Math.max(0, width - thumbW);
            int cx = x + halfThumb + (int) Math.round(currentRatio() * travel);
            int tx = cx - halfThumb;
            int ty = y + (height - thumbH) / 2;
            return lastMouseX >= tx && lastMouseX < tx + thumbW
                    && lastMouseY >= ty && lastMouseY < ty + thumbH;
        } else {
            int thumbW = resolveSize(thumbStyle.getWidth(), DEFAULT_THUMB_LENGTH, width);
            int thumbH = resolveSize(thumbStyle.getHeight(), DEFAULT_THUMB_THICKNESS, height);
            int halfThumb = thumbH / 2;
            int travel = Math.max(0, height - thumbH);
            int cy = y + height - halfThumb - (int) Math.round(currentRatio() * travel);
            int tx = x + (width - thumbW) / 2;
            int ty = cy - halfThumb;
            return lastMouseX >= tx && lastMouseX < tx + thumbW
                    && lastMouseY >= ty && lastMouseY < ty + thumbH;
        }
    }

    @Override
    public boolean onMouseClick(double mx, double my, int button) {
        if (button != 0) return false;
        if (!isPointInside(mx, my)) return false;
        requestFocus();
        dragging = true;
        updateFromMouse(mx, my);
        return true;
    }

    @Override
    public boolean onMouseDrag(double mx, double my, double dragX, double dragY, int button) {
        if (!dragging) return false;
        updateFromMouse(mx, my);
        return true;
    }

    @Override
    public boolean onMouseRelease(double mx, double my, int button) {
        if (!dragging) return false;
        dragging = false;
        return true;
    }

    @Override
    public void onMouseMove(double mx, double my) {
        lastMouseX = mx;
        lastMouseY = my;
    }

    @Override
    public boolean onKeyPress(int keyCode, int scanCode, int modifiers) {
        double increment = step > 0 ? step : (max - min) / 100.0;
        if (Screen.hasShiftDown()) increment *= 10.0;

        boolean horizontal = orientation == Orientation.HORIZONTAL;
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT:
            case GLFW.GLFW_KEY_DOWN:
                // Both decrease (vertical: down = lower value).
                if (horizontal && keyCode == GLFW.GLFW_KEY_DOWN) return false;
                if (!horizontal && keyCode == GLFW.GLFW_KEY_LEFT) return false;
                writer.accept(snap(reader.getAsDouble() - increment));
                return true;
            case GLFW.GLFW_KEY_RIGHT:
            case GLFW.GLFW_KEY_UP:
                if (horizontal && keyCode == GLFW.GLFW_KEY_UP) return false;
                if (!horizontal && keyCode == GLFW.GLFW_KEY_RIGHT) return false;
                writer.accept(snap(reader.getAsDouble() + increment));
                return true;
            case GLFW.GLFW_KEY_HOME:
                writer.accept(min);
                return true;
            case GLFW.GLFW_KEY_END:
                writer.accept(max);
                return true;
            case GLFW.GLFW_KEY_PAGE_DOWN:
                writer.accept(snap(reader.getAsDouble() - (max - min) / 10.0));
                return true;
            case GLFW.GLFW_KEY_PAGE_UP:
                writer.accept(snap(reader.getAsDouble() + (max - min) / 10.0));
                return true;
            default:
                return false;
        }
    }

    private int resolveTrackColor(int fillColor) {
        if (trackStyle != null) {
            return trackStyle.getBackgroundColor();
        }
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

    private void updateFromMouse(double mx, double my) {
        double ratio;
        if (orientation == Orientation.HORIZONTAL) {
            int thumbW = resolveSize(thumbStyle.getWidth(), DEFAULT_THUMB_THICKNESS, width);
            double halfThumb = thumbW / 2.0;
            double barStart = x + halfThumb;
            double barEnd = x + width - halfThumb;
            double span = Math.max(1.0, barEnd - barStart);
            ratio = (mx - barStart) / span;
        } else {
            int thumbH = resolveSize(thumbStyle.getHeight(), DEFAULT_THUMB_THICKNESS, height);
            double halfThumb = thumbH / 2.0;
            double barStart = y + halfThumb;       // top of travel
            double barEnd = y + height - halfThumb; // bottom of travel
            double span = Math.max(1.0, barEnd - barStart);
            // Top = max, bottom = min → invert.
            ratio = 1.0 - (my - barStart) / span;
        }
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

    private static int lighten(int argb, int amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + amount);
        int b = Math.min(255, (argb & 0xFF) + amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int resolveSize(int constraint, int fallback, int max) {
        if (Size.isWrapContent(constraint)) return Math.min(fallback, max);
        if (Size.isMatchParent(constraint)) return max;
        if (constraint <= 0) return Math.min(fallback, max);
        return Math.min(constraint, max);
    }
}
