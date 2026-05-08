package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.triggersstudio.moddinglib.client.ui.context.UIContext;
import org.triggersstudio.moddinglib.client.ui.state.State;
import org.triggersstudio.moddinglib.client.ui.styling.Size;
import org.triggersstudio.moddinglib.client.ui.styling.Style;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Drop-down selector bound to a {@code State<T>}. Closed: shows the current
 * selection (or placeholder) in a button-like trigger. Clicked: opens a
 * popover listing all items; clicking an item writes it to the state and
 * closes the popover; clicking outside closes without changing the state.
 *
 * <p>The popover renders through the {@link UIContext} overlay queue so it
 * appears above siblings later in the tree, and a popup-click handler (also
 * on the context) routes outside-the-trigger clicks back to this component
 * regardless of where in the parent hierarchy the combo lives.
 *
 * <p>Layout-wise the trigger is a fixed-width button — the popover is drawn
 * outside the layout flow and does not affect siblings. Auto-flips above the
 * trigger when there isn't enough room below.
 */
public class ComboBoxComponent<T> extends UIComponent {

    private static final int DEFAULT_TRIGGER_WIDTH = 160;
    private static final int DEFAULT_TRIGGER_HEIGHT = 22;
    private static final int ITEM_HEIGHT = 20;
    private static final int POPOVER_GAP = 2;
    private static final int CHEVRON_W = 8;

    private final State<T> selection;
    private final List<T> items;
    private final Function<T, String> labeler;
    private final Style triggerStyle;
    private final Style popoverStyle;
    private final Style itemStyle;
    private final Style selectedItemStyle;
    private final String placeholder;

    private boolean open = false;
    private boolean hovered = false;
    private double cursorX, cursorY;
    private int popoverX, popoverY, popoverW, popoverH;
    private Runnable unregisterPopupHandler = null;

    public ComboBoxComponent(State<T> selection, List<T> items,
                             Function<T, String> labeler,
                             Style triggerStyle, Style popoverStyle,
                             Style itemStyle, Style selectedItemStyle,
                             String placeholder) {
        super(triggerStyle != null ? triggerStyle : defaultTriggerStyle());
        this.selection = Objects.requireNonNull(selection, "selection");
        this.items = items != null ? items : List.of();
        this.labeler = labeler != null ? labeler : v -> String.valueOf(v);
        this.triggerStyle = this.style;
        this.popoverStyle = popoverStyle != null ? popoverStyle : defaultPopoverStyle();
        this.itemStyle = itemStyle != null ? itemStyle : defaultItemStyle();
        this.selectedItemStyle = selectedItemStyle != null ? selectedItemStyle : defaultSelectedItemStyle();
        this.placeholder = placeholder != null ? placeholder : "Select…";
    }

    public static Style defaultTriggerStyle() {
        return Style.backgroundColor(0xFF_2A_2A_2A)
                .textColor(0xFF_FF_FF_FF)
                .padding(4, 6)
                .border(0xFF_44_44_44, 1)
                .build();
    }

    public static Style defaultPopoverStyle() {
        return Style.backgroundColor(0xF8_18_18_20)
                .border(0xFF_55_55_55, 1)
                .build();
    }

    public static Style defaultItemStyle() {
        return Style.backgroundColor(0x00_00_00_00)
                .textColor(0xFF_DD_DD_DD)
                .padding(2, 8)
                .build();
    }

    public static Style defaultSelectedItemStyle() {
        return Style.backgroundColor(0xFF_3A_5C_88)
                .textColor(0xFF_FF_FF_FF)
                .padding(2, 8)
                .build();
    }

