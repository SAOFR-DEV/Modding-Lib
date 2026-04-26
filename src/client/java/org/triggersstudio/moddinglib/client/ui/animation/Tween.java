package org.triggersstudio.moddinglib.client.ui.animation;

import java.time.Duration;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;

/**
 * Time-based interpolated value. Implements {@link DoubleSupplier} so it can
 * plug into anywhere a supplier is accepted ({@code TextComponent},
 * {@code AnimatedComponent}, slider readers, etc.).
 *
 * <p>The current value is computed lazily from {@link System#nanoTime()} on
 * every {@link #getAsDouble()} call — there is no global tick or registry.
 * This means tweens that aren't being read (e.g., on a hidden screen) don't
 * consume any CPU.
 *
 * <p>Builder usage:
 * <pre>
 * Tween fade = Tween.from(0.0).to(1.0)
 *         .duration(Duration.ofMillis(300))
 *         .easing(Easing.OUT_CUBIC)
 *         .play();
 * </pre>
 *
 * <p>Or compact:
 * <pre>
 * Tween fade = Tween.over(0.0, 1.0, 300, Easing.OUT_CUBIC).play();
 * </pre>
 *
 * <p>Use {@link #replay()} to restart from the beginning.
 */
public final class Tween implements DoubleSupplier {

    private final double from;
    private final double to;
    private final long durationNanos;
    private final DoubleUnaryOperator easing;

    private long startNanos = -1; // -1 = not started

    private Tween(double from, double to, long durationNanos, DoubleUnaryOperator easing) {
        this.from = from;
        this.to = to;
        this.durationNanos = Math.max(1, durationNanos);
        this.easing = easing != null ? easing : Easing.LINEAR;
    }

    /** Start a builder at the given initial value. */
    public static Builder from(double from) {
        return new Builder(from);
    }

    /**
     * Compact factory: build, set duration in ms and easing in one call.
     * Returns a stopped tween — call {@link #play()} to start.
     */
    public static Tween over(double from, double to, long durationMs, DoubleUnaryOperator easing) {
        return new Tween(from, to, durationMs * 1_000_000L, easing);
    }

    /**
     * Convenience: a 0 → 1 tween for fading-in, with EASE_OUT.
     */
    public static Tween fadeIn(long durationMs) {
        return over(0.0, 1.0, durationMs, Easing.OUT_CUBIC).play();
    }

    /**
     * Convenience: a 1 → 0 tween for fading-out, with EASE_OUT.
     */
    public static Tween fadeOut(long durationMs) {
        return over(1.0, 0.0, durationMs, Easing.OUT_CUBIC).play();
    }

    /** Start (or restart) the tween from time zero. */
    public Tween play() {
        startNanos = System.nanoTime();
        return this;
    }

    /** Reset to the start. Equivalent to {@link #play()}. */
    public Tween replay() {
        return play();
    }

    /** Reset the tween to its starting value and stop it. */
    public Tween stop() {
        startNanos = -1;
        return this;
    }

    /** @return whether the tween has reached its end value. */
    public boolean isDone() {
        if (startNanos < 0) return false;
        return System.nanoTime() - startNanos >= durationNanos;
    }

    /** @return whether the tween has been started at least once. */
    public boolean isStarted() {
        return startNanos >= 0;
    }

    @Override
    public double getAsDouble() {
        if (startNanos < 0) return from;
        long elapsed = System.nanoTime() - startNanos;
        if (elapsed <= 0) return from;
        if (elapsed >= durationNanos) return to;
        double progress = (double) elapsed / (double) durationNanos;
        double eased = easing.applyAsDouble(progress);
        return from + (to - from) * eased;
    }

    /** Fluent builder. */
    public static final class Builder {
        private final double from;
        private double to = 1.0;
        private long durationNanos = 250_000_000L; // 250ms default
        private DoubleUnaryOperator easing = Easing.OUT_CUBIC;

        private Builder(double from) {
            this.from = from;
        }

        public Builder to(double value) {
            this.to = value;
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

        /** Build and start the tween. */
        public Tween play() {
            return build().play();
        }

        /** Build without starting. Call {@link Tween#play()} later. */
        public Tween build() {
            return new Tween(from, to, durationNanos, easing);
        }
    }
}
