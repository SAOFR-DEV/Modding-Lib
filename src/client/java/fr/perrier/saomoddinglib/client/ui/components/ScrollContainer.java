package fr.perrier.saomoddinglib.client.ui.components;

import net.minecraft.client.gui.DrawContext;
import fr.perrier.saomoddinglib.client.ui.styling.Style;
import fr.perrier.saomoddinglib.client.ui.layout.LayoutType;

/**
 * Base class for single-axis scroll containers. Holds the shared logic
 * (single-child constraint, background, scissor clipping, scrollbar thumb math)
 * and delegates axis-specific work to {@link VerticalScrollContainer}
 * and {@link HorizontalScrollContainer}.
 */
public abstract class ScrollContainer extends Container {
    protected int scroll = 0;
    protected int maxScroll = 0;
    protected final int scrollSpeed = 20;
    protected final int scrollbarThickness = 8;


    protected ScrollContainer(Style style) {
        super(style, LayoutType.VERTICAL, 0);
    }

    @Override
    public MeasureResult measure(int maxWidth, int maxHeight) {
        if (children.isEmpty()) {
            return new MeasureResult(0, 0);
        }
        int resultWidth = applyConstraint(0, style.getWidth(), maxWidth);
        int resultHeight = applyConstraint(0, style.getHeight(), maxHeight);
        return new MeasureResult(resultWidth, resultHeight);
    }

    @Override
    public void render(DrawContext drawContext) {
        if (style.getBackgroundColor() != 0x00_00_00_00) {
            drawContext.fill(x, y, x + width, y + height, style.getBackgroundColor());
        }

        enableScissor(drawContext);
        for (UIComponent child : children) {
            child.render(drawContext);
        }
        drawContext.disableScissor();

        if (maxScroll > 0) {
            drawScrollbar(drawContext);
        }
    }

    protected abstract void enableScissor(DrawContext drawContext);

    protected abstract void drawScrollbar(DrawContext drawContext);

    public int getScroll() {
        return scroll;
    }

    public void setScroll(int value) {
        this.scroll = Math.max(0, Math.min(maxScroll, value));
    }
}