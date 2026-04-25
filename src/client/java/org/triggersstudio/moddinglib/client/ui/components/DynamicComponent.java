package org.triggersstudio.moddinglib.client.ui.components;

import org.triggersstudio.moddinglib.client.ui.context.UIContext;
import org.triggersstudio.moddinglib.client.ui.layout.LayoutType;
import org.triggersstudio.moddinglib.client.ui.state.State;
import org.triggersstudio.moddinglib.client.ui.styling.Style;

import java.util.function.Function;

/**
 * Container that derives its single child from a {@link State}. The builder
 * function is invoked whenever the state changes, and the resulting component
 * replaces the previous child.
 *
 * <p>Use this for content that depends reactively on state — paginated views,
 * tabs, conditional sections, list rendering driven by a selection. The
 * traditional {@code Components.Text(supplier)} only handles strings; this
 * one handles arbitrary component trees.
 *
 * <p>Construct via {@code Components.Dynamic(...)}.
 */
public class DynamicComponent<T> extends Container {

    private final State<T> source;
    private final Function<T, UIComponent> builder;

    public DynamicComponent(State<T> source, Function<T, UIComponent> builder) {
        super(Style.DEFAULT, LayoutType.VERTICAL, 0);
        if (source == null) throw new IllegalArgumentException("source state must not be null");
        if (builder == null) throw new IllegalArgumentException("builder must not be null");
        this.source = source;
        this.builder = builder;
        rebuild();
    }

    @Override
    public void onAttach(UIContext ctx) {
        if (isAttached()) return;
        super.onAttach(ctx);
        track(source.onChange(v -> rebuild()));
    }

    private void rebuild() {
        for (UIComponent c : children) {
            c.onDetach();
        }
        children.clear();
        UIComponent built = builder.apply(source.get());
        if (built != null) {
            addChild(built);
        }
    }
}
