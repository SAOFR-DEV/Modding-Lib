package fr.perrier.saomoddinglib.client.ui.state;

/**
 * Handle returned when subscribing to a {@link State}. Call {@link #unsubscribe()}
 * to stop receiving change notifications and release the associated listener.
 *
 * <p>Subscriptions registered through {@link fr.perrier.saomoddinglib.client.ui.components.UIComponent#track(Subscription)}
 * are disposed automatically when the component is detached.</p>
 */
@FunctionalInterface
public interface Subscription {
    void unsubscribe();
}