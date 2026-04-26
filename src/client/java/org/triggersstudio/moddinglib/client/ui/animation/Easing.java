package org.triggersstudio.moddinglib.client.ui.animation;

import java.util.function.DoubleUnaryOperator;

/**
 * Standard easing functions, expressed as {@code t -> eased(t)} where
 * {@code t ∈ [0, 1]}.
 *
 * <p>Coverage: linear, the four Penner families ({@code Quad/Cubic/Quart/Quint},
 * a.k.a. {@code Power} of 2/3/4/5), {@code Sine}, {@code Expo}, {@code Circ},
 * {@code Back}, {@code Elastic}, {@code Bounce}. Each family exposes
 * {@code IN}, {@code OUT}, {@code IN_OUT} and {@code OUT_IN} variants.
 *
 * <p>Plus parametric factories: {@link #bezier(double, double, double, double)}
 * for arbitrary cubic-bezier curves, {@link #steps(int, boolean)} for stair-step
 * easing, {@link #power(double)} for arbitrary exponents.
 *
 * <p>Composition helpers:
 * <ul>
 *   <li>{@link #combine(DoubleUnaryOperator, DoubleUnaryOperator)} — first half "in", second half "out".</li>
 *   <li>{@link #outIn(DoubleUnaryOperator)} — apply OUT then IN to make the OUT_IN variant.</li>
 *   <li>{@link #reverse(DoubleUnaryOperator)} — flip an IN curve into its OUT counterpart.</li>
 * </ul>
 *
 * <p>Not yet included (TODO): physically simulated {@code Spring} family
 * (default/snappy/bouncy/strong) and the {@code irregular} family. Both need
 * design beyond a pure {@code DoubleUnaryOperator}.
 */
public final class Easing {

    // ===== Linear =====
    public static final DoubleUnaryOperator LINEAR = t -> t;

    // ===== Sine =====
    public static final DoubleUnaryOperator IN_SINE = t -> 1 - Math.cos((t * Math.PI) / 2);
    public static final DoubleUnaryOperator OUT_SINE = t -> Math.sin((t * Math.PI) / 2);
    public static final DoubleUnaryOperator IN_OUT_SINE = t -> -(Math.cos(Math.PI * t) - 1) / 2;
    public static final DoubleUnaryOperator OUT_IN_SINE = outIn(IN_SINE);

    // ===== Quad (power 2) =====
    public static final DoubleUnaryOperator IN_QUAD = t -> t * t;
    public static final DoubleUnaryOperator OUT_QUAD = t -> 1 - (1 - t) * (1 - t);
    public static final DoubleUnaryOperator IN_OUT_QUAD = t -> t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
    public static final DoubleUnaryOperator OUT_IN_QUAD = outIn(IN_QUAD);

    // ===== Cubic (power 3) =====
    public static final DoubleUnaryOperator IN_CUBIC = t -> t * t * t;
    public static final DoubleUnaryOperator OUT_CUBIC = t -> 1 - Math.pow(1 - t, 3);
    public static final DoubleUnaryOperator IN_OUT_CUBIC = t -> t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    public static final DoubleUnaryOperator OUT_IN_CUBIC = outIn(IN_CUBIC);

    // ===== Quart (power 4) =====
    public static final DoubleUnaryOperator IN_QUART = t -> t * t * t * t;
    public static final DoubleUnaryOperator OUT_QUART = t -> 1 - Math.pow(1 - t, 4);
    public static final DoubleUnaryOperator IN_OUT_QUART = t -> t < 0.5 ? 8 * t * t * t * t : 1 - Math.pow(-2 * t + 2, 4) / 2;
    public static final DoubleUnaryOperator OUT_IN_QUART = outIn(IN_QUART);

    // ===== Quint (power 5) =====
    public static final DoubleUnaryOperator IN_QUINT = t -> Math.pow(t, 5);
    public static final DoubleUnaryOperator OUT_QUINT = t -> 1 - Math.pow(1 - t, 5);
    public static final DoubleUnaryOperator IN_OUT_QUINT = t -> t < 0.5 ? 16 * Math.pow(t, 5) : 1 - Math.pow(-2 * t + 2, 5) / 2;
    public static final DoubleUnaryOperator OUT_IN_QUINT = outIn(IN_QUINT);

    // ===== Expo =====
    public static final DoubleUnaryOperator IN_EXPO = t -> t == 0 ? 0 : Math.pow(2, 10 * t - 10);
    public static final DoubleUnaryOperator OUT_EXPO = t -> t == 1 ? 1 : 1 - Math.pow(2, -10 * t);
    public static final DoubleUnaryOperator IN_OUT_EXPO = t -> {
        if (t == 0) return 0;
        if (t == 1) return 1;
        return t < 0.5
                ? Math.pow(2, 20 * t - 10) / 2
                : (2 - Math.pow(2, -20 * t + 10)) / 2;
    };
    public static final DoubleUnaryOperator OUT_IN_EXPO = outIn(IN_EXPO);

