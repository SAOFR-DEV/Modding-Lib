package org.triggersstudio.moddinglib.client.ui.context;

import net.minecraft.client.gui.DrawContext;
import org.triggersstudio.moddinglib.client.ui.components.UIComponent;
import org.triggersstudio.moddinglib.client.ui.screen.UIScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Screen-scoped shared services exposed to {@link UIComponent} instances.
 * One instance is created per {@link UIScreen}
 * and propagated down the tree at attach time.
 *
 * <p>Holds the focus state (which component receives keyboard events) and
 * the per-frame overlay queue (deferred draws like tooltip popups that must
 * render above the rest of the tree).
 *
 * <p>Not thread-safe: all mutations must happen on the Minecraft render thread.
 */
public class UIContext {

    private UIComponent focused;
    private final List<Consumer<DrawContext>> overlayQueue = new ArrayList<>();

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

    /**
     * Queue a render callback to be invoked after the main component tree
     * finishes drawing this frame. Use this for tooltips, popups, or anything
     * that must appear above siblings later in the tree. The queue is flushed
     * (and emptied) once per frame by {@link UIScreen}.
     */
    public void deferOverlay(Consumer<DrawContext> renderCall) {
        if (renderCall != null) {
            overlayQueue.add(renderCall);
        }
    }

    /**
     * Run and clear all queued overlay draws. Called by {@link UIScreen} after
     * rendering the component tree.
     */
    public void renderOverlays(DrawContext drawContext) {
        if (overlayQueue.isEmpty()) return;
        // Snapshot in case an overlay enqueues another one during draw.
        List<Consumer<DrawContext>> snapshot = new ArrayList<>(overlayQueue);
        overlayQueue.clear();
        for (Consumer<DrawContext> r : snapshot) {
            r.accept(drawContext);
        }
    }
}
