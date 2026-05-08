package org.triggersstudio.moddinglib.client.ui.api;

import net.minecraft.util.Identifier;
import org.triggersstudio.moddinglib.client.ui.animation.Direction;
import org.triggersstudio.moddinglib.client.ui.animation.Easing;
import org.triggersstudio.moddinglib.client.ui.animation.Tween;
import org.triggersstudio.moddinglib.client.ui.components.*;
import org.triggersstudio.moddinglib.client.ui.layout.LayoutType;
import org.triggersstudio.moddinglib.client.ui.state.State;
import org.triggersstudio.moddinglib.client.ui.styling.Style;
import org.triggersstudio.moddinglib.client.ui.screen.UIScreen;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Composition API for building UI component trees.
 * All methods return UIComponent instances for nested composition.
 * 
 * Usage example:
 * <pre>
 * UIComponent screen = Column(
 *     Style.padding(16),
 *     
 *     Text("Title", Style.fontSize(24)),
 *     
 *     Button("Click Me", Style.height(40).onClick(...)),
 *     
 *     Row(
 *         Style.spacing(8),
 *         
 *         Button("OK", Style.flex(1)),
 *         Button("Cancel", Style.flex(1))
 *     )
 * );
 * </pre>
 */
public class Components {
    
    // ===== Text Component =====
    
    /**
     * Create a Text component with default style.
     */
    public static UIComponent Text(String content) {
        return Text(content, Style.DEFAULT);
    }
    
    /**
     * Create a Text component with custom style.
     */
    public static UIComponent Text(String content, Style style) {
        return new TextComponent(content, style);
    }

    /**
     * Create a reactive Text component whose content is resolved from the
     * supplier on every frame. Pass a {@code State<String>} (or
     * {@code myState.map(...)}) to bind the text to observable state.
     */
    public static UIComponent Text(Supplier<String> contentSupplier) {
        return Text(contentSupplier, Style.DEFAULT);
    }

    /**
     * Create a reactive Text component with custom style.
     */
    public static UIComponent Text(Supplier<String> contentSupplier, Style style) {
        return new TextComponent(contentSupplier, style);
    }
    
    // ===== Button Component =====
    
    /**
     * Create a Button component with default style.
     */
    public static UIComponent Button(String label) {
        return Button(label, Style.DEFAULT);
    }
    
    /**
     * Create a Button component with custom style.
     */
    public static UIComponent Button(String label, Style style) {
        return new ButtonComponent(label, style);
    }
    
    // ===== Image Component =====
    
    /**
     * Create an Image component.
     */
    public static UIComponent Image(Identifier texture) {
        return Image(texture, Style.DEFAULT);
    }
    
    /**
     * Create an Image component with custom style.
     */
    public static UIComponent Image(Identifier texture, Style style) {
        return new ImageComponent(texture, style);
    }
    
    /**
     * Create an Image component with explicit texture size.
     */
    public static UIComponent Image(Identifier texture, int textureWidth, int textureHeight, Style style) {
        return new ImageComponent(texture, textureWidth, textureHeight, style);
    }
    
    // ===== Container Components =====
    
    /**
     * Create a Row (horizontal container) with default style.
     */
    public static UIComponent Row(UIComponent... children) {
        return Row(Style.DEFAULT, children);
    }
    
    /**
     * Create a Row (horizontal container) with custom style and children.
     */
    public static UIComponent Row(Style style, UIComponent... children) {
        Container row = new Container(style, LayoutType.HORIZONTAL, 0);
        for (UIComponent child : children) {
            row.addChild(child);
        }
        return row;
    }

    /**
     * Create a Row using a scope builder — use for dynamic lists, loops or conditionals.
     */
    public static UIComponent Row(Consumer<ContainerScope> content) {
        return Row(Style.DEFAULT, content);
    }