    // ===== Circ =====
    public static final DoubleUnaryOperator IN_CIRC = t -> 1 - Math.sqrt(1 - t * t);
    public static final DoubleUnaryOperator OUT_CIRC = t -> Math.sqrt(1 - Math.pow(t - 1, 2));
    public static final DoubleUnaryOperator IN_OUT_CIRC = t -> t < 0.5
            ? (1 - Math.sqrt(1 - Math.pow(2 * t, 2))) / 2
            : (Math.sqrt(1 - Math.pow(-2 * t + 2, 2)) + 1) / 2;
    public static final DoubleUnaryOperator OUT_IN_CIRC = outIn(IN_CIRC);

    // ===== Back (overshoot) =====
    private static final double BACK_C1 = 1.70158;
    private static final double BACK_C2 = BACK_C1 * 1.525;
    private static final double BACK_C3 = BACK_C1 + 1;
    public static final DoubleUnaryOperator IN_BACK = t -> BACK_C3 * t * t * t - BACK_C1 * t * t;
    public static final DoubleUnaryOperator OUT_BACK = t -> 1 + BACK_C3 * Math.pow(t - 1, 3) + BACK_C1 * Math.pow(t - 1, 2);
    public static final DoubleUnaryOperator IN_OUT_BACK = t -> t < 0.5
            ? (Math.pow(2 * t, 2) * ((BACK_C2 + 1) * 2 * t - BACK_C2)) / 2
            : (Math.pow(2 * t - 2, 2) * ((BACK_C2 + 1) * (t * 2 - 2) + BACK_C2) + 2) / 2;
    public static final DoubleUnaryOperator OUT_IN_BACK = outIn(IN_BACK);

