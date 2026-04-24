package fr.perrier.saomoddinglib.client.ui.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import fr.perrier.saomoddinglib.client.ui.components.UIComponent;
import fr.perrier.saomoddinglib.client.ui.context.UIContext;
import fr.perrier.saomoddinglib.client.ui.debug.DebugOverlay;
import fr.perrier.saomoddinglib.client.ui.rendering.UIRenderer;

/**
 * Minecraft Screen adapter for UI component trees.
 * Bridges Minecraft's screen lifecycle to the UI system.
 */
public class UIScreen extends Screen {
    private final UIComponent rootComponent;
    private final UIContext uiContext = new UIContext();

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
        // onAttach is idempotent, so a resize-triggered re-init is safe.
        rootComponent.onAttach(uiContext);
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

        // Debug overlay draws on top of everything else.
        DebugOverlay.render(drawContext, rootComponent, mouseX, mouseY,
                this.width, this.height, uiContext);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontalAmount, double verticalAmount) {
        if (DebugOverlay.handleMouseScroll(x, y, verticalAmount)) return true;
        return rootComponent.onMouseScroll(x, y, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (DebugOverlay.handleMouseClick(rootComponent, x, y, this.width, this.height)) return true;
        UIComponent preFocus = uiContext.getFocused();
        boolean consumed = rootComponent.onMouseClick(x, y, button);
        // Blur on click outside: if focus didn't change during dispatch and the
        // click wasn't inside the focused component, drop focus.
        UIComponent postFocus = uiContext.getFocused();
        if (preFocus != null && postFocus == preFocus && !preFocus.isPointInside(x, y)) {
            uiContext.clearFocus();
        }
        return consumed;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        if (DebugOverlay.handleMouseDrag(rootComponent, x, y)) return true;
        return rootComponent.onMouseDrag(x, y, dragX, dragY, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (DebugOverlay.handleMouseRelease()) return true;
        return rootComponent.onMouseRelease(x, y, button);
    }

    @Override
    public void mouseMoved(double x, double y) {
        rootComponent.onMouseMove(x, y);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (DebugOverlay.handleKeyToggle(keyCode, scanCode)) return true;
        UIComponent focused = uiContext.getFocused();
        if (focused != null && focused.onKeyPress(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == 256) { // ESC — fallback close only when no focus consumed it
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        UIComponent focused = uiContext.getFocused();
        if (focused != null && focused.onCharTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }
    
    public UIComponent getRootComponent() {
        return rootComponent;
    }
}
