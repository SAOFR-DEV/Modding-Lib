package fr.perrier.saomoddinglib.client.ui.styling;

/**
 * Static helper methods for Style creation and common configurations.
 *
 * Usage with static import:
 * <pre>
 * import static fr.perrier.saomoddinglib.client.ui.styling.Styles.*;
 *
 * Style style = padding(16)
 *     .backgroundColor(0xFF_1A_1A_1A)
 *     .fontSize(24)
 *     .build();
 * </pre>
 */
public class Styles {

    // Size constants for easy access
    public static final int WRAP_CONTENT = Size.WRAP_CONTENT;
    public static final int MATCH_PARENT = Size.MATCH_PARENT;

    // Alignment shortcuts
    public static final Alignment START = Alignment.START;
    public static final Alignment CENTER = Alignment.CENTER;
    public static final Alignment END = Alignment.END;
    public static final Alignment STRETCH = Alignment.STRETCH;

    // Common colors
    public static final int BLACK = 0xFF_00_00_00;
    public static final int WHITE = 0xFF_FF_FF_FF;
    public static final int RED = 0xFF_FF_00_00;
    public static final int GREEN = 0xFF_00_FF_00;
    public static final int BLUE = 0xFF_00_00_FF;
    public static final int TRANSPARENT = 0x00_00_00_00;

    // === Style shortcuts ===

    public static Style.Builder size(int width, int height) {
        return Style.builder().width(width).height(height);
    }

    public static Style.Builder width(int width) {
        return Style.width(width);
    }

    public static Style.Builder height(int height) {
        return Style.height(height);
    }

    public static Style.Builder padding(int all) {
        return Style.padding(all);
    }

    public static Style.Builder padding(int vertical, int horizontal) {
        return Style.padding(vertical, horizontal);
    }

    public static Style.Builder padding(int top, int right, int bottom, int left) {
        return Style.padding(top, right, bottom, left);
    }

    public static Style.Builder margin(int all) {
        return Style.margin(all);
    }

    public static Style.Builder margin(int vertical, int horizontal) {
        return Style.margin(vertical, horizontal);
    }

    public static Style.Builder margin(int top, int right, int bottom, int left) {
        return Style.margin(top, right, bottom, left);
    }

    public static Style.Builder backgroundColor(int color) {
        return Style.backgroundColor(color);
    }

    public static Style.Builder textColor(int color) {
        return Style.textColor(color);
    }

    public static Style.Builder fontSize(float size) {
        return Style.fontSize(size);
    }

    public static Style.Builder borderRadius(int radius) {
        return Style.borderRadius(radius);
    }

    public static Style.Builder border(int color, int width) {
        return Style.border(color, width);
    }

    public static Style.Builder align(Alignment horizontal, Alignment vertical) {
        return Style.align(horizontal, vertical);
    }

    public static Style.Builder onClick(fr.perrier.saomoddinglib.client.ui.events.ClickHandler handler) {
        return Style.onClick(handler);
    }

    public static Style.Builder opacity(float opacity) {
        return Style.opacity(opacity);
    }

    public static Style.Builder bold() {
        return Style.bold();
    }
}

