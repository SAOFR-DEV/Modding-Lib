package fr.perrier.saomoddinglib.client.ui.api;

import net.minecraft.util.Identifier;
import fr.perrier.saomoddinglib.client.ui.components.*;
import fr.perrier.saomoddinglib.client.ui.layout.LayoutType;
import fr.perrier.saomoddinglib.client.ui.styling.Style;
import fr.perrier.saomoddinglib.client.ui.screen.UIScreen;

import java.util.function.Consumer;

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
