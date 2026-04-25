package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.gui.DrawContext;
import org.triggersstudio.moddinglib.client.ui.styling.Style;

/**
 * Scroll container that scrolls its single child vertically.
 * Width is fixed to the container's content width; height is unbounded so
 * the child can report its intrinsic height.
 */
public class VerticalScrollContainer extends ScrollContainer {

    public VerticalScrollContainer(Style style) {
        super(style);
    }

    @Override
    public void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        int contentX = x + style.getPadding().left;
        int contentY = y + style.getPadding().top;
        int contentWidth = width - style.getPadding().getHorizontal() - scrollbarThickness;
        int contentHeight = height - style.getPadding().getVertical();

        if (children.isEmpty()) {
            this.maxScroll = 0;
            return;
        }

        UIComponent child = children.get(0);
        var measured = child.measure(contentWidth, Integer.MAX_VALUE);
        child.layout(contentX, contentY - scroll, contentWidth, measured.height);

        this.maxScroll = Math.max(0, measured.height - contentHeight);
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
    }

    @Override
    protected void enableScissor(DrawContext drawContext) {
        drawContext.enableScissor(x, y, x + width - scrollbarThickness, y + height);
    }

    @Override
    protected void drawScrollbar(DrawContext drawContext) {
        int trackX = x + width - scrollbarThickness;
        drawContext.fill(trackX, y, x + width, y + height, 0xFF_2A_2A_2A);

        int thumbHeight = Math.max(10, (int) ((float) height / (maxScroll + height) * height));
        int thumbY = y + (int) ((float) scroll / maxScroll * (height - thumbHeight));
        drawContext.fill(trackX + 1, thumbY, x + width - 1, thumbY + thumbHeight, 0xFF_66_66_66);
    }

    @Override
    public boolean onMouseScroll(double mx, double my, double scrollDelta) {
        if (!isPointInside(mx, my)) {
            return false;
        }
        setScroll((int) (scroll - scrollDelta * scrollSpeed));
        return true;
    }

    @Override
    public boolean onMouseClick(double mx, double my, int button) {
        if (!isPointInside(mx, my)) {
            return false;
        }
        double adjustedY = my + scroll;
        for (UIComponent child : children) {
            if (child.onMouseClick(mx, adjustedY, button)) {
                return true;
            }
        }
        if (style.getClickHandler() != null) {
            style.getClickHandler().onClick(mx, my, button);
            return true;
        }
        return false;
    }
}
