package org.triggersstudio.moddinglib.client.ui.animation;

import java.time.Duration;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Time-based ARGB color interpolator. Mirrors {@link Tween}'s lifecycle
 * (play/stop/replay, loop/yoyo, onComplete) but operates on {@code int}
 * ARGB values, interpolating each channel linearly through the easing
 * function.
 *
 * <p>Implements both {@link IntSupplier} (returns the ARGB int directly)
 * and {@link Supplier}{@code <Integer>} for callers that prefer the boxed
 * variant.
 *
 * <pre>
 * ColorTween bg = ColorTween.over(0xFF_22_22_22, 0xFF_2A_5C_88, 600, Easing.OUT_CUBIC).play();
 * int currentColor = bg.getAsInt();
 *
 * ColorTween pulse = ColorTween.from(0xFF_FF_55_55).to(0xFF_55_FF_55)
 *         .durationMs(500).yoyo().play();
 * </pre>
 *
 * <p>Lazy compute: the color is only recomputed when {@link #getAsInt()} is
 * called, so a tween whose owner isn't being drawn consumes zero CPU.
 */
public final class ColorTween implements IntSupplier, Supplier<Integer> {

    private final int fromArgb;
    private final int toArgb;
    private final long durationNanos;
    private final DoubleUnaryOperator easing;
    private final boolean loop;
    private final boolean yoyo;
    private Runnable onComplete;

    private long startNanos = -1;
    private boolean completedFired = false;

    private ColorTween(int from, int to, long durationNanos, DoubleUnaryOperator easing,
                       boolean loop, boolean yoyo, Runnable onComplete) {
        this.fromArgb = from;
        this.toArgb = to;
        this.durationNanos = Math.max(1, durationNanos);
        this.easing = easing != null ? easing : Easing.LINEAR;
        this.loop = loop || yoyo;
        this.yoyo = yoyo;
        this.onComplete = onComplete;
    }

    /** Start a builder at the given starting ARGB color. */
    public static Builder from(int fromArgb) {
        return new Builder(fromArgb);
    }

    /**
     * Compact factory: from → to in {@code durationMs} with the given easing,
     * stopped. Call {@link #play()} to start.
     */
    public static ColorTween over(int fromArgb, int toArgb, long durationMs, DoubleUnaryOperator easing) {
        return new ColorTween(fromArgb, toArgb, durationMs * 1_000_000L, easing, false, false, null);
    }

    /** Start (or restart) the tween from time zero. */
    public ColorTween play() {
        startNanos = System.nanoTime();
        completedFired = false;
        return this;
    }

    public ColorTween replay() {
        return play();
    }

    public ColorTween stop() {
        startNanos = -1;
        completedFired = false;
        return this;
    }

    public ColorTween onComplete(Runnable callback) {
        this.onComplete = callback;
        return this;
    }

    public boolean isDone() {
        if (startNanos < 0) return false;
        if (loop) return false;
        return System.nanoTime() - startNanos >= durationNanos;
    }

    public boolean isStarted() {
        return startNanos >= 0;
    }

    @Override
    public int getAsInt() {
        if (startNanos < 0) return fromArgb;
        long elapsed = System.nanoTime() - startNanos;
        if (elapsed <= 0) return fromArgb;

        if (!loop) {
            if (elapsed >= durationNanos) {
                if (!completedFired) {
                    completedFired = true;
                    if (onComplete != null) onComplete.run();
                }
                return toArgb;
            }
            double progress = (double) elapsed / (double) durationNanos;
            return lerp(fromArgb, toArgb, easing.applyAsDouble(progress));
        }

        long cycleNanos = elapsed % durationNanos;
        long cycleIndex = elapsed / durationNanos;
        double progress = (double) cycleNanos / (double) durationNanos;
        if (yoyo && (cycleIndex & 1L) == 1L) {
            progress = 1.0 - progress;
        }
        return lerp(fromArgb, toArgb, easing.applyAsDouble(progress));
    }

    @Override
    public Integer get() {
        return getAsInt();
    }

    private static int lerp(int a, int b, double t) {
        if (t <= 0.0) return a;
        if (t >= 1.0) return b;
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) Math.round(aa + (ba - aa) * t);
        int rr = (int) Math.round(ar + (br - ar) * t);
        int rg = (int) Math.round(ag + (bg - ag) * t);
        int rb = (int) Math.round(ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    public static final class Builder {
        private final int fromArgb;
        private int toArgb = 0xFF_FF_FF_FF;
        private long durationNanos = 250_000_000L;
        private DoubleUnaryOperator easing = Easing.OUT_CUBIC;
        private boolean loop = false;
        private boolean yoyo = false;
        private Runnable onComplete = null;

        private Builder(int fromArgb) {
            this.fromArgb = fromArgb;
        }

        public Builder to(int argb) {
            this.toArgb = argb;
            return this;
        }

        public Builder duration(Duration d) {
            this.durationNanos = d.toNanos();
            return this;
        }

        public Builder durationMs(long ms) {
            this.durationNanos = ms * 1_000_000L;
            return this;
        }

        public Builder easing(DoubleUnaryOperator e) {
            this.easing = e;
            return this;
        }

        public Builder loop() {
            this.loop = true;
            return this;
        }

        public Builder yoyo() {
            this.yoyo = true;
            this.loop = true;
            return this;
        }

        public Builder onComplete(Runnable callback) {
            this.onComplete = callback;
            return this;
        }

        public ColorTween play() {
            return build().play();
        }

        public ColorTween build() {
            return new ColorTween(fromArgb, toArgb, durationNanos, easing, loop, yoyo, onComplete);
        }
    }
}
