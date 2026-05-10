package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.MinecraftClient;
import org.triggersstudio.moddinglib.client.ui.rendering.PaintRenderer;
import org.triggersstudio.moddinglib.client.ui.rendering.Shapes;
import org.triggersstudio.moddinglib.client.ui.styling.Paint;
import org.triggersstudio.moddinglib.client.ui.styling.Style;
import org.triggersstudio.moddinglib.client.ui.styling.Size;

/**
 * Button component with text and click handling.
 */
public class ButtonComponent extends UIComponent {
    private final String label;
    private boolean hovered;
    
    public ButtonComponent(String label, Style style) {
        super(style);
        this.label = label != null ? label : "Button";
        this.hovered = false;
    }
    
    @Override
    public MeasureResult measure(int maxWidth, int maxHeight) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        
        int textWidth = textRenderer.getWidth(label);
        int textHeight = 10;
        
        // Add padding and some minimum button size
        int minWidth = 60;
        int minHeight = 20;
        
        int totalWidth = Math.max(textWidth, minWidth) + style.getPadding().getHorizontal();
        int totalHeight = Math.max(textHeight, minHeight) + style.getPadding().getVertical();
        
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
        Paint bgPaint = style.getBackgroundPaint();
        int legacyColor = style.getBackgroundColor();

        // Hover-darken behavior is preserved for solid fills (the historical
        // case). For gradient backgrounds we leave the paint as-is — darkening
        // each stop would require a Paint.map(...) helper we don't have yet,
        // and most gradient buttons would prefer a styled overlay anyway.
        if (hovered && bgPaint instanceof Paint.Solid solid && solid.argb() != 0) {
            bgPaint = Paint.solid(darkenColor(solid.argb()));
        }
        int radius = style.getBorderRadius();

        if (legacyColor != 0x00_00_00_00) {
            PaintRenderer.fillRect(drawContext, x, y, width, height, bgPaint, radius);
        }
        if (style.getBorderWidth() > 0) {
            Shapes.drawRoundRectBorder(drawContext, x, y, width, height,
                    radius, style.getBorderWidth(), style.getBorderColor());
        }

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int textWidth = textRenderer.getWidth(label);
        int textHeight = 10;
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - textHeight) / 2;
        drawContext.drawText(textRenderer, label, textX, textY, style.getTextColor(), false);
    }
    
    @Override
    public boolean onMouseClick(double mx, double my, int button) {
        if (!isPointInside(mx, my)) {
            return false;
        }
        
        if (style.getClickHandler() != null) {
            style.getClickHandler().onClick(mx, my, button);
            return true;
        }
        
        return false;
    }
    
    @Override
    public void onMouseMove(double mx, double my) {
        hovered = isPointInside(mx, my);
    }
    
    /**
     * Darken a color for hover effect
     */
    private int darkenColor(int color) {
        int a = (color >> 24) & 0xFF;
        int r = Math.max(0, ((color >> 16) & 0xFF) - 30);
        int g = Math.max(0, ((color >> 8) & 0xFF) - 30);
        int b = Math.max(0, (color & 0xFF) - 30);
        return (a << 24) | (r << 16) | (g << 8) | b;
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
}
