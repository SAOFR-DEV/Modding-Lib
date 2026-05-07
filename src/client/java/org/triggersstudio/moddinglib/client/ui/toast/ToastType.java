package org.triggersstudio.moddinglib.client.ui.toast;

/**
 * Visual category for a toast notification. Drives the accent (left bar) and
 * default text label rendered before the message.
 */
public enum ToastType {
    INFO    (0xFF_4A_8C_C8, "INFO"),
    SUCCESS (0xFF_4A_C8_7A, "SUCCESS"),
    WARNING (0xFF_E0_A0_3A, "WARNING"),
    ERROR   (0xFF_C8_4A_4A, "ERROR");

    public final int accentColor;
    public final String label;

    ToastType(int accentColor, String label) {
        this.accentColor = accentColor;
        this.label = label;
    }
}
