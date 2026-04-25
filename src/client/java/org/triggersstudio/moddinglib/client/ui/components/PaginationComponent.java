package org.triggersstudio.moddinglib.client.ui.components;

import org.triggersstudio.moddinglib.client.ui.context.UIContext;
import org.triggersstudio.moddinglib.client.ui.layout.LayoutType;
import org.triggersstudio.moddinglib.client.ui.state.State;
import org.triggersstudio.moddinglib.client.ui.styling.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * Page navigator bound to a {@link State<Integer>} (1-indexed). Renders a
 * row of clickable page buttons with first/prev/next/last arrows and an
 * ellipsis-collapsed window around the current page.
 *
 * <p>The component subscribes to its state at attach time and rebuilds the
 * children list whenever the page or any external mutation occurs. Use
 * {@code Components.Pagination(...)} to construct.
 *
 * <p>Styling: {@code buttonStyle} applies to all buttons; {@code activeStyle}
 * to the current page only. Disabled arrow buttons (on first/last page)
 * derive a dimmer textColor from {@code buttonStyle}.
 */
public class PaginationComponent extends Container {

    private static final int ELLIPSIS = -1;
    private static final int SPACING = 4;

    private static final Style DEFAULT_BUTTON_STYLE = Style.backgroundColor(0xFF_2A_2A_2A)
            .textColor(0xFF_FF_FF_FF)
            .width(24)
            .height(20)
            .build();
    private static final Style DEFAULT_ACTIVE_STYLE = Style.backgroundColor(0xFF_55_88_FF)
            .textColor(0xFF_FF_FF_FF)
            .width(24)
            .height(20)
            .build();

    private final State<Integer> page;
    private final int totalPages;
    private final int siblings;
    private final Style buttonStyle;
    private final Style activeStyle;

    public PaginationComponent(State<Integer> page, int totalPages, int siblings,
                               Style buttonStyle, Style activeStyle) {
        super(Style.DEFAULT, LayoutType.HORIZONTAL, SPACING);
        if (page == null) throw new IllegalArgumentException("page state must not be null");
        if (totalPages < 1) throw new IllegalArgumentException("totalPages must be >= 1");
        if (siblings < 0) throw new IllegalArgumentException("siblings must be >= 0");
        this.page = page;
        this.totalPages = totalPages;
        this.siblings = siblings;
        this.buttonStyle = buttonStyle != null ? buttonStyle : DEFAULT_BUTTON_STYLE;
        this.activeStyle = activeStyle != null ? activeStyle : DEFAULT_ACTIVE_STYLE;
        rebuild();
    }

    @Override
    public void onAttach(UIContext ctx) {
        if (isAttached()) return;
        super.onAttach(ctx);
        track(page.onChange(v -> rebuild()));
    }

    private void rebuild() {
        // Detach existing children to release any subscriptions they hold.
        for (UIComponent c : children) {
            c.onDetach();
        }
        children.clear();

        int requested = page.get() != null ? page.get() : 1;
        int current = Math.max(1, Math.min(totalPages, requested));
        if (current != requested) {
            // Re-anchor the state inside the valid range. The set() will fire
            // onChange and trigger rebuild() again, this time with current==requested.
            page.set(current);
            return;
        }

        addArrow("«", current > 1, () -> page.set(1));
        addArrow("‹", current > 1, () -> page.set(current - 1));

        for (int p : computePages(current, totalPages, siblings)) {
            if (p == ELLIPSIS) {
                addEllipsis();
            } else {
                addNumber(p, p == current);
            }
        }

        addArrow("›", current < totalPages, () -> page.set(current + 1));
        addArrow("»", current < totalPages, () -> page.set(totalPages));
    }

    private void addArrow(String label, boolean enabled, Runnable action) {
        Style.Builder builder = buttonStyle.toBuilder();
        if (!enabled) {
            builder.textColor(dimAlpha(buttonStyle.getTextColor()));
        } else {
            builder.onClick((mx, my, btn) -> action.run());
        }
        addChild(new ButtonComponent(label, builder.build()));
    }

    private void addNumber(int p, boolean active) {
        Style base = active ? activeStyle : buttonStyle;
        Style style = base.toBuilder()
                .onClick((mx, my, btn) -> page.set(p))
                .build();
        addChild(new ButtonComponent(String.valueOf(p), style));
    }

    private void addEllipsis() {
        // Same slot size as the buttons, transparent background, dimmed text.
        Style style = buttonStyle.toBuilder()
                .backgroundColor(0)
                .textColor(dimAlpha(buttonStyle.getTextColor()))
                .build();
        addChild(new ButtonComponent("…", style));
    }

    private static int dimAlpha(int color) {
        return (color & 0x00_FF_FF_FF) | 0x60_00_00_00;
    }

    /**
     * Build the list of pages to display, with {@link #ELLIPSIS} sentinels
     * marking collapsed ranges. Always includes 1 and {@code total}; shows a
     * window of {@code 2*siblings + 1} numbers around {@code current}, with
     * the window expanded toward the edges so its size stays constant.
     * Avoids inserting an ellipsis that would only collapse a single page.
     */
    static List<Integer> computePages(int current, int total, int siblings) {
        List<Integer> result = new ArrayList<>();
        if (total < 1) return result;
        result.add(1);
        if (total == 1) return result;

        int target = 2 * siblings + 1;
        int start = Math.max(2, current - siblings);
        int end = Math.min(total - 1, current + siblings);
        int size = Math.max(0, end - start + 1);
        if (size < target) {
            if (start == 2) {
                end = Math.min(total - 1, start + target - 1);
            } else if (end == total - 1) {
                start = Math.max(2, end - target + 1);
            }
        }

        if (start == 3) result.add(2);
        else if (start > 3) result.add(ELLIPSIS);

        for (int p = start; p <= end; p++) result.add(p);

        if (end == total - 2) result.add(total - 1);
        else if (end < total - 2) result.add(ELLIPSIS);

        result.add(total);
        return result;
    }
}
