package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.gui.DrawContext;
import org.triggersstudio.moddinglib.client.ui.context.UIContext;
import org.triggersstudio.moddinglib.client.ui.rendering.PaintRenderer;
import org.triggersstudio.moddinglib.client.ui.styling.Size;
import org.triggersstudio.moddinglib.client.ui.styling.Style;

/**
 * Indeterminate loading spinner. Renders {@code dotCount} dots arranged on a
 * circle; each frame, one dot is the "head" and the trailing dots fade out
 * by angular distance — producing a smooth rotating trail.
 *
 * <p>Time tracking is local: the rotation phase is recomputed from
 * {@link System#nanoTime()} each render, so the spinner stays in sync no
 * matter when it was attached. No global ticker.
 *
 * <p>The component is a fixed square ({@code size × size}). Pass a non-zero
 * size or rely on the default (24px). Style background/border are honored
 * via the wrapping {@link Style}; the dots use {@code dotColor}.
 */
public class SpinnerComponent extends UIComponent {

    private static final int DEFAULT_SIZE = 24;
    private static final int DEFAULT_DOT_COUNT = 8;
    private static final long DEFAULT_PERIOD_MS = 800L;
    private static final int DEFAULT_DOT_COLOR = 0xFF_FF_FF_FF;

    private final int size;
    private final int dotCount;
    private final int dotSize;
    private final long periodMs;
    private final int dotColor;

    private long startNanos = -1L;

    public SpinnerComponent() {
        this(DEFAULT_SIZE, DEFAULT_DOT_COLOR, DEFAULT_PERIOD_MS, Style.DEFAULT);
    }

    public SpinnerComponent(int size) {
        this(size, DEFAULT_DOT_COLOR, DEFAULT_PERIOD_MS, Style.DEFAULT);
    }

    public SpinnerComponent(int size, int dotColor, long periodMs, Style style) {
        super(style);
        this.size = size > 0 ? size : DEFAULT_SIZE;
        this.dotCount = DEFAULT_DOT_COUNT;
        this.dotSize = Math.max(2, this.size / 8);
        this.periodMs = periodMs > 0 ? periodMs : DEFAULT_PERIOD_MS;
        this.dotColor = dotColor;
    }

    @Override
    public void onAttach(UIContext ctx) {
        super.onAttach(ctx);
        if (startNanos < 0) {
            startNanos = System.nanoTime();
        }
    }

    @Override
    public MeasureResult measure(int maxWidth, int maxHeight) {
        int padH = style.getPadding().getHorizontal();
        int padV = style.getPadding().getVertical();
        int totalW = applyConstraint(size + padH, style.getWidth(), maxWidth);
        int totalH = applyConstraint(size + padV, style.getHeight(), maxHeight);
        return new MeasureResult(totalW, totalH);
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
        if (style.getBackgroundColor() != 0x00_00_00_00) {
            PaintRenderer.fillRect(drawContext, x, y, width, height,
                    style.getBackgroundPaint(), style.getBorderRadius());
        }

        if (startNanos < 0) startNanos = System.nanoTime();
        double elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        double phase = elapsedMs / (double) periodMs;
        phase = phase - Math.floor(phase); // 0..1 wrap

        int cx = x + width / 2;
        int cy = y + height / 2;
        int radius = size / 2 - dotSize;

        int baseRgb = dotColor & 0x00_FF_FF_FF;
        int baseAlpha = (dotColor >>> 24) & 0xFF;

        for (int i = 0; i < dotCount; i++) {
            double dotPhase = (double) i / (double) dotCount;
            // Distance from "head" walking backward around the ring (0..1).
            double dist = (phase - dotPhase + 1.0) % 1.0;
            // Ease the trail: head bright, tail faint.
            double intensity = 1.0 - dist;
            intensity = intensity * intensity; // quadratic falloff
            int alpha = Math.max(20, (int) Math.round(baseAlpha * intensity));

            double angle = dotPhase * 2.0 * Math.PI - Math.PI / 2.0;
            int dx = (int) Math.round(Math.cos(angle) * radius);
            int dy = (int) Math.round(Math.sin(angle) * radius);

            int color = (alpha << 24) | baseRgb;
            int half = dotSize / 2;
            drawContext.fill(
                    cx + dx - half,
                    cy + dy - half,
                    cx + dx + half + (dotSize % 2),
                    cy + dy + half + (dotSize % 2),
                    color);
        }
    }

    protected int applyConstraint(int measured, int constraint, int max) {
        if (Size.isMatchParent(constraint)) {
            return max;
        } else if (Size.isWrapContent(constraint)) {
            return measured;
        } else {
            return Math.min(constraint, max);
        }
    }
}
