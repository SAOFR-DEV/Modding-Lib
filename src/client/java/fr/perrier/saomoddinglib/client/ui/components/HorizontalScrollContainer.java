package fr.perrier.saomoddinglib.client.ui.components;

import net.minecraft.client.gui.DrawContext;
import fr.perrier.saomoddinglib.client.ui.styling.Style;

/**
 * Scroll container that scrolls its single child horizontally.
 * Height is fixed to the container's content height; width is unbounded so
 * the child can report its intrinsic width.
 */
public class HorizontalScrollContainer extends ScrollContainer {

    public HorizontalScrollContainer(Style style) {
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
        int contentWidth = width - style.getPadding().getHorizontal();
        int contentHeight = height - style.getPadding().getVertical() - scrollbarThickness;

        if (children.isEmpty()) {
            this.maxScroll = 0;
            return;
        }

        UIComponent child = children.get(0);
        var measured = child.measure(Integer.MAX_VALUE, contentHeight);
        child.layout(contentX - scroll, contentY, measured.width, contentHeight);

        this.maxScroll = Math.max(0, measured.width - contentWidth);
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
    }

    @Override
    protected void enableScissor(DrawContext drawContext) {
        drawContext.enableScissor(x, y, x + width, y + height - scrollbarThickness);
    }

    @Override
    protected void drawScrollbar(DrawContext drawContext) {
        int trackY = y + height - scrollbarThickness;
        drawContext.fill(x, trackY, x + width, y + height, 0xFF_2A_2A_2A);

        int thumbWidth = Math.max(10, (int) ((float) width / (maxScroll + width) * width));
        int thumbX = x + (int) ((float) scroll / maxScroll * (width - thumbWidth));
        drawContext.fill(thumbX, trackY + 1, thumbX + thumbWidth, y + height - 1, 0xFF_66_66_66);
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
        double adjustedX = mx + scroll;
        for (UIComponent child : children) {
            if (child.onMouseClick(adjustedX, my, button)) {
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