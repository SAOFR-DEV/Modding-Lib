package fr.perrier.saomoddinglib.client.ui.components;

import net.minecraft.client.gui.DrawContext;
import fr.perrier.saomoddinglib.client.ui.styling.Style;

/**
 * Base class for all UI components.
 * Manages lifecycle: measure -> layout -> render
 */
public abstract class UIComponent {
    protected Style style;
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean needsMeasure = true;
    protected boolean needsLayout = true;

    public UIComponent(Style style) {
        this.style = style != null ? style : Style.DEFAULT;
        this.x = 0;
        this.y = 0;
        this.width = 0;
        this.height = 0;
    }

    /**
     * Measure phase: determine the component's desired size
     */
    public abstract MeasureResult measure(int maxWidth, int maxHeight);

    /**
     * Layout phase: position the component and its children
     */
    public abstract void layout(int x, int y, int width, int height);

    /**
     * Render phase: draw the component
     */
    public abstract void render(DrawContext drawContext);

    /**
     * Handle mouse click event
     * @return true if event was consumed
     */
    public boolean onMouseClick(double x, double y, int button) {
        return false;
    }

    /**
     * Handle mouse scroll event
     * @return true if event was consumed
     */
    public boolean onMouseScroll(double x, double y, double scrollDelta) {
        return false;
    }

    /**
     * Handle mouse move event
     */
    public void onMouseMove(double x, double y) {
    }

    // Getters
    public Style getStyle() {
        return style;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isPointInside(double px, double py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    /**
     * Result of measurement phase
     */
    public static class MeasureResult {
        public final int width;
        public final int height;

        public MeasureResult(int width, int height) {
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
        }
    }
}

