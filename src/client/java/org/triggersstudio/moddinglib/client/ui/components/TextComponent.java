package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.triggersstudio.moddinglib.client.ui.styling.Style;
import org.triggersstudio.moddinglib.client.ui.styling.Size;

import java.util.function.Supplier;

/**
 * Text component for displaying text content.
 *
 * <p>The content is resolved lazily through a {@link Supplier} on every frame,
 * so passing a {@code State<String>} (or any supplier) makes the text update
 * automatically as the source changes.</p>
 */
public class TextComponent extends UIComponent {
    private final Supplier<String> contentSupplier;

    public TextComponent(String content, Style style) {
        this(() -> content != null ? content : "", style);
    }

    public TextComponent(Supplier<String> contentSupplier, Style style) {
        super(style);
        this.contentSupplier = contentSupplier != null ? contentSupplier : () -> "";
    }

    private String resolve() {
        String s = contentSupplier.get();
        return s != null ? s : "";
    }

    /**
     * Build the {@link Text} payload that gets passed to the renderer:
     * a literal carrying the resolved string, with the font from
     * {@link Style#getFont()} applied when set so the vanilla
     * {@code TextRenderer} routes glyph lookup through the right font
     * provider.
     */
    private Text styledText(String content) {
        MutableText t = Text.literal(content);
        Identifier font = style.getFont();
        if (font != null) {
            t = t.fillStyle(net.minecraft.text.Style.EMPTY.withFont(font));
        }
        return t;
    }

    @Override
    public MeasureResult measure(int maxWidth, int maxHeight) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        // Width must be measured through the same Text path the renderer
        // uses, otherwise a custom font with non-vanilla glyph widths
        // would lay out at the wrong size.
        int textWidth = textRenderer.getWidth(styledText(resolve()));
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

        String displayText = resolve();
        if (style.isBold()) {
            // Bold via §l — works the same for vanilla and resource-pack
            // fonts. We send the formatting code as a sibling so the
            // font-style we baked into the parent still propagates.
            MutableText bold = Text.literal("§l").append(styledText(displayText));
            drawContext.drawText(textRenderer, bold, textX, textY, style.getTextColor(), true);
        } else {
            drawContext.drawText(textRenderer, styledText(displayText), textX, textY, style.getTextColor(), false);
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

