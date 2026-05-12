package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.gui.DrawContext;
import org.triggersstudio.moddinglib.client.ui.rendering.Shapes;
import org.triggersstudio.moddinglib.client.ui.styling.Size;
import org.triggersstudio.moddinglib.client.ui.styling.Style;
import org.triggersstudio.moddinglib.client.ui.video.VideoPlayer;

import java.util.function.Supplier;

/**
 * Click + drag scrub bar bound to a {@link VideoPlayer}. Reads
 * {@code currentTimeSeconds / durationSeconds} for the visual fill; on
 * mouse release seeks the player to the corresponding position.
 *
 * <p>The player handle is supplied via a callable so the component can
 * tolerate the async-load phase (player is {@code null} until decode is
 * up). For live / unbounded streams ({@code durationSeconds() == ∞}) the
 * bar shows zero fill and ignores clicks — there's nowhere to seek to.
 *
 * <p>To avoid hammering the FFmpeg seek API with one call per drag tick
 * (which trashes the decoder, especially on low-end CPUs), the seek only
 * fires on {@code onMouseRelease}. During the active drag the fill jumps
 * to the cursor for instant visual feedback while playback keeps running
 * from its current position.
 */
public class VideoScrubBarComponent extends UIComponent {

    private static final int DEFAULT_HEIGHT = 6;
    private static final int DEFAULT_WIDTH = 200;
    private static final int HOVER_LIGHTEN = 30;
    /** Transparent-ish white track derived from the fill color when nothing
     *  more specific is configured. */
    private static final int TRACK_ALPHA_MASK = 0x40_00_00_00;

    private final Supplier<VideoPlayer> playerSupplier;

    private boolean dragging = false;
    private double dragRatio = 0.0;
    private double lastMouseX, lastMouseY;

    public VideoScrubBarComponent(Supplier<VideoPlayer> playerSupplier, Style style) {
        super(style);
        if (playerSupplier == null) throw new IllegalArgumentException("playerSupplier must not be null");
        this.playerSupplier = playerSupplier;
    }

    @Override
    public MeasureResult measure(int maxWidth, int maxHeight) {
        int w = resolveSize(style.getWidth(), DEFAULT_WIDTH, maxWidth);
        int h = resolveSize(style.getHeight(), DEFAULT_HEIGHT, maxHeight);
        // Reserve a little vertical breathing room for the thumb.
        int thumbR = thumbRadius(h);
        int total = Math.max(h, thumbR * 2);
        return new MeasureResult(w, total);
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
        int fill = style.getBackgroundColor();
        if (fill == 0) fill = 0xFF_55_88_FF;
        int track = (fill & 0x00_FF_FF_FF) | TRACK_ALPHA_MASK;
        int barH = Math.min(height, Math.max(2, style.getHeight() <= 0 ? DEFAULT_HEIGHT : style.getHeight()));
        int barY = y + (height - barH) / 2;
        int radius = Math.min(style.getBorderRadius(), barH / 2);

        Shapes.fillRoundRect(ctx, x, barY, width, barH, radius, track);

        double ratio = dragging ? dragRatio : currentRatio();
        int fillW = (int) Math.round(ratio * width);
        if (fillW > 0) {
            Shapes.fillRoundRect(ctx, x, barY, fillW, barH, radius, fill);
        }

        // Thumb pops on hover/drag for affordance.
        boolean active = dragging || isPointInside(lastMouseX, lastMouseY);
        int thumbR = thumbRadius(barH);
        int thumbCx = x + fillW;
        int thumbCy = barY + barH / 2;
        int thumbColor = active ? lighten(fill, HOVER_LIGHTEN) : fill;
        Shapes.fillRoundRect(ctx,
                thumbCx - thumbR, thumbCy - thumbR,
                thumbR * 2, thumbR * 2,
                thumbR, thumbColor);
    }

    @Override
    public boolean onMouseClick(double mx, double my, int button) {
        if (button != 0) return false;
        if (!isPointInside(mx, my)) return false;
        VideoPlayer p = playerSupplier.get();
        if (p == null) return false;
        double dur = p.durationSeconds();
        if (!Double.isFinite(dur) || dur <= 0) return false; // live stream — nothing to scrub
        dragging = true;
        dragRatio = computeRatio(mx);
        return true;
    }

    @Override
    public boolean onMouseDrag(double mx, double my, double dx, double dy, int button) {
        if (!dragging) return false;
        dragRatio = computeRatio(mx);
        return true;
    }

    @Override
    public boolean onMouseRelease(double mx, double my, int button) {
        if (!dragging) return false;
        dragging = false;
        VideoPlayer p = playerSupplier.get();
        if (p != null) {
            double dur = p.durationSeconds();
            if (Double.isFinite(dur) && dur > 0) {
                p.seek(dragRatio * dur);
            }
        }
        return true;
    }

    @Override
    public void onMouseMove(double mx, double my) {
        this.lastMouseX = mx;
        this.lastMouseY = my;
    }

    private double computeRatio(double mx) {
        double r = (mx - x) / Math.max(1.0, (double) width);
        if (r < 0) return 0;
        if (r > 1) return 1;
        return r;
    }

    private double currentRatio() {
        VideoPlayer p = playerSupplier.get();
        if (p == null) return 0;
        double dur = p.durationSeconds();
        if (!Double.isFinite(dur) || dur <= 0) return 0;
        double r = p.currentTimeSeconds() / dur;
        if (r < 0) return 0;
        if (r > 1) return 1;
        return r;
    }

    private static int thumbRadius(int barH) {
        return Math.max(4, barH / 2 + 2);
    }

    private static int lighten(int argb, int amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, ((argb >>> 16) & 0xFF) + amount);
        int g = Math.min(255, ((argb >>> 8) & 0xFF) + amount);
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