    @Override
    public MeasureResult measure(int maxWidth, int maxHeight) {
        int padH = triggerStyle.getPadding().getHorizontal();
        int padV = triggerStyle.getPadding().getVertical();
        int w = applyConstraint(DEFAULT_TRIGGER_WIDTH, triggerStyle.getWidth(), maxWidth);
        int h = applyConstraint(DEFAULT_TRIGGER_HEIGHT + padV, triggerStyle.getHeight(), maxHeight);
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
    public void onDetach() {
        // Closing the screen with the popover open: drop the handler.
        if (unregisterPopupHandler != null) {
            unregisterPopupHandler.run();
            unregisterPopupHandler = null;
        }
        open = false;
        super.onDetach();
    }

    @Override
    public void onMouseMove(double mx, double my) {
        cursorX = mx;
        cursorY = my;
        hovered = isPointInside(mx, my);
    }

    @Override
    public boolean onMouseClick(double mx, double my, int button) {
        if (!isPointInside(mx, my)) return false;
        if (open) {
            close();
        } else {
            openPopover();
        }
        return true;
    }

    private void openPopover() {
        if (open) return;
        open = true;
        if (context != null) {
            unregisterPopupHandler = context.registerPopupClickHandler(this::handlePopupClick);
        }
    }

    private void close() {
        if (!open) return;
        open = false;
        if (unregisterPopupHandler != null) {
            unregisterPopupHandler.run();
            unregisterPopupHandler = null;
        }
    }

    private boolean handlePopupClick(double mx, double my, int button) {
        // Click on trigger → close (toggle).
        if (isPointInside(mx, my)) {
            close();
            return true;
        }
        // Click inside popover → select item, close.
        if (mx >= popoverX && mx <= popoverX + popoverW
                && my >= popoverY && my <= popoverY + popoverH) {
            int idx = (int) ((my - popoverY - 1) / ITEM_HEIGHT);
            if (idx >= 0 && idx < items.size()) {
                selection.set(items.get(idx));
            }
            close();
            return true;
        }
        // Click outside both → close, but let the original target still receive.
        close();
        return false;
    }

    @Override
    public void render(DrawContext drawContext) {
        renderTrigger(drawContext);
        if (open && context != null && !items.isEmpty()) {
            computePopoverBounds();
            context.deferOverlay(this::renderPopover);
        }
    }

    private void renderTrigger(DrawContext drawContext) {
        int bg = triggerStyle.getBackgroundColor();
        if (hovered && !open && bg != 0x00_00_00_00) {
            bg = darken(bg, 20);
        }
        drawContext.fill(x, y, x + width, y + height, bg);
        if (triggerStyle.getBorderWidth() > 0) {
            drawBorder(drawContext, x, y, width, height,
                    open ? lighten(triggerStyle.getBorderColor(), 60) : triggerStyle.getBorderColor(),
                    triggerStyle.getBorderWidth());
        }

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        T current = selection.get();
        String label = current != null ? labeler.apply(current) : placeholder;
        int textColor = current != null ? triggerStyle.getTextColor() : 0xFF_88_88_88;

        int textX = x + triggerStyle.getPadding().left;
        int textY = y + (height - 8) / 2;
        int maxLabelW = width - triggerStyle.getPadding().getHorizontal() - CHEVRON_W - 4;
        drawContext.drawText(tr, truncate(tr, label, maxLabelW), textX, textY, textColor, false);

        // Chevron on the right (small triangle pointing down, or up when open).
        int chevX = x + width - triggerStyle.getPadding().right - CHEVRON_W;
        int chevY = y + height / 2 - 2;
        drawChevron(drawContext, chevX, chevY, open);
    }

    private void computePopoverBounds() {
        popoverW = width;
        popoverH = items.size() * ITEM_HEIGHT + 2;
        popoverX = x;
        int below = y + height + POPOVER_GAP;
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
        if (below + popoverH > screenH - 4) {
            // Flip above.
            popoverY = y - POPOVER_GAP - popoverH;
            if (popoverY < 4) popoverY = Math.max(4, screenH - popoverH - 4);
        } else {
            popoverY = below;
        }
    }

    private void renderPopover(DrawContext drawContext) {
        // Background + border.
        drawContext.fill(popoverX, popoverY, popoverX + popoverW, popoverY + popoverH,
                popoverStyle.getBackgroundColor());
        if (popoverStyle.getBorderWidth() > 0) {
            drawBorder(drawContext, popoverX, popoverY, popoverW, popoverH,
                    popoverStyle.getBorderColor(), popoverStyle.getBorderWidth());
        }

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        T current = selection.get();
        for (int i = 0; i < items.size(); i++) {
            int iy = popoverY + 1 + i * ITEM_HEIGHT;
            T item = items.get(i);
            boolean isSelected = Objects.equals(item, current);
            boolean isHovered = cursorX >= popoverX && cursorX <= popoverX + popoverW
                    && cursorY >= iy && cursorY < iy + ITEM_HEIGHT;
            Style rowStyle = isSelected ? selectedItemStyle : itemStyle;
            int rowBg = rowStyle.getBackgroundColor();
            if (isHovered && !isSelected) {
                rowBg = 0x40_FF_FF_FF; // highlight overlay
            }
            if (rowBg != 0x00_00_00_00) {
                drawContext.fill(popoverX + 1, iy, popoverX + popoverW - 1, iy + ITEM_HEIGHT, rowBg);
            }
            String label = labeler.apply(item);
            int textX = popoverX + rowStyle.getPadding().left;
            int textY = iy + (ITEM_HEIGHT - 8) / 2;
            int maxLabelW = popoverW - rowStyle.getPadding().getHorizontal();
            drawContext.drawText(tr, truncate(tr, label, maxLabelW),
                    textX, textY, rowStyle.getTextColor(), false);
        }
    }

    private static void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color, int bw) {
        ctx.fill(x, y, x + w, y + bw, color);
        ctx.fill(x, y + h - bw, x + w, y + h, color);
        ctx.fill(x, y, x + bw, y + h, color);
        ctx.fill(x + w - bw, y, x + w, y + h, color);
    }