    /**
     * Create a Row with custom style using a scope builder.
     */
    public static UIComponent Row(Style style, Consumer<ContainerScope> content) {
        ContainerScope scope = new ContainerScope();
        content.accept(scope);
        Container row = new Container(style, LayoutType.HORIZONTAL, 0);
        for (UIComponent child : scope.children()) {
            row.addChild(child);
        }
        return row;
    }

    /**
     * Create a Column (vertical container) with default style.
     */
    public static UIComponent Column(UIComponent... children) {
        return Column(Style.DEFAULT, children);
    }

    /**
     * Create a Column (vertical container) with custom style and children.
     */
    public static UIComponent Column(Style style, UIComponent... children) {
        Container column = new Container(style, LayoutType.VERTICAL, 0);
        for (UIComponent child : children) {
            column.addChild(child);
        }
        return column;
    }

    /**
     * Create a Column using a scope builder — use for dynamic lists, loops or conditionals.
     */
    public static UIComponent Column(Consumer<ContainerScope> content) {
        return Column(Style.DEFAULT, content);
    }

    /**
     * Create a Column with custom style using a scope builder.
     */
    public static UIComponent Column(Style style, Consumer<ContainerScope> content) {
        ContainerScope scope = new ContainerScope();
        content.accept(scope);
        Container column = new Container(style, LayoutType.VERTICAL, 0);
        for (UIComponent child : scope.children()) {
            column.addChild(child);
        }
        return column;
    }
    
    /**
     * Create a vertically-scrolling container with default style.
     */
    public static UIComponent VScroll(UIComponent content) {
        return VScroll(Style.DEFAULT, content);
    }

    /**
     * Create a vertically-scrolling container with custom style.
     */
    public static UIComponent VScroll(Style style, UIComponent content) {
        VerticalScrollContainer scroll = new VerticalScrollContainer(style);
        scroll.addChild(content);
        return scroll;
    }

    /**
     * Create a horizontally-scrolling container with default style.
     */
    public static UIComponent HScroll(UIComponent content) {
        return HScroll(Style.DEFAULT, content);
    }

    /**
     * Create a horizontally-scrolling container with custom style.
     */
    public static UIComponent HScroll(Style style, UIComponent content) {
        HorizontalScrollContainer scroll = new HorizontalScrollContainer(style);
        scroll.addChild(content);
        return scroll;
    }
    
    // ===== Text Field =====

    /**
     * Create a single-line editable text field bound bidirectionally to a
     * {@code State<String>}.
     */
    public static UIComponent TextField(State<String> state) {
        return TextField(state, "", Style.DEFAULT);
    }

    public static UIComponent TextField(State<String> state, String placeholder) {
        return TextField(state, placeholder, Style.DEFAULT);
    }

    public static UIComponent TextField(State<String> state, String placeholder, Style style) {
        return new TextFieldComponent(state, placeholder, style);
    }

    // ===== Dynamic =====
    //
    // Container whose single child is derived from a State<T>. The builder
    // is re-invoked whenever the state changes, and the resulting component
    // replaces the previous child. Use this to plug arbitrary component
    // trees into reactive state — paginated views, tabs, conditional UI.

    public static <T> UIComponent Dynamic(State<T> state, Function<T, UIComponent> builder) {
        return new DynamicComponent<>(state, builder);
    }

    public static <T> UIComponent Dynamic(State<T> state, Supplier<UIComponent> builder) {
        return new DynamicComponent<>(state, value -> builder.get());
    }

    // ===== Pagination =====
    //
    // Page navigator bound to a State<Integer> (1-indexed). The default
    // siblings count is 2 (window of 5 around the current page). Pass a
    // larger value to expose more neighboring pages, or 0 for a minimal
    // first / current / last layout.

    public static UIComponent Pagination(State<Integer> state, int totalPages) {
        return Pagination(state, totalPages, 2, null, null);
    }

    public static UIComponent Pagination(State<Integer> state, int totalPages, int siblings) {
        return Pagination(state, totalPages, siblings, null, null);
    }

