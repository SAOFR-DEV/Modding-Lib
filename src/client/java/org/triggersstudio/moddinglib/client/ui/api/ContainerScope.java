package org.triggersstudio.moddinglib.client.ui.api;

import net.minecraft.util.Identifier;
import org.triggersstudio.moddinglib.client.ui.components.AccordionSection;
import org.triggersstudio.moddinglib.client.ui.components.UIComponent;
import org.triggersstudio.moddinglib.client.ui.state.State;
import org.triggersstudio.moddinglib.client.ui.styling.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
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

    // --- Text Field ---
    public void TextField(State<String> state) {
        children.add(Components.TextField(state));
    }
    public void TextField(State<String> state, String placeholder) {
        children.add(Components.TextField(state, placeholder));
    }
    public void TextField(State<String> state, String placeholder, Style style) {
        children.add(Components.TextField(state, placeholder, style));
    }

    // --- Dynamic ---
    public <T> void Dynamic(State<T> state, Function<T, UIComponent> builder) {
        children.add(Components.Dynamic(state, builder));
    }
    public <T> void Dynamic(State<T> state, Supplier<UIComponent> builder) {
        children.add(Components.Dynamic(state, builder));
    }

    // --- Pagination ---
    public void Pagination(State<Integer> state, int totalPages) {
        children.add(Components.Pagination(state, totalPages));
    }
    public void Pagination(State<Integer> state, int totalPages, int siblings) {
        children.add(Components.Pagination(state, totalPages, siblings));
    }
    public void Pagination(State<Integer> state, int totalPages, Style buttonStyle, Style activeStyle) {
        children.add(Components.Pagination(state, totalPages, buttonStyle, activeStyle));
    }
    public void Pagination(State<Integer> state, int totalPages, int siblings,
                           Style buttonStyle, Style activeStyle) {
        children.add(Components.Pagination(state, totalPages, siblings, buttonStyle, activeStyle));
    }

    // --- Progress Bar ---
    public void ProgressBar(State<Double> state, double min, double max) {
        children.add(Components.ProgressBar(state, min, max));
    }
    public void ProgressBar(State<Double> state, double min, double max, DoubleFunction<String> labelFormat) {
        children.add(Components.ProgressBar(state, min, max, labelFormat));
    }
    public void ProgressBar(State<Double> state, double min, double max, Style barStyle, Style fillStyle) {
        children.add(Components.ProgressBar(state, min, max, barStyle, fillStyle));
    }
    public void ProgressBar(State<Double> state, double min, double max,
                            DoubleFunction<String> labelFormat,
                            Style barStyle, Style fillStyle) {
        children.add(Components.ProgressBar(state, min, max, labelFormat, barStyle, fillStyle));
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

    // --- Accordion (multi-open) ---
    public void Accordion(AccordionSection... sections) {
        children.add(Components.Accordion(sections));
    }
    public void Accordion(Style headerStyle, AccordionSection... sections) {
        children.add(Components.Accordion(headerStyle, sections));
    }
    public void Accordion(State<Set<Integer>> openSet, AccordionSection... sections) {
        children.add(Components.Accordion(openSet, sections));
    }
    public void Accordion(State<Set<Integer>> openSet, Style headerStyle, AccordionSection... sections) {
        children.add(Components.Accordion(openSet, headerStyle, sections));
    }

    // --- Accordion (single-open) ---
    public void AccordionSingle(AccordionSection... sections) {
        children.add(Components.AccordionSingle(sections));
    }
    public void AccordionSingle(Style headerStyle, AccordionSection... sections) {
        children.add(Components.AccordionSingle(headerStyle, sections));
    }
    public void AccordionSingle(State<Integer> openIndex, AccordionSection... sections) {
        children.add(Components.AccordionSingle(openIndex, sections));
    }
    public void AccordionSingle(State<Integer> openIndex, Style headerStyle, AccordionSection... sections) {
        children.add(Components.AccordionSingle(openIndex, headerStyle, sections));
    }
}
