package org.triggersstudio.moddinglib.client.ui.styling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A paint describes how a region should be filled — either a single ARGB
 * color or a multi-stop gradient. {@code Paint} is consumed by the rendering
 * code (see {@code PaintRenderer}) and stored on {@link Style} for both the
 * background and the text fill.
 *
 * <p>All gradient implementations carry their stops sorted by ascending
 * offset and clamped to {@code [0, 1]}. Construct via the static factories
 * — they validate input — rather than the records directly.
 */
public sealed interface Paint
        permits Paint.Solid, Paint.LinearGradient, Paint.RadialGradient, Paint.ConicGradient {

    /**
     * @return the ARGB value at {@code t ∈ [0, 1]} along the paint's
     * conceptual axis. For {@link Solid} this is just the color; for
     * gradients it interpolates between the two stops bracketing {@code t}.
     */
    int sample(double t);

    /**
     * @return a representative ARGB value — the first solid color or the
     * first stop of the gradient. Used as the back-compat fallback for
     * {@code Style.getBackgroundColor()}.
     */
    int representativeColor();

    /**
     * Sample the paint at a specific point inside the bounding rect this
     * paint is being drawn into. {@code (localX, localY)} are pixel
     * coordinates relative to the rect's top-left; {@code rectW × rectH}
     * is the rect's full size.
     *
     * <p>This is the 2D entry point both the rect renderer and per-glyph
     * text rendering go through, so all paint types (linear / radial /
     * conic) get a consistent shape regardless of where they're applied.
     */
    int sampleAt(double localX, double localY, int rectW, int rectH);

    // --------- Records ---------

    record Solid(int argb) implements Paint {
        @Override
        public int sample(double t) { return argb; }

        @Override
        public int representativeColor() { return argb; }

        @Override
        public int sampleAt(double localX, double localY, int rectW, int rectH) { return argb; }
    }

    /**
     * A single color anchor in a gradient. {@code offset} is in {@code [0, 1]}.
     */
    record Stop(double offset, int argb) {
        public Stop {
            if (Double.isNaN(offset)) {
                throw new IllegalArgumentException("Stop offset must not be NaN");
            }
            offset = Math.max(0.0, Math.min(1.0, offset));
        }
    }

    /**
     * Gradient along a straight axis at {@code angleDegrees} from the
     * positive X axis (clockwise on screen, so 0° = left→right,
     * 90° = top→bottom, 180° = right→left, 270° = bottom→top). Stops are
     * sampled along that axis projected onto the bounding rect.
     */
    record LinearGradient(double angleDegrees, List<Stop> stops) implements Paint {
        public LinearGradient {
            stops = sortedStopsCopy(stops);
        }

        @Override
        public int sample(double t) { return Paint.lerpStops(stops, t); }

        @Override
        public int representativeColor() { return stops.get(0).argb(); }

        @Override
        public int sampleAt(double localX, double localY, int rectW, int rectH) {
            // Project the point onto the gradient axis; the four corners of
            // the rect bracket the projection range so t = 0 sits at the
            // furthest-back corner and t = 1 at the furthest-forward one.
            double rad = Math.toRadians(angleDegrees);
            double dx = Math.cos(rad);
            double dy = Math.sin(rad);
            double p00 = 0;
            double p10 = rectW * dx;
            double p01 = rectH * dy;
            double p11 = rectW * dx + rectH * dy;
            double pMin = Math.min(Math.min(p00, p10), Math.min(p01, p11));
            double pMax = Math.max(Math.max(p00, p10), Math.max(p01, p11));
            double pSpan = pMax - pMin;
            if (pSpan <= 0) return representativeColor();
            double t = (localX * dx + localY * dy - pMin) / pSpan;
            return sample(t);
        }
    }

    /**
     * Radial gradient centered at fractional coordinates {@code (cx, cy)} in
     * the bounding rect (both in {@code [0, 1]}, where {@code (0.5, 0.5)} is
     * the center). The disc extends to {@code rx} of the rect's half-width
     * and {@code ry} of its half-height — pass {@code rx == ry == 1.0} to
     * fill all the way to the longest corner.
     */
    record RadialGradient(double cx, double cy, double rx, double ry, List<Stop> stops) implements Paint {
        public RadialGradient {
            stops = sortedStopsCopy(stops);
            if (rx <= 0 || ry <= 0) {
                throw new IllegalArgumentException("Radial gradient radii must be positive");
            }
        }

        @Override
        public int sample(double t) { return Paint.lerpStops(stops, t); }

        @Override
        public int representativeColor() { return stops.get(0).argb(); }

        @Override
        public int sampleAt(double localX, double localY, int rectW, int rectH) {
            double cxAbs = cx * rectW;
            double cyAbs = cy * rectH;
            double rxAbs = Math.max(rx * rectW, 1e-6);
            double ryAbs = Math.max(ry * rectH, 1e-6);
            double dxN = (localX - cxAbs) / rxAbs;
            double dyN = (localY - cyAbs) / ryAbs;
            return sample(Math.sqrt(dxN * dxN + dyN * dyN));
        }
    }

    /**
     * Conic (a.k.a. angular) gradient sweeping {@code 360°} clockwise around
     * fractional center {@code (cx, cy)}, starting from
     * {@code startAngleDegrees} (measured the same way as
     * {@link LinearGradient}: 0° = right, increasing clockwise).
     */
    record ConicGradient(double cx, double cy, double startAngleDegrees, List<Stop> stops) implements Paint {
        public ConicGradient {
            stops = sortedStopsCopy(stops);
        }

        @Override
        public int sample(double t) { return Paint.lerpStops(stops, t); }

        @Override
        public int representativeColor() { return stops.get(0).argb(); }

        @Override
        public int sampleAt(double localX, double localY, int rectW, int rectH) {
            double cxAbs = cx * rectW;
            double cyAbs = cy * rectH;
            double startRad = Math.toRadians(startAngleDegrees);
            double TWO_PI = Math.PI * 2.0;
            double a = Math.atan2(localY - cyAbs, localX - cxAbs);
            double t = ((a - startRad) % TWO_PI + TWO_PI) % TWO_PI / TWO_PI;
            return sample(t);
        }
    }

    // --------- Factories ---------

    static Paint solid(int argb) {
        return new Solid(argb);
    }

    static Stop stop(double offset, int argb) {
        return new Stop(offset, argb);
    }

    /**
     * Convenience for the common case "just two colors, evenly spread":
     * builds stops {@code [(0.0, from), (1.0, to)]}.
     */
    static LinearGradient linear(double angleDegrees, int from, int to) {
        return new LinearGradient(angleDegrees, List.of(new Stop(0.0, from), new Stop(1.0, to)));
    }

    static LinearGradient linear(double angleDegrees, Stop... stops) {
        return new LinearGradient(angleDegrees, List.of(stops));
    }

    static LinearGradient linear(double angleDegrees, List<Stop> stops) {
        return new LinearGradient(angleDegrees, stops);
    }

    /**
     * Radial gradient centered on the rect, reaching the corners ({@code rx = ry = 1}).
     */
    static RadialGradient radial(int from, int to) {
        return new RadialGradient(0.5, 0.5, 1.0, 1.0,
                List.of(new Stop(0.0, from), new Stop(1.0, to)));
    }

    static RadialGradient radial(double cx, double cy, double rx, double ry, Stop... stops) {
        return new RadialGradient(cx, cy, rx, ry, List.of(stops));
    }

    static RadialGradient radial(double cx, double cy, double rx, double ry, List<Stop> stops) {
        return new RadialGradient(cx, cy, rx, ry, stops);
    }

    static ConicGradient conic(double startAngleDegrees, Stop... stops) {
        return new ConicGradient(0.5, 0.5, startAngleDegrees, List.of(stops));
    }

    static ConicGradient conic(double cx, double cy, double startAngleDegrees, Stop... stops) {
        return new ConicGradient(cx, cy, startAngleDegrees, List.of(stops));
    }

    static ConicGradient conic(double cx, double cy, double startAngleDegrees, List<Stop> stops) {
        return new ConicGradient(cx, cy, startAngleDegrees, stops);
    }

    // --------- Helpers (package-visible) ---------

    /**
     * Validate, copy, clamp, and sort a list of stops. We never mutate the
     * caller's list and we make sure the renderer can do a binary search on
     * a sorted snapshot without re-sorting per frame.
     */
    private static List<Stop> sortedStopsCopy(List<Stop> stops) {
        if (stops == null || stops.size() < 2) {
            throw new IllegalArgumentException("A gradient needs at least 2 stops");
        }
        List<Stop> copy = new ArrayList<>(stops);
        copy.sort(Comparator.comparingDouble(Stop::offset));
        return List.copyOf(copy);
    }

    /**
     * Linearly interpolate ARGB across a sorted stop list, clamping
     * {@code t} to the first/last stop outside {@code [stops[0].offset, stops[N-1].offset]}.
     */
    private static int lerpStops(List<Stop> stops, double t) {
        if (Double.isNaN(t)) t = 0;
        Stop first = stops.get(0);
        Stop last = stops.get(stops.size() - 1);
        if (t <= first.offset()) return first.argb();
        if (t >= last.offset()) return last.argb();

        // Linear scan — gradient stop counts are small (<10 in practice).
        for (int i = 1; i < stops.size(); i++) {
            Stop b = stops.get(i);
            if (t <= b.offset()) {
                Stop a = stops.get(i - 1);
                double span = b.offset() - a.offset();
                double local = span <= 0 ? 0 : (t - a.offset()) / span;
                return lerpArgb(a.argb(), b.argb(), local);
            }
        }
        return last.argb();
    }

    /**
     * Channel-wise linear interpolation in straight (non-premultiplied)
     * ARGB. Good enough for UI gradients; would benefit from gamma-correct
     * mixing if we ever care about photographic-accuracy color blends.
     */
    private static int lerpArgb(int a, int b, double t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        int oa = (int) Math.round(aa + (ba - aa) * t);
        int or = (int) Math.round(ar + (br - ar) * t);
        int og = (int) Math.round(ag + (bg - ag) * t);
        int ob = (int) Math.round(ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }
}