    public static UIComponent Pagination(State<Integer> state, int totalPages,
                                         Style buttonStyle, Style activeStyle) {
        return Pagination(state, totalPages, 2, buttonStyle, activeStyle);
    }

    public static UIComponent Pagination(State<Integer> state, int totalPages, int siblings,
                                         Style buttonStyle, Style activeStyle) {
        return new PaginationComponent(state, totalPages, siblings, buttonStyle, activeStyle);
    }

    // ===== Progress Bar =====
    //
    // Read-only bar bound to a State<Double>. For other numeric types, derive
    // via state.map(v -> v.doubleValue()).
    //
    // The label format is a DoubleFunction<String> applied to the current
    // value each frame. Pass null to disable the label. The default formatter
    // produces "Progression XX%" rounded against the [min, max] range.

    public static UIComponent ProgressBar(State<Double> state, double min, double max) {
        return ProgressBar(state, min, max, ProgressBarComponent.defaultLabelFormat(min, max), null, null);
    }

    public static UIComponent ProgressBar(State<Double> state, double min, double max,
                                          DoubleFunction<String> labelFormat) {
        return ProgressBar(state, min, max, labelFormat, null, null);
    }

    public static UIComponent ProgressBar(State<Double> state, double min, double max,
                                          Style barStyle, Style fillStyle) {
        return ProgressBar(state, min, max,
                ProgressBarComponent.defaultLabelFormat(min, max), barStyle, fillStyle);
    }

    public static UIComponent ProgressBar(State<Double> state, double min, double max,
                                          DoubleFunction<String> labelFormat,
                                          Style barStyle, Style fillStyle) {
        return new ProgressBarComponent(barStyle, fillStyle, min, max, state::get, labelFormat);
    }

    // ===== Slider Components =====
    //
    // All sliders bind bidirectionally to a State<N>: dragging writes to the
    // state, external mutation of the state moves the thumb on next frame.
    //
    // Four overloads per numeric type:
    //   (state, min, max)
    //   (state, min, max, step)
    //   (state, min, max, step, barStyle, thumbStyle)                -> track derived from bar
    //   (state, min, max, step, barStyle, thumbStyle, trackStyle)    -> explicit track
    //
    // Default step is 1 for Int/Long/Short, 0 (continuous) for Double/Float.

    // --- Int ---

    public static UIComponent SliderInt(State<Integer> state, int min, int max) {
        return SliderInt(state, min, max, 1);
    }

    public static UIComponent SliderInt(State<Integer> state, int min, int max, int step) {
        return SliderInt(state, min, max, step, null, null);
    }

    public static UIComponent SliderInt(State<Integer> state, int min, int max, int step,
                                        Style barStyle, Style thumbStyle) {
        return SliderInt(state, min, max, step, barStyle, thumbStyle, null);
    }

    public static UIComponent SliderInt(State<Integer> state, int min, int max, int step,
                                        Style barStyle, Style thumbStyle, Style trackStyle) {
        return new SliderComponent(barStyle, thumbStyle, trackStyle,
                min, max, step,
                () -> (double) state.get(),
                v -> state.set((int) Math.round(v)));
    }

    // --- Double ---

    public static UIComponent SliderDouble(State<Double> state, double min, double max) {
        return SliderDouble(state, min, max, 0.0);
    }

    public static UIComponent SliderDouble(State<Double> state, double min, double max, double step) {
        return SliderDouble(state, min, max, step, null, null);
    }

    public static UIComponent SliderDouble(State<Double> state, double min, double max, double step,
                                           Style barStyle, Style thumbStyle) {
        return SliderDouble(state, min, max, step, barStyle, thumbStyle, null);
    }

    public static UIComponent SliderDouble(State<Double> state, double min, double max, double step,
                                           Style barStyle, Style thumbStyle, Style trackStyle) {
        return new SliderComponent(barStyle, thumbStyle, trackStyle,
                min, max, step,
                state::get,
                state::set);
    }

    // --- Float ---