    private static void drawChevron(DrawContext ctx, int x, int y, boolean up) {
        // 7×4 chevron made of 4 stacked rows.
        int color = 0xFF_AA_AA_AA;
        if (up) {
            ctx.fill(x + 3, y + 0, x + 4, y + 1, color);
            ctx.fill(x + 2, y + 1, x + 5, y + 2, color);
            ctx.fill(x + 1, y + 2, x + 6, y + 3, color);
            ctx.fill(x + 0, y + 3, x + 7, y + 4, color);
        } else {
            ctx.fill(x + 0, y + 0, x + 7, y + 1, color);
            ctx.fill(x + 1, y + 1, x + 6, y + 2, color);
            ctx.fill(x + 2, y + 2, x + 5, y + 3, color);
            ctx.fill(x + 3, y + 3, x + 4, y + 4, color);
        }
    }

    private static String truncate(TextRenderer tr, String s, int maxWidth) {
        if (tr.getWidth(s) <= maxWidth) return s;
        String ell = "...";
        int ellW = tr.getWidth(ell);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            sb.append(s.charAt(i));
            if (tr.getWidth(sb.toString()) + ellW > maxWidth) {
                if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
                sb.append(ell);
                return sb.toString();
            }
        }
        return s;
    }

    private static int darken(int argb, int amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.max(0, ((argb >> 16) & 0xFF) - amount);
        int g = Math.max(0, ((argb >> 8) & 0xFF) - amount);
        int b = Math.max(0, (argb & 0xFF) - amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lighten(int argb, int amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + amount);
        int b = Math.min(255, (argb & 0xFF) + amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    protected int applyConstraint(int measured, int constraint, int max) {
        if (Size.isMatchParent(constraint)) {
            return max;
        } else if (Size.isWrapContent(constraint)) {
            return measured;
        } else {
            return Math.min(constraint, max);
        }
    }
}
