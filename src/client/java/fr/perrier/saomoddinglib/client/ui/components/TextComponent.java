package fr.perrier.saomoddinglib.client.ui.components;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.MinecraftClient;
import fr.perrier.saomoddinglib.client.ui.styling.Style;
import fr.perrier.saomoddinglib.client.ui.styling.Size;

/**
 * Text component for displaying text content.
 */
public class TextComponent extends UIComponent {
    private final String content;

    public TextComponent(String content, Style style) {
        super(style);
        this.content = content != null ? content : "";
    }

    @Override
    public MeasureResult measure(int maxWidth, int maxHeight) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        int textWidth = textRenderer.getWidth(content);
        int textHeight = 10; // Approximate height

        int totalWidth = textWidth + style.getPadding().getHorizontal();
        int totalHeight = textHeight + style.getPadding().getVertical();

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
        // Draw background if specified
        if (style.getBackgroundColor() != 0x00_00_00_00) {
            drawContext.fill(x, y, x + width, y + height, style.getBackgroundColor());
        }

        // Draw text
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int textX = x + style.getPadding().left;
        int textY = y + style.getPadding().top;

        // Apply opacity when rendering
        if (style.getOpacity() < 1.0f) {
            // For now, we'll just draw at full opacity
            // Full opacity support would require matrix stack manipulations
        }

        String displayText = content;
        if (style.isBold()) {
            // Minecraft doesn't have native bold text, so we'll render twice slightly offset
            drawContext.drawText(textRenderer, "§l" + displayText, textX, textY, style.getTextColor(), true);
        } else {
            drawContext.drawText(textRenderer, displayText, textX, textY, style.getTextColor(), false);
        }
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