    public static UIComponent SliderFloat(State<Float> state, float min, float max) {
        return SliderFloat(state, min, max, 0f);
    }

    public static UIComponent SliderFloat(State<Float> state, float min, float max, float step) {
        return SliderFloat(state, min, max, step, null, null);
    }

    public static UIComponent SliderFloat(State<Float> state, float min, float max, float step,
                                          Style barStyle, Style thumbStyle) {
        return SliderFloat(state, min, max, step, barStyle, thumbStyle, null);
    }

    public static UIComponent SliderFloat(State<Float> state, float min, float max, float step,
                                          Style barStyle, Style thumbStyle, Style trackStyle) {
        return new SliderComponent(barStyle, thumbStyle, trackStyle,
                min, max, step,
                () -> (double) state.get(),
                v -> state.set((float) v));
    }

    // --- Long ---

    public static UIComponent SliderLong(State<Long> state, long min, long max) {
        return SliderLong(state, min, max, 1L);
    }

    public static UIComponent SliderLong(State<Long> state, long min, long max, long step) {
        return SliderLong(state, min, max, step, null, null);
    }

    public static UIComponent SliderLong(State<Long> state, long min, long max, long step,
                                         Style barStyle, Style thumbStyle) {
        return SliderLong(state, min, max, step, barStyle, thumbStyle, null);
    }

    public static UIComponent SliderLong(State<Long> state, long min, long max, long step,
                                         Style barStyle, Style thumbStyle, Style trackStyle) {
        return new SliderComponent(barStyle, thumbStyle, trackStyle,
                (double) min, (double) max, (double) step,
                () -> (double) state.get(),
                v -> state.set(Math.round(v)));
    }

    // --- Short ---

    public static UIComponent SliderShort(State<Short> state, short min, short max) {
        return SliderShort(state, min, max, (short) 1);
    }

    public static UIComponent SliderShort(State<Short> state, short min, short max, short step) {
        return SliderShort(state, min, max, step, null, null);
    }

    public static UIComponent SliderShort(State<Short> state, short min, short max, short step,
                                          Style barStyle, Style thumbStyle) {
        return SliderShort(state, min, max, step, barStyle, thumbStyle, null);
    }

    public static UIComponent SliderShort(State<Short> state, short min, short max, short step,
                                          Style barStyle, Style thumbStyle, Style trackStyle) {
        return new SliderComponent(barStyle, thumbStyle, trackStyle,
                min, max, step,
                () -> (double) state.get(),
                v -> state.set((short) Math.round(v)));
    }

    // ===== Animation =====
    //
    // Render-time wrapper that applies opacity / translate / scale through
    // DoubleSuppliers (typically Tweens). The wrapped child renders normally
    // — animations are visual-only, layout is unchanged.

    /** Start a fluent builder for an animated wrapper. Call .build() at the end. */
    public static AnimatedBuilder Animated(UIComponent child) {
        return new AnimatedBuilder(child);
    }

    /** Fade {@code child} in (0 → 1 alpha) over {@code durationMs}, EASE_OUT. */
    public static UIComponent FadeIn(UIComponent child, long durationMs) {
        return new AnimatedComponent(child, Tween.fadeIn(durationMs), null, null, null);
    }

    /** Fade {@code child} out (1 → 0 alpha) over {@code durationMs}, EASE_OUT. */
    public static UIComponent FadeOut(UIComponent child, long durationMs) {
        return new AnimatedComponent(child, Tween.fadeOut(durationMs), null, null, null);
    }

    /**
     * Slide {@code child} in from the given direction over {@code durationMs}.
     * Default offset is 100px; for finer control use the {@link #Animated} builder.
     */
    public static UIComponent SlideIn(UIComponent child, Direction from, long durationMs) {
        int offset = 100;
        return switch (from) {
            case LEFT  -> new AnimatedComponent(child, null,
                    Tween.over(-offset, 0, durationMs, Easing.OUT_CUBIC).play(), null, null);
            case RIGHT -> new AnimatedComponent(child, null,
                    Tween.over(offset, 0, durationMs, Easing.OUT_CUBIC).play(), null, null);
            case TOP   -> new AnimatedComponent(child, null, null,
                    Tween.over(-offset, 0, durationMs, Easing.OUT_CUBIC).play(), null);
            case BOTTOM -> new AnimatedComponent(child, null, null,
                    Tween.over(offset, 0, durationMs, Easing.OUT_CUBIC).play(), null);
        };
    }

