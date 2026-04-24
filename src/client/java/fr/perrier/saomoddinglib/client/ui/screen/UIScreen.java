package fr.perrier.saomoddinglib.client.ui.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import fr.perrier.saomoddinglib.client.ui.components.UIComponent;
import fr.perrier.saomoddinglib.client.ui.rendering.UIRenderer;

/**
 * Minecraft Screen adapter for UI component trees.
 * Bridges Minecraft's screen lifecycle to the UI system.
 */
public class UIScreen extends Screen {
    private final UIComponent rootComponent;
    
    public UIScreen(UIComponent rootComponent, String title) {
        super(Text.literal(title));
        this.rootComponent = rootComponent;
    }
    
    public UIScreen(UIComponent rootComponent) {
        this(rootComponent, "");
    }
    
    @Override
    protected void init() {
        super.init();
        // onAttach() is idempotent, so a resize-triggered re-init is safe.
        rootComponent.onAttach();
        // Measure and layout the component tree
        UIRenderer.measureAndLayout(rootComponent, this.width, this.height);
    }

    @Override
    public void removed() {
        rootComponent.onDetach();
        super.removed();
    }
    
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float partialTick) {
        // Render background (optional - can be customized)
        this.renderBackground(drawContext, mouseX, mouseY, partialTick);
        
        // Render the UI component tree
        UIRenderer.render(rootComponent, drawContext, this.width, this.height);
    }
    
    @Override
    public boolean mouseScrolled(double x, double y, double horizontalAmount, double verticalAmount) {
        return rootComponent.onMouseScroll(x, y, verticalAmount);
    }
    
    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return rootComponent.onMouseClick(x, y, button);
    }
    
    @Override
    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        return rootComponent.onMouseDrag(x, y, dragX, dragY, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return rootComponent.onMouseRelease(x, y, button);
    }
    
    @Override
    public void mouseMoved(double x, double y) {
        rootComponent.onMouseMove(x, y);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC key
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    public UIComponent getRootComponent() {
        return rootComponent;
    }
}
