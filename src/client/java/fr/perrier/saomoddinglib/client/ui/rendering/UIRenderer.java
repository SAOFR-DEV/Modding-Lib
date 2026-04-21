package fr.perrier.saomoddinglib.client.ui.rendering;

import net.minecraft.client.gui.DrawContext;
import fr.perrier.saomoddinglib.client.ui.components.UIComponent;

/**
 * Central rendering coordinator for UI components.
 * Handles measure, layout, and render phases.
 */
public class UIRenderer {

    /**
     * Measure and layout a component tree, then render it.
     */
    public static void render(UIComponent root, DrawContext drawContext, int screenWidth, int screenHeight) {
        // Measure phase
        var measured = root.measure(screenWidth, screenHeight);

        // Layout phase
        root.layout(0, 0, measured.width, measured.height);

        // Render phase
        root.render(drawContext);
    }

    /**
     * Measure and layout only (without rendering).
     * Useful for computing sizes without drawing.
     */
    public static UIComponent.MeasureResult measureAndLayout(UIComponent root, int screenWidth, int screenHeight) {
        var measured = root.measure(screenWidth, screenHeight);
        root.layout(0, 0, measured.width, measured.height);
        return measured;
    }
}

