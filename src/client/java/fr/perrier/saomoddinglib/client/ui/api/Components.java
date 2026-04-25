package fr.perrier.saomoddinglib.client.ui.api;

import net.minecraft.util.Identifier;
import fr.perrier.saomoddinglib.client.ui.components.*;
import fr.perrier.saomoddinglib.client.ui.layout.LayoutType;
import fr.perrier.saomoddinglib.client.ui.state.State;
import fr.perrier.saomoddinglib.client.ui.styling.Style;
import fr.perrier.saomoddinglib.client.ui.screen.UIScreen;

import java.util.function.Consumer;
import java.util.function.DoubleFunction;
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