    /** Builder collecting animation suppliers for an {@link AnimatedComponent}. */
    public static final class AnimatedBuilder {
        private final UIComponent child;
        private DoubleSupplier opacity;
        private DoubleSupplier translateX;
        private DoubleSupplier translateY;
        private DoubleSupplier scale;

        AnimatedBuilder(UIComponent child) {
            if (child == null) throw new IllegalArgumentException("child must not be null");
            this.child = child;
        }

        public AnimatedBuilder opacity(DoubleSupplier supplier) { this.opacity = supplier; return this; }
        public AnimatedBuilder translateX(DoubleSupplier supplier) { this.translateX = supplier; return this; }
        public AnimatedBuilder translateY(DoubleSupplier supplier) { this.translateY = supplier; return this; }
        public AnimatedBuilder translate(DoubleSupplier x, DoubleSupplier y) {
            this.translateX = x;
            this.translateY = y;
            return this;
        }
        public AnimatedBuilder scale(DoubleSupplier supplier) { this.scale = supplier; return this; }

        public UIComponent build() {
            return new AnimatedComponent(child, opacity, translateX, translateY, scale);
        }
    }

    // ===== Calendar =====
    //
    // Month-view calendar bound to a State<LocalDate>. Header arrows pan the
    // displayed month; clicking a day writes that date back to the state.
    // Three optional style modifiers: header, day, selected day.

    public static UIComponent Calendar(State<java.time.LocalDate> selected) {
        return new CalendarComponent(selected, null, null, null);
    }

    public static UIComponent Calendar(State<java.time.LocalDate> selected,
                                       Style headerStyle, Style dayStyle, Style selectedDayStyle) {
        return new CalendarComponent(selected, headerStyle, dayStyle, selectedDayStyle);
    }

    // ===== Select List =====
    //
    // Vertical list of selectable rows bound to a State<T>. Each item is
    // rendered by a function returning a UIComponent; the row's background
    // and text color come from rowStyle / selectedRowStyle. Clicking a row
    // writes that item to the state. The default renderer wraps
    // String.valueOf(item) in a Text component.

    public static <T> UIComponent SelectList(State<T> selection, List<T> items) {
        return SelectList(selection, items, item -> Text(String.valueOf(item)), null, null);
    }

    public static <T> UIComponent SelectList(State<T> selection, List<T> items,
                                             Function<T, UIComponent> renderer) {
        return SelectList(selection, items, renderer, null, null);
    }

    public static <T> UIComponent SelectList(State<T> selection, List<T> items,
                                             Style rowStyle, Style selectedRowStyle) {
        return SelectList(selection, items, item -> Text(String.valueOf(item)),
                rowStyle, selectedRowStyle);
    }

    public static <T> UIComponent SelectList(State<T> selection, List<T> items,
                                             Function<T, UIComponent> renderer,
                                             Style rowStyle, Style selectedRowStyle) {
        return new SelectListComponent<>(selection, items, renderer, rowStyle, selectedRowStyle);
    }

    // ===== Accordion =====
    //
    // Vertical stack of collapsible sections. Multi-open by default; use
    // AccordionSingle for single-open behavior. Bind to your own State<...>
    // for persistence/programmatic control, or pass none to use internal
    // state (with per-section defaultOpen flags honored).

    public static AccordionSection AccordionSection(String title, UIComponent body) {
        return new AccordionSection(title, body, false);
    }

    public static AccordionSection AccordionSection(String title, UIComponent body, boolean defaultOpen) {
        return new AccordionSection(title, body, defaultOpen);
    }

