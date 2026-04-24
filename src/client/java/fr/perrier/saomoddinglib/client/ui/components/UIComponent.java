package fr.perrier.saomoddinglib.client.ui.components;

import net.minecraft.client.gui.DrawContext;
import fr.perrier.saomoddinglib.client.ui.context.UIContext;
import fr.perrier.saomoddinglib.client.ui.state.Subscription;
import fr.perrier.saomoddinglib.client.ui.styling.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all UI components.
 * Manages lifecycle: measure -> layout -> render, plus attach/detach
 * for subscription cleanup.
 */
public abstract class UIComponent {
    protected Style style;
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean needsMeasure = true;
    protected boolean needsLayout = true;

    private final List<Subscription> subscriptions = new ArrayList<>();
    private boolean attached = false;
    protected UIContext context;

    public UIComponent(Style style) {
        this.style = style != null ? style : Style.DEFAULT;
        this.x = 0;
        this.y = 0;
        this.width = 0;
        this.height = 0;
    }

    /**
     * Measure phase: determine the component's desired size
     */
    public abstract MeasureResult measure(int maxWidth, int maxHeight);

    /**
     * Layout phase: position the component and its children
     */
    public abstract void layout(int x, int y, int width, int height);

    /**
     * Render phase: draw the component
     */
    public abstract void render(DrawContext drawContext);

    /**
     * Handle mouse click event
     * @return true if event was consumed
     */
    public boolean onMouseClick(double x, double y, int button) {
        return false;
    }

    /**
     * Handle mouse scroll event
     * @return true if event was consumed
     */
    public boolean onMouseScroll(double x, double y, double scrollDelta) {
        return false;
    }

    /**
     * Handle mouse move event
     */
    public void onMouseMove(double x, double y) {
    }

    /**
     * Handle mouse drag event (mouse moved while a button is held).
     * Components that captured the initial click (e.g. sliders) react here
     * regardless of current cursor position.
     * @return true if event was consumed
     */
    public boolean onMouseDrag(double x, double y, double dragX, double dragY, int button) {
        return false;
    }

    /**
     * Handle mouse release event. Components that track a capture state
     * (e.g. sliders in drag mode) should clear it here.
     * @return true if event was consumed
     */
    public boolean onMouseRelease(double x, double y, int button) {
        return false;
    }

    /**
     * Handle a raw key press (arrows, enter, backspace, function keys, etc.).
     * Only the focused component receives this event.
     * @return true if event was consumed
     */
    public boolean onKeyPress(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /**
     * Handle a typed character (respects IME, dead keys, shift state).
     * Only the focused component receives this event.
     * @return true if event was consumed
     */
    public boolean onCharTyped(char chr, int modifiers) {
        return false;
    }

    /**
     * Called when this component gains keyboard focus.
     */
    public void onFocus() {
    }

    /**
     * Called when this component loses keyboard focus.
     */
    public void onBlur() {
    }

    /**
     * @return whether this component currently holds focus in its UIContext.
     */
    public boolean isFocused() {
        return context != null && context.getFocused() == this;
    }

    /**
     * Ask the enclosing UIContext to transfer focus to this component.
     * No-op if the component isn't attached to a context yet.
     */
    public void requestFocus() {
        if (context != null) {
            context.requestFocus(this);
        }
    }

    // Getters
    public Style getStyle() {
        return style;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isPointInside(double px, double py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    /**
     * Register a subscription whose lifecycle is tied to this component.
     * The subscription is released automatically on {@link #onDetach()}.
     */
    public void track(Subscription subscription) {
        if (subscription != null) {
            subscriptions.add(subscription);
        }
    }

    /**
     * Called when the component becomes live (screen opens). Idempotent.
     * Receives the screen-scoped {@link UIContext}. Subclasses that manage
     * children must propagate this call.
     */
    public void onAttach(UIContext ctx) {
        if (attached) return;
        attached = true;
        this.context = ctx;
    }

    /**
     * Called when the component is removed from the live tree (screen closes).
     * Disposes all tracked subscriptions, clears focus if held, and drops the
     * context reference. Idempotent. Subclasses that manage children must
     * propagate this call.
     */
    public void onDetach() {
        if (!attached) return;
        attached = false;
        if (context != null && context.getFocused() == this) {
            context.clearFocus();
        }
        for (Subscription sub : subscriptions) {
            sub.unsubscribe();
        }
        subscriptions.clear();
        context = null;
    }

    public boolean isAttached() {
        return attached;
    }

    /**
     * Result of measurement phase
     */
    public static class MeasureResult {
        public final int width;
        public final int height;

        public MeasureResult(int width, int height) {
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
        }
    }
}

