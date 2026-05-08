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
    private final List<PopupClickHandler> popupClickHandlers = new ArrayList<>();

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

    /**
     * Receives a click before the component tree gets a chance. Implementors
     * (e.g. open combo box popovers) inspect the position and either consume
     * the click (return {@code true}) or let it pass through to the tree
     * (return {@code false}). Multiple handlers stack; the most-recently
     * registered runs first.
     */
    @FunctionalInterface
    public interface PopupClickHandler {
        boolean onClick(double x, double y, int button);
    }

    /**
     * Register a handler that intercepts mouse clicks before they reach the
     * component tree. Use for popovers, dropdowns, anything that needs to
     * react to clicks happening anywhere on the screen (close-on-outside,
     * item selection in a popover that extends past its parent's bounds).
     *
     * <p>The most-recently registered handler runs first. The returned action
     * removes the handler — the caller must invoke it on close.
     */
    public Runnable registerPopupClickHandler(PopupClickHandler handler) {
        if (handler == null) return () -> {};
        popupClickHandlers.add(handler);
        return () -> popupClickHandlers.remove(handler);
    }

    /**
     * Run registered popup handlers in LIFO order. Returns {@code true} if any
     * handler consumed the click, in which case the screen should skip its
     * normal tree dispatch. Called by {@link UIScreen} from {@code mouseClicked}.
     */
    public boolean dispatchPopupClick(double x, double y, int button) {
        if (popupClickHandlers.isEmpty()) return false;
        // Snapshot + reverse so handlers can unregister themselves during dispatch.
        List<PopupClickHandler> snapshot = new ArrayList<>(popupClickHandlers);
        for (int i = snapshot.size() - 1; i >= 0; i--) {
            if (snapshot.get(i).onClick(x, y, button)) {
                return true;
            }
        }
        return false;
    }
}
