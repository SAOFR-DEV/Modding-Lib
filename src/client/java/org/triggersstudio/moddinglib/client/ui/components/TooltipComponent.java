package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.triggersstudio.moddinglib.client.ui.styling.Style;

/**
 * Wraps a child and shows a tooltip popup after the cursor has hovered over
 * the child for {@code delayMs} (default 500). The popup follows the cursor
 * with a small offset and auto-flips when it would overflow the screen edge.
 *
 * <p>The popup is drawn through the screen's {@link org.triggersstudio.moddinglib.client.ui.context.UIContext}
 * overlay queue, so it always appears above siblings later in the tree.
 *
 * <p>The tooltip text supports newlines for multi-line content. Layout and
 * lifecycle of the wrapped child are unaffected.
 */
public class TooltipComponent extends Container {

    private static final long DEFAULT_DELAY_MS = 500L;
    private static final int CURSOR_OFFSET_X = 12;
    private static final int CURSOR_OFFSET_Y = 12;
    private static final int SCREEN_MARGIN = 4;
    private static final int FADE_DURATION_MS = 120;

    private final String text;
    private final long delayMs;
    private final Style tooltipStyle;

    private boolean hovered = false;
    private long hoverStartMs = 0L;
    private double cursorX, cursorY;
    private long popupShownAtMs = 0L;

    public TooltipComponent(String text, UIComponent child, Style tooltipStyle, long delayMs) {
        super(Style.DEFAULT, org.triggersstudio.moddinglib.client.ui.layout.LayoutType.VERTICAL, 0);
        this.text = text != null ? text : "";
        this.tooltipStyle = tooltipStyle != null ? tooltipStyle : defaultTooltipStyle();
        this.delayMs = delayMs > 0 ? delayMs : DEFAULT_DELAY_MS;
        if (child != null) {
            addChild(child);
        }
    }

    public TooltipComponent(String text, UIComponent child) {
        this(text, child, null, DEFAULT_DELAY_MS);
    }

    public TooltipComponent(String text, UIComponent child, Style tooltipStyle) {
        this(text, child, tooltipStyle, DEFAULT_DELAY_MS);
    }

    public static Style defaultTooltipStyle() {
        return Style.backgroundColor(0xF0_10_10_18)
                .textColor(0xFF_FF_FF_FF)
                .padding(4, 6)
                .border(0xFF_55_55_88, 1)
                .build();
    }

    @Override
    public void onMouseMove(double mx, double my) {
        super.onMouseMove(mx, my);
        cursorX = mx;
        cursorY = my;
        boolean inside = isPointInside(mx, my);
        if (inside && !hovered) {
            hoverStartMs = System.currentTimeMillis();
            popupShownAtMs = 0L;
        }
        if (!inside) {
            popupShownAtMs = 0L;
        }
        hovered = inside;
    }

    @Override
    public void render(DrawContext drawContext) {
        super.render(drawContext);
        if (!hovered || context == null || text.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - hoverStartMs < delayMs) {
            return;
        }
        if (popupShownAtMs == 0L) {
            popupShownAtMs = now;
        }
        // Capture popup-instant state — render call runs after the tree.
        final double cx = cursorX;
        final double cy = cursorY;
        final long shownAt = popupShownAtMs;
        context.deferOverlay(ctx -> drawTooltip(ctx, cx, cy, shownAt));
    }

    private void drawTooltip(DrawContext drawContext, double cx, double cy, long shownAt) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String[] lines = text.split("\n");
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, tr.getWidth(line));
        }
        int lineHeight = 10;
        int textHeight = lines.length * lineHeight + (lines.length - 1) * 2;

        int padH = tooltipStyle.getPadding().getHorizontal();
        int padV = tooltipStyle.getPadding().getVertical();
        int boxW = textWidth + padH;
        int boxH = textHeight + padV;

        int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();

        int boxX = (int) cx + CURSOR_OFFSET_X;
        int boxY = (int) cy + CURSOR_OFFSET_Y;
        // Auto-flip horizontally if overflow.
        if (boxX + boxW + SCREEN_MARGIN > screenW) {
            boxX = (int) cx - CURSOR_OFFSET_X - boxW;
        }
        // Auto-flip vertically if overflow.
        if (boxY + boxH + SCREEN_MARGIN > screenH) {
            boxY = (int) cy - CURSOR_OFFSET_Y - boxH;
        }
        // Clamp to screen.
        boxX = Math.max(SCREEN_MARGIN, Math.min(boxX, screenW - boxW - SCREEN_MARGIN));
        boxY = Math.max(SCREEN_MARGIN, Math.min(boxY, screenH - boxH - SCREEN_MARGIN));

        // Fade alpha based on time since popup first shown.
        long elapsed = System.currentTimeMillis() - shownAt;
        float fade = Math.min(1f, elapsed / (float) FADE_DURATION_MS);
        int bg = applyAlphaFactor(tooltipStyle.getBackgroundColor(), fade);
        int border = applyAlphaFactor(tooltipStyle.getBorderColor(), fade);
        int textColor = applyAlphaFactor(tooltipStyle.getTextColor(), fade);

        // Background + border.
        drawContext.fill(boxX, boxY, boxX + boxW, boxY + boxH, bg);
        if (tooltipStyle.getBorderWidth() > 0) {
            int bw = tooltipStyle.getBorderWidth();
            drawContext.fill(boxX, boxY, boxX + boxW, boxY + bw, border);
            drawContext.fill(boxX, boxY + boxH - bw, boxX + boxW, boxY + boxH, border);
            drawContext.fill(boxX, boxY, boxX + bw, boxY + boxH, border);
            drawContext.fill(boxX + boxW - bw, boxY, boxX + boxW, boxY + boxH, border);
        }

        int textX = boxX + tooltipStyle.getPadding().left;
        int textY = boxY + tooltipStyle.getPadding().top;
        for (int i = 0; i < lines.length; i++) {
            drawContext.drawText(tr, lines[i], textX, textY + i * (lineHeight + 2), textColor, true);
        }
    }

    private static int applyAlphaFactor(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int newA = Math.round(a * factor);
        return (argb & 0x00_FF_FF_FF) | (newA << 24);
    }
}
