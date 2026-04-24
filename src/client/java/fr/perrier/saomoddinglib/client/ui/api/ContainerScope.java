package fr.perrier.saomoddinglib.client.ui.api;

import net.minecraft.util.Identifier;
import fr.perrier.saomoddinglib.client.ui.components.UIComponent;
import fr.perrier.saomoddinglib.client.ui.state.State;
import fr.perrier.saomoddinglib.client.ui.styling.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Builder scope passed to lambda-based container factories
 * (e.g. {@link Components#Column(Style, Consumer)}).
 * Exposes the same component constructors as {@link Components} and
 * accumulates children in declaration order.
 */
public class ContainerScope {
    private final List<UIComponent> children = new ArrayList<>();

    public List<UIComponent> children() {
        return children;
    }

    public void add(UIComponent component) {
        children.add(component);
    }

    public void Text(String content) {
        children.add(Components.Text(content));
    }

    public void Text(String content, Style style) {
        children.add(Components.Text(content, style));
    }

    public void Text(Supplier<String> contentSupplier) {
        children.add(Components.Text(contentSupplier));
    }

    public void Text(Supplier<String> contentSupplier, Style style) {
        children.add(Components.Text(contentSupplier, style));
    }

    public void Button(String label) {
        children.add(Components.Button(label));
    }

    public void Button(String label, Style style) {
        children.add(Components.Button(label, style));
    }

    public void Image(Identifier texture) {
        children.add(Components.Image(texture));
    }

    public void Image(Identifier texture, Style style) {
        children.add(Components.Image(texture, style));
    }

    public void Image(Identifier texture, int textureWidth, int textureHeight, Style style) {
        children.add(Components.Image(texture, textureWidth, textureHeight, style));
    }

    public void Row(Consumer<ContainerScope> content) {
        children.add(Components.Row(content));
    }

    public void Row(Style style, Consumer<ContainerScope> content) {
        children.add(Components.Row(style, content));
    }

    public void Column(Consumer<ContainerScope> content) {
        children.add(Components.Column(content));
    }

    public void Column(Style style, Consumer<ContainerScope> content) {
        children.add(Components.Column(style, content));
    }

    public void VScroll(Consumer<ContainerScope> content) {
        children.add(Components.VScroll(Components.Column(content)));
    }

    public void VScroll(Style style, Consumer<ContainerScope> content) {
        children.add(Components.VScroll(style, Components.Column(content)));
    }

    public void HScroll(Consumer<ContainerScope> content) {
        children.add(Components.HScroll(Components.Row(content)));
    }

    public void HScroll(Style style, Consumer<ContainerScope> content) {
        children.add(Components.HScroll(style, Components.Row(content)));
    }

    // --- Slider Int ---
    public void SliderInt(State<Integer> state, int min, int max) {
        children.add(Components.SliderInt(state, min, max));
    }
    public void SliderInt(State<Integer> state, int min, int max, int step) {
        children.add(Components.SliderInt(state, min, max, step));
    }
    public void SliderInt(State<Integer> state, int min, int max, int step, Style barStyle, Style thumbStyle) {
        children.add(Components.SliderInt(state, min, max, step, barStyle, thumbStyle));
    }
    public void SliderInt(State<Integer> state, int min, int max, int step, Style barStyle, Style thumbStyle, Style trackStyle) {
        children.add(Components.SliderInt(state, min, max, step, barStyle, thumbStyle, trackStyle));
    }

    // --- Slider Double ---
    public void SliderDouble(State<Double> state, double min, double max) {
        children.add(Components.SliderDouble(state, min, max));
    }
    public void SliderDouble(State<Double> state, double min, double max, double step) {
        children.add(Components.SliderDouble(state, min, max, step));
    }
    public void SliderDouble(State<Double> state, double min, double max, double step, Style barStyle, Style thumbStyle) {
        children.add(Components.SliderDouble(state, min, max, step, barStyle, thumbStyle));
    }
    public void SliderDouble(State<Double> state, double min, double max, double step, Style barStyle, Style thumbStyle, Style trackStyle) {
        children.add(Components.SliderDouble(state, min, max, step, barStyle, thumbStyle, trackStyle));
    }

    // --- Slider Float ---
    public void SliderFloat(State<Float> state, float min, float max) {
        children.add(Components.SliderFloat(state, min, max));
    }
    public void SliderFloat(State<Float> state, float min, float max, float step) {
        children.add(Components.SliderFloat(state, min, max, step));
    }
    public void SliderFloat(State<Float> state, float min, float max, float step, Style barStyle, Style thumbStyle) {
        children.add(Components.SliderFloat(state, min, max, step, barStyle, thumbStyle));
    }
    public void SliderFloat(State<Float> state, float min, float max, float step, Style barStyle, Style thumbStyle, Style trackStyle) {
        children.add(Components.SliderFloat(state, min, max, step, barStyle, thumbStyle, trackStyle));
    }

    // --- Slider Long ---
    public void SliderLong(State<Long> state, long min, long max) {
        children.add(Components.SliderLong(state, min, max));
    }
    public void SliderLong(State<Long> state, long min, long max, long step) {
        children.add(Components.SliderLong(state, min, max, step));
    }
    public void SliderLong(State<Long> state, long min, long max, long step, Style barStyle, Style thumbStyle) {
        children.add(Components.SliderLong(state, min, max, step, barStyle, thumbStyle));
    }
    public void SliderLong(State<Long> state, long min, long max, long step, Style barStyle, Style thumbStyle, Style trackStyle) {
        children.add(Components.SliderLong(state, min, max, step, barStyle, thumbStyle, trackStyle));
    }

    // --- Slider Short ---
    public void SliderShort(State<Short> state, short min, short max) {
        children.add(Components.SliderShort(state, min, max));
    }
    public void SliderShort(State<Short> state, short min, short max, short step) {
        children.add(Components.SliderShort(state, min, max, step));
    }
    public void SliderShort(State<Short> state, short min, short max, short step, Style barStyle, Style thumbStyle) {
        children.add(Components.SliderShort(state, min, max, step, barStyle, thumbStyle));
    }
    public void SliderShort(State<Short> state, short min, short max, short step, Style barStyle, Style thumbStyle, Style trackStyle) {
        children.add(Components.SliderShort(state, min, max, step, barStyle, thumbStyle, trackStyle));
    }
}