    // --- Multi-open ---

    public static UIComponent Accordion(AccordionSection... sections) {
        return Accordion((Style) null, sections);
    }

    public static UIComponent Accordion(Style headerStyle, AccordionSection... sections) {
        List<AccordionSection> list = List.of(sections);
        State<Set<Integer>> openSet = State.of(AccordionComponent.initialOpenSet(list, false));
        return new AccordionComponent(openSet, false, list, headerStyle);
    }

    public static UIComponent Accordion(State<Set<Integer>> openSet, AccordionSection... sections) {
        return Accordion(openSet, null, sections);
    }

    public static UIComponent Accordion(State<Set<Integer>> openSet, Style headerStyle, AccordionSection... sections) {
        return new AccordionComponent(openSet, false, List.of(sections), headerStyle);
    }

    // --- Single-open ---

    public static UIComponent AccordionSingle(AccordionSection... sections) {
        return AccordionSingle((Style) null, sections);
    }

    public static UIComponent AccordionSingle(Style headerStyle, AccordionSection... sections) {
        List<AccordionSection> list = List.of(sections);
        State<Set<Integer>> openSet = State.of(AccordionComponent.initialOpenSet(list, true));
        return new AccordionComponent(openSet, true, list, headerStyle);
    }

    public static UIComponent AccordionSingle(State<Integer> openIndex, AccordionSection... sections) {
        return AccordionSingle(openIndex, null, sections);
    }

    public static UIComponent AccordionSingle(State<Integer> openIndex, Style headerStyle, AccordionSection... sections) {
        Integer initial = openIndex.get();
        Set<Integer> initialSet = (initial != null && initial >= 0) ? Set.of(initial) : Set.of();
        State<Set<Integer>> internal = State.of(initialSet);
        AccordionComponent comp = new AccordionComponent(internal, true, List.of(sections), headerStyle);
        // Bridge: internal Set<Integer> ↔ external Integer.
        // The equals-check in State.set prevents the back-and-forth from looping.
        comp.track(internal.onChange(set -> {
            int idx = set.isEmpty() ? -1 : set.iterator().next();
            openIndex.set(idx);
        }));
        comp.track(openIndex.onChange(idx -> {
            internal.set((idx == null || idx < 0) ? Set.of() : Set.of(idx));
        }));
        return comp;
    }

    // ===== Skeleton =====
    //
    // Loading placeholder with a shimmer sweep. Three overloads: default
    // (full-width line, 12px tall), explicit width/height, and fully
    // configured (size + base + shimmer + period + style).

    public static UIComponent Skeleton() {
        return new SkeletonComponent();
    }

    public static UIComponent Skeleton(int width, int height) {
        return new SkeletonComponent(width, height);
    }

    public static UIComponent Skeleton(int width, int height,
                                       int baseColor, int shimmerColor,
                                       long periodMs, Style style) {
        return new SkeletonComponent(width, height, baseColor, shimmerColor, periodMs, style);
    }

    // ===== ComboBox =====
    //
    // Drop-down selector bound to a State<T>. Closed: shows the current
    // selection (or placeholder) in a button-like trigger. Open: popover with
    // every item; click to select, click outside to dismiss. The popover
    // renders through the screen overlay layer, so it can extend past its
    // parent's bounds without being clipped.

    public static <T> UIComponent ComboBox(State<T> selection, List<T> items) {
        return new ComboBoxComponent<>(selection, items, null,
                null, null, null, null, "Select…");
    }

    public static <T> UIComponent ComboBox(State<T> selection, List<T> items,
                                           Function<T, String> labeler) {
        return new ComboBoxComponent<>(selection, items, labeler,
                null, null, null, null, "Select…");
    }

    public static <T> UIComponent ComboBox(State<T> selection, List<T> items,
                                           Function<T, String> labeler, String placeholder) {
        return new ComboBoxComponent<>(selection, items, labeler,
                null, null, null, null, placeholder);
    }

