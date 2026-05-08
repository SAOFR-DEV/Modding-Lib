package org.triggersstudio.moddinglib.client.ui.components;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import org.triggersstudio.moddinglib.client.ui.layout.LayoutType;
import org.triggersstudio.moddinglib.client.ui.styling.Style;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/**
 * Wraps a single child and applies render-time transformations driven by
 * {@link DoubleSupplier}s (typically {@code Tween}s, but any supplier works).
 *
 * <p>Transforms applied at render:
 * <ul>
 *   <li>{@code translate(x, y)} via the matrix stack.</li>
 *   <li>{@code scale(s)} around the component's center.</li>
 *   <li>{@code opacity} via {@link RenderSystem#setShaderColor(float, float, float, float)},
 *       which Minecraft's drawing primitives respect as a global color multiplier.</li>
 * </ul>
 *
 * <p>Each supplier is read once per frame; missing suppliers ({@code null}) leave
 * the corresponding transform identity. Layout / measure pass-through to the
 * child unchanged — animations are visual-only.
 *
 * <p>Construct via {@code Components.Animated(child)...} or the helpers
 * {@code Components.FadeIn / FadeOut / SlideIn}.
 */
public class AnimatedComponent extends Container {

    private final DoubleSupplier opacity;
    private final DoubleSupplier translateX;
    private final DoubleSupplier translateY;
    private final DoubleSupplier scale;
    private final IntSupplier backgroundColor;

    public AnimatedComponent(UIComponent child,
                             DoubleSupplier opacity,
                             DoubleSupplier translateX,
                             DoubleSupplier translateY,
                             DoubleSupplier scale) {
        this(child, opacity, translateX, translateY, scale, null);
    }

    public AnimatedComponent(UIComponent child,
                             DoubleSupplier opacity,
                             DoubleSupplier translateX,
                             DoubleSupplier translateY,
                             DoubleSupplier scale,
                             IntSupplier backgroundColor) {
        super(Style.DEFAULT, LayoutType.VERTICAL, 0);
        if (child == null) throw new IllegalArgumentException("child must not be null");
        this.opacity = opacity;
        this.translateX = translateX;
        this.translateY = translateY;
        this.scale = scale;
        this.backgroundColor = backgroundColor;
        addChild(child);
    }

    @Override
    public void render(DrawContext drawContext) {
        double tx = translateX != null ? translateX.getAsDouble() : 0;
        double ty = translateY != null ? translateY.getAsDouble() : 0;
        double sc = scale != null ? scale.getAsDouble() : 1;
        double op = opacity != null ? Math.max(0, Math.min(1, opacity.getAsDouble())) : 1;

        boolean hasTransform = tx != 0 || ty != 0 || sc != 1;
        boolean hasOpacity = op < 1;

        if (hasTransform) {
            drawContext.getMatrices().push();
            if (sc != 1) {
                // Scale around the child's center
                int cx = x + width / 2;
                int cy = y + height / 2;
                drawContext.getMatrices().translate(cx + tx, cy + ty, 0);
                drawContext.getMatrices().scale((float) sc, (float) sc, 1f);
                drawContext.getMatrices().translate(-cx, -cy, 0);
            } else {
                drawContext.getMatrices().translate((float) tx, (float) ty, 0);
            }
        }

        if (hasOpacity) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, (float) op);
        }

        try {
            // Animated background fill behind the child if a color supplier is set.
            if (backgroundColor != null) {
                int bg = backgroundColor.getAsInt();
                if (((bg >>> 24) & 0xFF) != 0) {
                    drawContext.fill(x, y, x + width, y + height, bg);
                }
            }
            super.render(drawContext);
        } finally {
            if (hasOpacity) {
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }
            if (hasTransform) {
                drawContext.getMatrices().pop();
            }
        }
    }
}
