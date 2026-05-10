package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.triggersstudio.moddinglib.client.ui.rendering.PaintRenderer;
import org.triggersstudio.moddinglib.client.ui.styling.Paint;
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
        // Draw background if specified — Paint dispatches to a fast solid
        // path or to gradient scanline rendering depending on what's set.
        if (style.getBackgroundColor() != 0x00_00_00_00) {
            PaintRenderer.fillRect(drawContext, x, y, width, height,
                    style.getBackgroundPaint(), style.getBorderRadius());
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
        Paint textPaint = style.getTextPaint();
        if (textPaint instanceof Paint.Solid) {
            // Fast path — single drawText call. Identical to the historical
            // behavior, including shadow on bold text.
            if (style.isBold()) {
                MutableText bold = Text.literal("§l").append(styledText(displayText));
                drawContext.drawText(textRenderer, bold, textX, textY, style.getTextColor(), true);
            } else {
                drawContext.drawText(textRenderer, styledText(displayText), textX, textY, style.getTextColor(), false);
            }
        } else {
            drawGradientText(drawContext, textRenderer, textPaint, displayText, textX, textY);
        }
    }

    /**
     * Per-glyph gradient text. Each codepoint is drawn individually with the
     * paint sampled at the glyph's center, so multi-stop / radial / conic
     * gradients all work uniformly.
     *
     * <p>Bold is preserved by routing through MC's vanilla bold mechanism
     * (a {@code §l} prefix on every per-glyph {@code Text}) — that way
     * width measurement and the characteristic 1px double-stroke stay
     * consistent with the non-gradient path.
     */
    private void drawGradientText(DrawContext ctx, TextRenderer renderer, Paint paint,
                                  String text, int textX, int textY) {
        if (text.isEmpty()) return;

        // Measure the full string once so per-glyph sampling has a stable
        // denominator. Bold widens each glyph by 1 px, so we measure with
        // the same prefix the per-glyph draw will use.
        String prefix = style.isBold() ? "§l" : "";
        int totalWidth = renderer.getWidth(styledText(prefix + text));
        int textHeight = 9; // TextRenderer.fontHeight in 1.21.x

        int cursorX = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int charLen = Character.charCount(codePoint);
            String glyph = text.substring(i, i + charLen);
            Text styled = styledText(prefix + glyph);
            int glyphWidth = renderer.getWidth(styled);

            double centerX = cursorX + glyphWidth / 2.0;
            double centerY = textHeight / 2.0;
            int color = paint.sampleAt(centerX, centerY, totalWidth, textHeight);

            ctx.drawText(renderer, styled, textX + cursorX, textY, color, false);

            cursorX += glyphWidth;
            i += charLen;
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