    public static <T> UIComponent ComboBox(State<T> selection, List<T> items,
                                           Function<T, String> labeler,
                                           Style triggerStyle, Style popoverStyle) {
        return new ComboBoxComponent<>(selection, items, labeler,
                triggerStyle, popoverStyle, null, null, "Select…");
    }

    public static <T> UIComponent ComboBox(State<T> selection, List<T> items,
                                           Function<T, String> labeler,
                                           Style triggerStyle, Style popoverStyle,
                                           Style itemStyle, Style selectedItemStyle,
                                           String placeholder) {
        return new ComboBoxComponent<>(selection, items, labeler,
                triggerStyle, popoverStyle, itemStyle, selectedItemStyle, placeholder);
    }

    // ===== Spinner =====
    //
    // Indeterminate loading indicator. Fixed-size circular dot trail with
    // smooth rotation. Three overloads: default 24px, custom size, fully
    // configured (size + dot color + period in ms + style).

    public static UIComponent Spinner() {
        return new SpinnerComponent();
    }

    public static UIComponent Spinner(int size) {
        return new SpinnerComponent(size);
    }

    public static UIComponent Spinner(int size, int dotColor, long periodMs, Style style) {
        return new SpinnerComponent(size, dotColor, periodMs, style);
    }

    // ===== Tooltip =====
    //
    // Wraps any child. After the cursor hovers the child for delayMs (default
    // 500), a small popup with the given text appears near the cursor and
    // follows it until the cursor leaves. Auto-flips at screen edges. Use
    // \n in the text for multi-line content. Pass a custom Style for the
    // popup's background, text color, padding, border.

    public static UIComponent Tooltip(String text, UIComponent child) {
        return new TooltipComponent(text, child);
    }

    public static UIComponent Tooltip(String text, UIComponent child, Style tooltipStyle) {
        return new TooltipComponent(text, child, tooltipStyle);
    }

    public static UIComponent Tooltip(String text, UIComponent child, Style tooltipStyle, long delayMs) {
        return new TooltipComponent(text, child, tooltipStyle, delayMs);
    }

    // ===== Toast =====
    //
    // Global notification stack rendered at the top-right of the active
    // UIScreen. Toasts slide in, persist for `durationMs`, then slide out
    // and fade. Independent of any particular screen — calling Toast.info
    // from anywhere in mod code (on the render thread) enqueues a toast.

    public static final class Toast {
        private Toast() {}

        public static void info(String message) {
            org.triggersstudio.moddinglib.client.ui.toast.ToastManager.show(
                    message, org.triggersstudio.moddinglib.client.ui.toast.ToastType.INFO);
        }

        public static void success(String message) {
            org.triggersstudio.moddinglib.client.ui.toast.ToastManager.show(
                    message, org.triggersstudio.moddinglib.client.ui.toast.ToastType.SUCCESS);
        }

        public static void warning(String message) {
            org.triggersstudio.moddinglib.client.ui.toast.ToastManager.show(
                    message, org.triggersstudio.moddinglib.client.ui.toast.ToastType.WARNING);
        }

        public static void error(String message) {
            org.triggersstudio.moddinglib.client.ui.toast.ToastManager.show(
                    message, org.triggersstudio.moddinglib.client.ui.toast.ToastType.ERROR);
        }

        public static void show(String message,
                                org.triggersstudio.moddinglib.client.ui.toast.ToastType type,
                                long durationMs) {
            org.triggersstudio.moddinglib.client.ui.toast.ToastManager.show(message, type, durationMs);
        }
    }

    // ===== Screen Creation =====
    
    /**
     * Create a UIScreen with the given component tree.
     */
    public static UIScreen Screen(UIComponent component) {
        return new UIScreen(component, "Screen");
    }
    
    /**
     * Create a UIScreen with the given component tree and title.
     */
    public static UIScreen Screen(UIComponent component, String title) {
        return new UIScreen(component, title);
    }
}
