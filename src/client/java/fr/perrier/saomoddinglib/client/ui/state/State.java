package fr.perrier.saomoddinglib.client.ui.state;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Observable, mutable value. The cornerstone of the UI reactive model.
 *
 * <p>Typical usage:
 * <pre>
 * State&lt;Integer&gt; counter = State.of(0);
 * counter.onChange(v -&gt; System.out.println("now " + v));
 * counter.set(1);            // prints "now 1"
 * counter.set(1);            // equals-check: no-op, no notification
 *
 * State&lt;String&gt; label = counter.map(v -&gt; "Counter: " + v);
 * </pre>
 *
 * <p><b>Threading:</b> {@link #set(Object)} may be called from any thread.
 * If the caller is not on the Minecraft render thread, the update is
 * forwarded to it via {@link MinecraftClient#execute(Runnable)} so listeners
 * always run on the render thread.
 *
 * <p><b>Equality short-circuit:</b> setting a value equal (via
 * {@link Objects#equals}) to the current one does nothing. This is what
 * makes {@link #bindBidirectional(State, State)} safe from infinite loops.
 */
public class State<T> implements Supplier<T> {

    private T value;
    private final List<Consumer<T>> listeners = new ArrayList<>();

    protected State(T initial) {
        this.value = initial;
    }

    /**
     * Create a new state holding the given initial value.
     */
    public static <T> State<T> of(T initial) {
        return new State<>(initial);
    }

    /**
     * @return the current value.
     */
    @Override
    public T get() {
        return value;
    }

    /**
     * Set the value and notify listeners. No-op if the new value equals the
     * current one. If called off the render thread, the update is scheduled
     * onto it.
     */
    public void set(T newValue) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && !client.isOnThread()) {
            client.execute(() -> applySet(newValue));
            return;
        }
        applySet(newValue);
    }

    private void applySet(T newValue) {
        if (Objects.equals(this.value, newValue)) {
            return;
        }
        this.value = newValue;
        // Snapshot so listeners may add/remove during iteration without CME.
        List<Consumer<T>> snapshot = new ArrayList<>(listeners);
        for (Consumer<T> listener : snapshot) {
            listener.accept(newValue);
        }
    }

    /**
     * Register a listener called with the new value on every actual change.
     * The returned {@link Subscription} removes the listener when disposed.
     */
    public Subscription onChange(Consumer<T> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /**
     * Create a derived state whose value is the result of applying
     * {@code mapper} to this state's value. The derived state updates
     * whenever the source changes.
     *
     * <p>The returned state is technically mutable (it inherits {@link #set}),
     * but you should treat it as read-only. Mutating it is legal and will
     * notify its own subscribers, but the next upstream change overrides it.
     */
    public <R> State<R> map(Function<T, R> mapper) {
        State<R> derived = new State<>(mapper.apply(this.value));
        this.onChange(v -> derived.applySet(mapper.apply(v)));
        return derived;
    }

    /**
     * Combine two states into a derived one. Recomputes whenever either
     * source changes.
     */
    public static <A, B, R> State<R> combine(State<A> a, State<B> b, BiFunction<A, B, R> combiner) {
        State<R> derived = new State<>(combiner.apply(a.get(), b.get()));
        a.onChange(v -> derived.applySet(combiner.apply(a.get(), b.get())));
        b.onChange(v -> derived.applySet(combiner.apply(a.get(), b.get())));
        return derived;
    }

    /**
     * Keep two states in sync in both directions. {@code b} is first aligned
     * to {@code a}'s current value. Subsequent changes on either side
     * propagate to the other. The equals-check in {@link #set} prevents
     * feedback loops.
     *
     * <p>Useful to plug a user-provided state into a component that exposes
     * its own internal state (e.g. a text field's editor buffer).
     */
    public static <T> void bindBidirectional(State<T> a, State<T> b) {
        b.set(a.get());
        a.onChange(b::set);
        b.onChange(a::set);
    }
}