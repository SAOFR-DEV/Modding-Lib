package org.triggersstudio.moddinglib.client.ui.context;

import org.triggersstudio.moddinglib.client.ui.components.UIComponent;
import org.triggersstudio.moddinglib.client.ui.screen.UIScreen;

/**
 * Screen-scoped shared services exposed to {@link UIComponent} instances.
 * One instance is created per {@link UIScreen}
 * and propagated down the tree at attach time.
 *
 * <p>Currently holds the focus state: which single component, if any, should
 * receive keyboard events. Additional screen-scoped concerns (theming,
 * animation clock) can live here later without rippling signature changes.
 *
 * <p>Not thread-safe: all mutations must happen on the Minecraft render thread.
 */
public class UIContext {

    private UIComponent focused;

    /**
     * Transfer focus to {@code component}. The currently focused component (if
     * any and different) is blurred first. Passing {@code null} clears focus
     * without focusing anything else (same as {@link #clearFocus()}).
     */
    public void requestFocus(UIComponent component) {
        if (focused == component) return;
        UIComponent previous = focused;
        focused = component;
        if (previous != null) {
            previous.onBlur();
        }
        if (component != null) {
            component.onFocus();
        }
    }

    /**
     * @return the component currently receiving keyboard events, or {@code null}.
     */
    public UIComponent getFocused() {
        return focused;
    }

    /**
     * Blur the currently focused component, if any.
     */
    public void clearFocus() {
        requestFocus(null);
    }
}
