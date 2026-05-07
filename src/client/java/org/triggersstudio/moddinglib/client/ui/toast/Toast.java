package org.triggersstudio.moddinglib.client.ui.toast;

/**
 * Single toast notification. Holds the message, type, total visible duration,
 * and the timestamp when it entered the active queue. Lifecycle progress is
 * computed lazily by {@link ToastManager} from {@link System#currentTimeMillis()}.
 *
 * <p>Lifecycle phases (durations in ms):
 * <pre>
 *   [ slide-in : SLIDE_MS ]
 *   [ visible  : durationMs - 2*SLIDE_MS ]
 *   [ slide-out + fade : SLIDE_MS ]
 * </pre>
 */
public final class Toast {
    public static final long SLIDE_MS = 250L;
    public static final long DEFAULT_DURATION_MS = 3500L;

    public final String message;
    public final ToastType type;
    public final long durationMs;
    public final long startMs;

    public Toast(String message, ToastType type, long durationMs) {
        this.message = message != null ? message : "";
        this.type = type != null ? type : ToastType.INFO;
        this.durationMs = Math.max(2 * SLIDE_MS + 200, durationMs);
        this.startMs = System.currentTimeMillis();
    }

    public long elapsed() {
        return System.currentTimeMillis() - startMs;
    }

    public boolean isFinished() {
        return elapsed() >= durationMs;
    }

    /** 0..1 slide progress (eased outside this class). 1 once fully on-screen. */
    public double slideInProgress() {
        long e = elapsed();
        if (e >= SLIDE_MS) return 1.0;
        return (double) e / (double) SLIDE_MS;
    }

    /** 1..0 fade-out progress in the final SLIDE_MS window. 1 before exit phase. */
    public double exitProgress() {
        long e = elapsed();
        long exitStart = durationMs - SLIDE_MS;
        if (e < exitStart) return 1.0;
        if (e >= durationMs) return 0.0;
        return 1.0 - (double) (e - exitStart) / (double) SLIDE_MS;
    }
}
