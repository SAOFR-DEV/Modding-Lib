package fr.perrier.saomoddinglib.client.ui.state;

import net.minecraft.client.MinecraftClient;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private static final Map<String, WeakReference<State<?>>> NAMED_STATES =
            Collections.synchronizedMap(new LinkedHashMap<>());

    private T value;
    private String name;
    private final List<Consumer<T>> listeners = new ArrayList<>();

    protected State(T initial) {
        this.value = initial;
    }

    /**
     * Create a new anonymous state.
     */
    public static <T> State<T> of(T initial) {
        return new State<>(initial);
    }

    /**
     * Create a new named state. Named states are registered in a global
     * weak-valued registry and surfaced by the debug overlay under the given
     * {@code name}. Registering the same name twice replaces the previous
     * entry. The reference in the registry is weak, so states that go out of
     * scope are cleaned up automatically.
     */
    public static <T> State<T> of(T initial, String name) {
        State<T> state = new State<>(initial);
        if (name != null && !name.isEmpty()) {
            state.name = name;
            NAMED_STATES.put(name, new WeakReference<>(state));
        }
        return state;
    }

    /**
     * @return the registered name of this state, or {@code null} if anonymous.
     */
    public String getName() {
        return name;
    }

    /**
     * Snapshot the currently-live named states. Cleans up stale entries whose
     * target has been garbage-collected. Returned in registration order.
     */
    public static List<NamedStateSnapshot> registeredStates() {
        List<NamedStateSnapshot> out = new ArrayList<>();
        synchronized (NAMED_STATES) {
            Iterator<Map.Entry<String, WeakReference<State<?>>>> it = NAMED_STATES.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, WeakReference<State<?>>> entry = it.next();
                State<?> live = entry.getValue().get();
                if (live == null) {
                    it.remove();
                } else {
                    out.add(new NamedStateSnapshot(entry.getKey(), live.get()));
                }
            }
        }
        return out;
    }

    /** Lightweight snapshot row for the debug overlay. */
    public record NamedStateSnapshot(String name, Object value) {}

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