package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import org.triggersstudio.moddinglib.client.ui.rendering.PaintRenderer;
import org.triggersstudio.moddinglib.client.ui.styling.Size;
import org.triggersstudio.moddinglib.client.ui.styling.Style;

import java.util.function.Supplier;

/**
 * Renders any {@link LivingEntity} (player, mob, ...) into a UI rect, using
 * the same scissored 3D path as the vanilla inventory paperdoll.
 *
 * <p>By default the entity body and head follow the cursor (vanilla behavior:
 * an atan-smoothed delta from the component center). Pass
 * {@code mouseTracksRotation=false} to render the entity from the front
 * (deltas forced to 0); the entity's own {@code bodyYaw / headYaw / pitch}
 * fields can then be set externally to drive a fixed pose.
 *
 * <p>The supplier is queried each frame, so the target entity can change at
 * runtime (e.g. {@code () -> MinecraftClient.getInstance().player} keeps
 * working across world joins / character swaps). When the supplier returns
 * {@code null} the component just paints its background — a {@link Skeleton}
 * sibling can sit underneath for a load placeholder.
 *
 * <p>Sizing: {@code entitySize} is the vanilla pixel-scale param (entity
 * height of 1 block ≈ {@code size} px on screen). Pass {@code 0} for an
 * auto-fit that targets ~85% of the inner box height for a 1.8m player.
 */
public class PlayerRenderComponent extends UIComponent {

    private static final int DEFAULT_WIDTH = 96;
    private static final int DEFAULT_HEIGHT = 128;

    private final Supplier<LivingEntity> entitySupplier;
    private final int entitySize;
    private final boolean mouseTracksRotation;
    private final float bottomOffset;

    public PlayerRenderComponent(Supplier<LivingEntity> entitySupplier, Style style) {
        this(entitySupplier, style, 0, true, 0f);
    }

    public PlayerRenderComponent(Supplier<LivingEntity> entitySupplier, Style style,
                                 int entitySize, boolean mouseTracksRotation, float bottomOffset) {
        super(style);
        this.entitySupplier = entitySupplier != null ? entitySupplier : () -> null;
        this.entitySize = Math.max(0, entitySize);
        this.mouseTracksRotation = mouseTracksRotation;
        this.bottomOffset = bottomOffset;
    }

    @Override
    public MeasureResult measure(int maxWidth, int maxHeight) {
        int w = applyConstraint(DEFAULT_WIDTH, style.getWidth(), maxWidth);
        int h = applyConstraint(DEFAULT_HEIGHT, style.getHeight(), maxHeight);
        return new MeasureResult(w, h);
    }

    @Override
    public void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public void render(DrawContext ctx) {
        if (style.getBackgroundColor() != 0) {
            PaintRenderer.fillRect(ctx, x, y, width, height,
                    style.getBackgroundPaint(), style.getBorderRadius());
        }

        LivingEntity entity = entitySupplier.get();
        if (entity != null) {
            int padL = style.getPadding().left;
            int padT = style.getPadding().top;
            int padR = style.getPadding().right;
            int padB = style.getPadding().bottom;
            int innerX1 = x + padL;
            int innerY1 = y + padT;
            int innerX2 = x + width - padR;
            int innerY2 = y + height - padB;
            if (innerX2 > innerX1 && innerY2 > innerY1) {
                int boundsH = innerY2 - innerY1;
                // Auto-fit: a 1.8m-tall player should occupy ~85% of the box.
                // Vanilla scale is "px per block-unit", so size = boundsH * 0.85 / 1.8.
                int size = entitySize > 0 ? entitySize : Math.max(8, (int) (boundsH * 0.47f));

                float mouseX;
                float mouseY;
                if (mouseTracksRotation) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    int sw = mc.getWindow().getScaledWidth();
                    int sh = mc.getWindow().getScaledHeight();
                    int ww = mc.getWindow().getWidth();
                    int wh = mc.getWindow().getHeight();
                    mouseX = ww > 0 ? (float) (mc.mouse.getX() * sw / (double) ww) : (innerX1 + innerX2) / 2f;
                    mouseY = wh > 0 ? (float) (mc.mouse.getY() * sh / (double) wh) : (innerY1 + innerY2) / 2f;
                } else {
                    // Synthesize cursor at the center -> deltas of 0 -> entity
                    // is shown facing front, no head pitch override.
                    mouseX = (innerX1 + innerX2) / 2f;
                    mouseY = (innerY1 + innerY2) / 2f;
                }

                // Push the entity forward in Z so it lands on top of
                // sibling 2D draws that share its rect (background fill,
                // border) regardless of buffer flush order. 100 GUI units
                // is enough to clear normal screen layers.
                ctx.getMatrices().push();
                ctx.getMatrices().translate(0f, 0f, 100f);
                InventoryScreen.drawEntity(ctx,
                        innerX1, innerY1, innerX2, innerY2,
                        size, bottomOffset,
                        mouseX, mouseY,
                        entity);
                ctx.getMatrices().pop();
            }
        }

        if (style.getBorderWidth() > 0) {
            drawBorder(ctx);
        }
    }

    private void drawBorder(DrawContext ctx) {
        int bw = style.getBorderWidth();
        int color = style.getBorderColor();
        ctx.fill(x, y, x + width, y + bw, color);
        ctx.fill(x, y + height - bw, x + width, y + height, color);
        ctx.fill(x, y, x + bw, y + height, color);
        ctx.fill(x + width - bw, y, x + width, y + height, color);
    }

    private static int applyConstraint(int measured, int constraint, int max) {
        if (Size.isMatchParent(constraint)) return max;
        if (Size.isWrapContent(constraint)) return Math.min(measured, max);
        if (constraint <= 0) return Math.min(measured, max);
        return Math.min(constraint, max);
    }
}
