package fr.perrier.saomoddinglib.client.ui.api;

import net.minecraft.util.Identifier;
import fr.perrier.saomoddinglib.client.ui.components.UIComponent;
import fr.perrier.saomoddinglib.client.ui.styling.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
}