    // ===== Elastic =====
    private static final double ELASTIC_C4 = (2 * Math.PI) / 3;
    private static final double ELASTIC_C5 = (2 * Math.PI) / 4.5;
    public static final DoubleUnaryOperator IN_ELASTIC = t -> {
        if (t == 0) return 0;
        if (t == 1) return 1;
        return -Math.pow(2, 10 * t - 10) * Math.sin((t * 10 - 10.75) * ELASTIC_C4);
    };
    public static final DoubleUnaryOperator OUT_ELASTIC = t -> {
        if (t == 0) return 0;
        if (t == 1) return 1;
        return Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * ELASTIC_C4) + 1;
    };
    public static final DoubleUnaryOperator IN_OUT_ELASTIC = t -> {
        if (t == 0) return 0;
        if (t == 1) return 1;
        return t < 0.5
                ? -(Math.pow(2, 20 * t - 10) * Math.sin((20 * t - 11.125) * ELASTIC_C5)) / 2
                : (Math.pow(2, -20 * t + 10) * Math.sin((20 * t - 11.125) * ELASTIC_C5)) / 2 + 1;
    };
    public static final DoubleUnaryOperator OUT_IN_ELASTIC = outIn(IN_ELASTIC);

    // ===== Bounce =====
    public static final DoubleUnaryOperator OUT_BOUNCE = Easing::bounceOut;
    public static final DoubleUnaryOperator IN_BOUNCE = t -> 1 - bounceOut(1 - t);
    public static final DoubleUnaryOperator IN_OUT_BOUNCE = t -> t < 0.5
            ? (1 - bounceOut(1 - 2 * t)) / 2
            : (1 + bounceOut(2 * t - 1)) / 2;
    public static final DoubleUnaryOperator OUT_IN_BOUNCE = outIn(IN_BOUNCE);

    private static double bounceOut(double t) {
        double n1 = 7.5625, d1 = 2.75;
        if (t < 1 / d1)        return n1 * t * t;
        else if (t < 2 / d1) { t -= 1.5 / d1;  return n1 * t * t + 0.75; }
        else if (t < 2.5 / d1){ t -= 2.25 / d1; return n1 * t * t + 0.9375; }
        else                  { t -= 2.625 / d1; return n1 * t * t + 0.984375; }
    }

    // ===== Power (configurable exponent) =====
    public static DoubleUnaryOperator inPower(double p) {
        return t -> Math.pow(t, p);
    }
    public static DoubleUnaryOperator outPower(double p) {
        return t -> 1 - Math.pow(1 - t, p);
    }
    public static DoubleUnaryOperator inOutPower(double p) {
        return t -> t < 0.5
                ? Math.pow(2, p - 1) * Math.pow(t, p)
                : 1 - Math.pow(-2 * t + 2, p) / 2;
    }
    public static DoubleUnaryOperator outInPower(double p) {
        return outIn(inPower(p));
    }

    // ===== Bezier (cubic) — defaults match CSS "ease" family =====
    /** Approximation of CSS {@code ease-in} via cubic-bezier. */
    public static final DoubleUnaryOperator BEZIER_IN = bezier(0.42, 0, 1, 1);
    /** Approximation of CSS {@code ease-out} via cubic-bezier. */
    public static final DoubleUnaryOperator BEZIER_OUT = bezier(0, 0, 0.58, 1);
    /** Approximation of CSS {@code ease-in-out} via cubic-bezier. */
    public static final DoubleUnaryOperator BEZIER_IN_OUT = bezier(0.42, 0, 0.58, 1);
    public static final DoubleUnaryOperator BEZIER_OUT_IN = outIn(BEZIER_IN);

    /**
     * Cubic-bezier easing parameterized by two control points {@code (x1, y1)}
     * and {@code (x2, y2)}, in {@code [0, 1]}. Mirrors the CSS
     * {@code cubic-bezier()} timing function.
     *
     * <p>Solves for {@code t} given input {@code x} via Newton-Raphson then
     * evaluates {@code y(t)}. Falls back to a few bisection steps if Newton
     * struggles near horizontal regions.
     */
    public static DoubleUnaryOperator bezier(double x1, double y1, double x2, double y2) {
        return x -> {
            if (x <= 0) return 0;
            if (x >= 1) return 1;
            double t = solveBezierT(x, x1, x2);
            return bezierComponent(t, y1, y2);
        };
    }

    private static double bezierComponent(double t, double a, double b) {
        // Cubic Bezier from (0,0) to (1,1) with controls (·, a) and (·, b).
        // P(t) = 3(1-t)²t·a + 3(1-t)t²·b + t³
        double mt = 1 - t;
        return 3 * mt * mt * t * a + 3 * mt * t * t * b + t * t * t;
    }

    private static double solveBezierT(double x, double x1, double x2) {
        double t = x; // initial guess
        for (int i = 0; i < 8; i++) {
            double currentX = bezierComponent(t, x1, x2);
            double dx = currentX - x;
            if (Math.abs(dx) < 1e-6) return t;
            double slope = 3 * (1 - t) * (1 - t) * x1 + 6 * (1 - t) * t * (x2 - x1) + 3 * t * t * (1 - x2);
            if (Math.abs(slope) < 1e-6) break;
            t -= dx / slope;
        }
        // Bisection fallback
        double lo = 0, hi = 1;
        for (int i = 0; i < 20; i++) {
            t = (lo + hi) / 2;
            double currentX = bezierComponent(t, x1, x2);
            if (currentX < x) lo = t;
            else hi = t;
        }
        return t;
    }

    // ===== Steps (discrete) =====
    /** {@link #steps(int, boolean) steps(1, false)} — single jump at the start. */
    public static final DoubleUnaryOperator STEPS_START = steps(1, true);
    /** {@link #steps(int, boolean) steps(1, true)}  — single jump at the end. */
    public static final DoubleUnaryOperator STEPS_END = steps(1, false);

    /**
     * Stair-step easing with {@code n} discrete steps. When {@code jumpStart}
     * is true, the curve jumps at the start of each step (CSS {@code step-start}
     * semantics for {@code n=1}); otherwise it jumps at the end ({@code step-end}).
     */
    public static DoubleUnaryOperator steps(int n, boolean jumpStart) {
        if (n < 1) throw new IllegalArgumentException("steps n must be >= 1");
        return t -> {
            if (t <= 0) return jumpStart ? 1.0 / n : 0;
            if (t >= 1) return 1;
            double scaled = t * n;
            double k = jumpStart ? Math.ceil(scaled) : Math.floor(scaled);
            return k / n;
        };
    }

    // ===== Composition helpers =====

    /**
     * Build an OUT_IN variant from an IN curve: apply the OUT mirror in the
     * first half, then the IN curve in the second half.
     */
    public static DoubleUnaryOperator outIn(DoubleUnaryOperator inCurve) {
        DoubleUnaryOperator outCurve = reverse(inCurve);
        return t -> t < 0.5
                ? outCurve.applyAsDouble(2 * t) / 2
                : 0.5 + inCurve.applyAsDouble(2 * t - 1) / 2;
    }

    /** Mirror an IN easing into its OUT counterpart: {@code 1 - in(1 - t)}. */
    public static DoubleUnaryOperator reverse(DoubleUnaryOperator inCurve) {
        return t -> 1 - inCurve.applyAsDouble(1 - t);
    }

    /** Combine two curves: first half from {@code first} (rescaled) then second from {@code second}. */
    public static DoubleUnaryOperator combine(DoubleUnaryOperator first, DoubleUnaryOperator second) {
        return t -> t < 0.5
                ? first.applyAsDouble(2 * t) / 2
                : 0.5 + second.applyAsDouble(2 * t - 1) / 2;
    }

    /** Configurable power easing — sugar for {@link #inPower(double)}. */
    public static DoubleUnaryOperator power(double exponent) {
        return inPower(exponent);
    }

    private Easing() {}
}
