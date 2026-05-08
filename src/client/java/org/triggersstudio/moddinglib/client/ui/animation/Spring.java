package org.triggersstudio.moddinglib.client.ui.animation;

import java.util.function.DoubleSupplier;

/**
 * Physics-based mass-spring-damper interpolator. Unlike a {@link Tween}, a
 * Spring has continuous behaviour: changing the {@link #target(double)} mid-flight
 * does not restart the animation — the system keeps its current position
 * and velocity and re-aims at the new target, so transitions remain smooth
 * even when re-targeted under heavy mouse interaction.
 *
 * <p>Integration: semi-implicit Euler at a fixed sub-step (1/240s) for
 * stability. {@link #getAsDouble()} computes the elapsed wall-clock time
 * since the previous sample and runs as many sub-steps as needed. CPU is
 * zero when nothing reads the spring (no global ticker).
 *
 * <p>Presets follow the popular set used by SwiftUI / react-spring:
 * <ul>
 *   <li>{@link #smooth(double)}    — moderate, near-critical damping (default).</li>
 *   <li>{@link #snappy(double)}    — high stiffness, no overshoot.</li>
 *   <li>{@link #bouncy(double)}    — under-damped, overshoots and oscillates.</li>
 *   <li>{@link #strong(double)}    — very stiff, very fast.</li>
 * </ul>
 *
 * <pre>
 * Spring s = Spring.bouncy(0.0).target(100.0);
 * double v = s.getAsDouble();   // pulled forward each frame
 * s.target(200.0);              // re-aim, smooth transition
 * </pre>
 */
public final class Spring implements DoubleSupplier {

    private static final double DEFAULT_MASS = 1.0;
    private static final double FIXED_STEP_SECONDS = 1.0 / 240.0;
    private static final double MAX_DT_SECONDS = 0.1; // clamp for tab-out / breakpoints
    private static final double DEFAULT_REST_THRESHOLD = 0.001;

    private final double stiffness;
    private final double damping;
    private final double mass;
    private final double restThreshold;

    private double position;
    private double velocity;
    private double target;
    private long lastSampleNanos = -1L;
    private boolean atRest = true;
    private Runnable onSettled;

    private Spring(double initial, double stiffness, double damping, double mass, double restThreshold) {
        this.position = initial;
        this.target = initial;
        this.stiffness = stiffness;
        this.damping = damping;
        this.mass = mass > 0 ? mass : DEFAULT_MASS;
        this.restThreshold = restThreshold > 0 ? restThreshold : DEFAULT_REST_THRESHOLD;
    }

    // ===== Builder + presets =====

    public static Builder from(double initial) {
        return new Builder(initial);
    }

    /** Moderate stiffness, near-critical damping. Use this when in doubt. */
    public static Spring smooth(double initial) {
        return new Spring(initial, 170.0, 26.0, DEFAULT_MASS, DEFAULT_REST_THRESHOLD);
    }

    /** High stiffness, slightly over-damped. Fast settle, no overshoot. */
    public static Spring snappy(double initial) {
        return new Spring(initial, 300.0, 35.0, DEFAULT_MASS, DEFAULT_REST_THRESHOLD);
    }

    /** Under-damped — overshoots and oscillates a few times before settling. */
    public static Spring bouncy(double initial) {
        return new Spring(initial, 180.0, 12.0, DEFAULT_MASS, DEFAULT_REST_THRESHOLD);
    }

    /** Very stiff, slightly damped. Almost instant. */
    public static Spring strong(double initial) {
        return new Spring(initial, 500.0, 30.0, DEFAULT_MASS, DEFAULT_REST_THRESHOLD);
    }

    // ===== Mutators =====

    /**
     * Aim the spring at a new target value. Continues from the current
     * position and velocity — no restart. No-op if {@code target} equals
     * the current target.
     */
    public Spring target(double target) {
        if (this.target == target) return this;
        this.target = target;
        this.atRest = false;
        return this;
    }

    /** Snap position and velocity. Useful for jumping without animating. */
    public Spring set(double value) {
        this.position = value;
        this.velocity = 0;
        this.target = value;
        this.atRest = true;
        return this;
    }

    /** Replace the rest callback. Fires once each time the spring settles. */
    public Spring onSettled(Runnable callback) {
        this.onSettled = callback;
        return this;
    }

    // ===== Queries =====

    public double position() {
        return position;
    }

    public double velocity() {
        return velocity;
    }

    public double target() {
        return target;
    }

    public boolean isAtRest() {
        return atRest;
    }

    // ===== Sampling =====

    @Override
    public double getAsDouble() {
        long now = System.nanoTime();
        if (lastSampleNanos < 0) {
            lastSampleNanos = now;
            return position;
        }
        double dt = (now - lastSampleNanos) / 1_000_000_000.0;
        lastSampleNanos = now;
        if (dt <= 0 || atRest) return position;
        if (dt > MAX_DT_SECONDS) dt = MAX_DT_SECONDS;

        // Sub-step to keep the integration stable when frame rate dips.
        while (dt > 0) {
            double sub = Math.min(dt, FIXED_STEP_SECONDS);
            // Semi-implicit Euler: integrate velocity from accel, then position from updated velocity.
            double force = -stiffness * (position - target) - damping * velocity;
            double accel = force / mass;
            velocity += accel * sub;
            position += velocity * sub;
            dt -= sub;
        }

        if (Math.abs(velocity) < restThreshold && Math.abs(position - target) < restThreshold) {
            position = target;
            velocity = 0;
            atRest = true;
            if (onSettled != null) onSettled.run();
        }
        return position;
    }

    /** Fluent builder for advanced configuration. */
    public static final class Builder {
        private final double initial;
        private double stiffness = 170.0;
        private double damping = 26.0;
        private double mass = DEFAULT_MASS;
        private double restThreshold = DEFAULT_REST_THRESHOLD;
        private double initialTarget = Double.NaN;
        private double initialVelocity = 0.0;
        private Runnable onSettled = null;

        private Builder(double initial) {
            this.initial = initial;
        }

        public Builder stiffness(double k) { this.stiffness = k; return this; }
        public Builder damping(double c) { this.damping = c; return this; }
        public Builder mass(double m) { this.mass = m; return this; }
        public Builder restThreshold(double t) { this.restThreshold = t; return this; }
        public Builder target(double t) { this.initialTarget = t; return this; }
        public Builder velocity(double v) { this.initialVelocity = v; return this; }
        public Builder onSettled(Runnable callback) { this.onSettled = callback; return this; }

        public Spring build() {
            Spring s = new Spring(initial, stiffness, damping, mass, restThreshold);
            s.velocity = initialVelocity;
            if (!Double.isNaN(initialTarget)) {
                s.target = initialTarget;
                if (initialTarget != initial || initialVelocity != 0) s.atRest = false;
            }
            s.onSettled = onSettled;
            return s;
        }
    }
}
