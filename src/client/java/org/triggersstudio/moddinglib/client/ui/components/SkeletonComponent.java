package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.gui.DrawContext;
import org.triggersstudio.moddinglib.client.ui.context.UIContext;
import org.triggersstudio.moddinglib.client.ui.styling.Size;
import org.triggersstudio.moddinglib.client.ui.styling.Style;

/**
 * Placeholder rectangle with a shimmer sweep, used to indicate loading content
 * before the real data arrives. The shimmer is a vertical band of slightly
 * lighter color that travels left-to-right on a periodic loop.
 *
 * <p>Time tracking is local: the shimmer phase is recomputed from
 * {@link System#nanoTime()} each render, so several skeletons stay in sync
 * within a frame without any global ticker. CPU is zero when the screen
 * isn't being drawn (offscreen, paused, etc.).
 *
 * <p>The base rectangle's size, base color, and shimmer color all come from
 * the constructor — height defaults to 12px (line height) and width to
 * MATCH_PARENT, so the most natural usage is to drop one inside a Column
 * with explicit sizing on the wrapping container.
 */
public class SkeletonComponent extends UIComponent {

    private static final long DEFAULT_PERIOD_MS = 1400L;
    private static final int DEFAULT_BASE_COLOR = 0xFF_2A_2A_2E;
    private static final int DEFAULT_SHIMMER_COLOR = 0xFF_44_44_4A;
    private static final int DEFAULT_HEIGHT = 12;

    private final int prefWidth;
    private final int prefHeight;
    private final int baseColor;
    private final int shimmerColor;
    private final long periodMs;

    private long startNanos = -1L;

    public SkeletonComponent() {
        this(Size.MATCH_PARENT, DEFAULT_HEIGHT, DEFAULT_BASE_COLOR, DEFAULT_SHIMMER_COLOR,
                DEFAULT_PERIOD_MS, Style.DEFAULT);
    }

    public SkeletonComponent(int width, int height) {
        this(width, height, DEFAULT_BASE_COLOR, DEFAULT_SHIMMER_COLOR, DEFAULT_PERIOD_MS, Style.DEFAULT);
    }

    public SkeletonComponent(int width, int height, int baseColor, int shimmerColor,
                             long periodMs, Style style) {
        super(style);
        this.prefWidth = width;
        this.prefHeight = height > 0 ? height : DEFAULT_HEIGHT;
        this.baseColor = baseColor;
        this.shimmerColor = shimmerColor;
        this.periodMs = periodMs > 0 ? periodMs : DEFAULT_PERIOD_MS;
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
        int w = applyConstraint(prefWidth, prefWidth, maxWidth);
        int h = applyConstraint(prefHeight, prefHeight, maxHeight);
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
    public void render(DrawContext drawContext) {
        // Base fill.
        drawContext.fill(x, y, x + width, y + height, baseColor);

        if (startNanos < 0) startNanos = System.nanoTime();
        double elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        double phase = elapsedMs / (double) periodMs;
        phase = phase - Math.floor(phase); // 0..1

        // Shimmer band: a moving vertical strip of width = bandWidth.
        // Travel range covers the full span plus the band so the band fully
        // exits the right edge before re-entering from the left.
        int bandWidth = Math.max(20, width / 4);
        int travel = width + bandWidth;
        int bandX = x - bandWidth + (int) Math.round(phase * travel);

        // Three-step gradient (faded edge / core / faded edge) drawn as three
        // thin slices to give a soft band without a real per-pixel gradient.
        int slice = bandWidth / 3;
        int faintAlpha = ((shimmerColor >>> 24) & 0xFF) / 3;
        int faintColor = (faintAlpha << 24) | (shimmerColor & 0x00_FF_FF_FF);

        clippedFill(drawContext, bandX,             bandX + slice,         x, x + width, y, y + height, faintColor);
        clippedFill(drawContext, bandX + slice,     bandX + slice * 2,     x, x + width, y, y + height, shimmerColor);
        clippedFill(drawContext, bandX + slice * 2, bandX + bandWidth,     x, x + width, y, y + height, faintColor);
    }

    private static void clippedFill(DrawContext ctx, int x1, int x2, int clipL, int clipR,
                                    int y1, int y2, int color) {
        int left = Math.max(x1, clipL);
        int right = Math.min(x2, clipR);
        if (right <= left) return;
        ctx.fill(left, y1, right, y2, color);
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
