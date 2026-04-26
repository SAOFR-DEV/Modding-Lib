package org.triggersstudio.moddinglib.client.ui.components;

import org.triggersstudio.moddinglib.client.ui.context.UIContext;
import org.triggersstudio.moddinglib.client.ui.layout.LayoutType;
import org.triggersstudio.moddinglib.client.ui.state.State;
import org.triggersstudio.moddinglib.client.ui.styling.Style;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Vertical list of selectable rows bound to a {@link State}. Each item is
 * rendered by a user-supplied function and wrapped in a clickable row that
 * applies {@code rowStyle} or {@code selectedRowStyle} depending on whether
 * the item equals the current state value.
 *
 * <p>Clicking a row writes the corresponding item to the state. The state
 * change triggers a rebuild so the highlight follows automatically.
 *
 * <p>Construct via {@code Components.SelectList(...)}.
 */
public class SelectListComponent<T> extends Container {

    private static final Style DEFAULT_ROW_STYLE = Style.backgroundColor(0xFF_2A_2A_2A)
            .textColor(0xFF_DD_DD_DD)
            .padding(6, 10)
            .build();
    private static final Style DEFAULT_SELECTED_ROW_STYLE = Style.backgroundColor(0xFF_55_88_FF)
            .textColor(0xFF_FF_FF_FF)
            .padding(6, 10)
            .build();

    private final State<T> selection;
    private final List<T> items;
    private final Function<T, UIComponent> renderer;
    private final Style rowStyle;
    private final Style selectedRowStyle;

    public SelectListComponent(State<T> selection, List<T> items,
                               Function<T, UIComponent> renderer,
                               Style rowStyle, Style selectedRowStyle) {
        super(Style.DEFAULT, LayoutType.VERTICAL, 2);
        if (selection == null) throw new IllegalArgumentException("selection state must not be null");
        if (items == null) throw new IllegalArgumentException("items must not be null");
        if (renderer == null) throw new IllegalArgumentException("renderer must not be null");
        this.selection = selection;
        this.items = List.copyOf(items);
        this.renderer = renderer;
        this.rowStyle = rowStyle != null ? rowStyle : DEFAULT_ROW_STYLE;
        this.selectedRowStyle = selectedRowStyle != null ? selectedRowStyle : DEFAULT_SELECTED_ROW_STYLE;
        rebuild();
    }

    @Override
    public void onAttach(UIContext ctx) {
        if (isAttached()) return;
        super.onAttach(ctx);
        track(selection.onChange(v -> rebuild()));
    }

    private void rebuild() {
        for (UIComponent c : children) {
            c.onDetach();
        }
        children.clear();

        T current = selection.get();
        for (T item : items) {
            boolean isSelected = Objects.equals(item, current);
            Style base = isSelected ? selectedRowStyle : rowStyle;
            Style rowWithClick = base.toBuilder()
                    .onClick((mx, my, btn) -> selection.set(item))
                    .build();
            // Each row is a Container so it has its own background + click handler;
            // the renderer's output is placed inside it.
            Container row = new Container(rowWithClick, LayoutType.VERTICAL, 0);
            row.addChild(renderer.apply(item));
            addChild(row);
        }
    }
}
