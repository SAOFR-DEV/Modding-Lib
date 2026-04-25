package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.triggersstudio.moddinglib.client.ui.styling.Style;
import org.triggersstudio.moddinglib.client.ui.styling.Size;

/**
 * Image component for displaying textures/images.
 */
public class ImageComponent extends UIComponent {
    private final Identifier texture;
    private int textureWidth = 256;
    private int textureHeight = 256;

    public ImageComponent(Identifier texture, Style style) {
        super(style);
        this.texture = texture;
    }

    public ImageComponent(Identifier texture, int textureWidth, int textureHeight, Style style) {
        super(style);
        this.texture = texture;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    @Override
    public MeasureResult measure(int maxWidth, int maxHeight) {
        int totalWidth = textureWidth + style.getPadding().getHorizontal();
        int totalHeight = textureHeight + style.getPadding().getVertical();

        // Apply style constraints
        int resultWidth = applyConstraint(totalWidth, style.getWidth(), maxWidth);
        int resultHeight = applyConstraint(totalHeight, style.getHeight(), maxHeight);

        return new MeasureResult(resultWidth, resultHeight);
    }

    @Override
    public void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public void render(DrawContext drawContext) {
        // Draw background
        if (style.getBackgroundColor() != 0x00_00_00_00) {
            drawContext.fill(x, y, x + width, y + height, style.getBackgroundColor());
        }

        // Draw texture
        int imageX = x + style.getPadding().left;
        int imageY = y + style.getPadding().top;
        int imageWidth = width - style.getPadding().getHorizontal();
        int imageHeight = height - style.getPadding().getVertical();

        drawContext.drawTexture(RenderLayer::getGuiTextured, texture, imageX, imageY, 0.0f, 0.0f, imageWidth, imageHeight, textureWidth, textureHeight);

        // Draw border
        if (style.getBorderWidth() > 0) {
            drawBorder(drawContext);
        }
    }

    private void drawBorder(DrawContext drawContext) {
        int borderWidth = style.getBorderWidth();
        int color = style.getBorderColor();

        // Top border
        drawContext.fill(x, y, x + width, y + borderWidth, color);
        // Bottom border
        drawContext.fill(x, y + height - borderWidth, x + width, y + height, color);
        // Left border
        drawContext.fill(x, y, x + borderWidth, y + height, color);
        // Right border
        drawContext.fill(x + width - borderWidth, y, x + width, y + height, color);
    }

    /**
     * Apply style constraint to measured value
     */
    protected int applyConstraint(int measured, int constraint, int max) {
        if (Size.isMatchParent(constraint)) {
            return max;
        } else if (Size.isWrapContent(constraint)) {
            return measured;
        } else {
            // Fixed size
            return Math.min(constraint, max);
        }
    }

    public void setTextureSize(int width, int height) {
        this.textureWidth = width;
        this.textureHeight = height;
    }
}

