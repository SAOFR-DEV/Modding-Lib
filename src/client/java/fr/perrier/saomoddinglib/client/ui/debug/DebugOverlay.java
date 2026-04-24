package fr.perrier.saomoddinglib.client.ui.debug;

import fr.perrier.saomoddinglib.client.ui.components.Container;
import fr.perrier.saomoddinglib.client.ui.components.UIComponent;
import fr.perrier.saomoddinglib.client.ui.context.UIContext;
import fr.perrier.saomoddinglib.client.ui.state.State;
import fr.perrier.saomoddinglib.client.ui.styling.Insets;
import fr.perrier.saomoddinglib.client.ui.styling.Style;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Browser-devtools-style inspector for the UI library. Toggled at runtime
 * with a KeyBinding (default F12) when {@link #isEnabled()} is true.
 *
 * <p>By default, enabled reflects {@code FabricLoader#isDevelopmentEnvironment()}:
 * the overlay is live in IDE-driven dev runs and inert in shipped jars.
 * A mod author can force either way via {@link #setEnabled(boolean)}.
 *
 * <p>When active, the overlay:
 * <ul>
 *   <li>Outlines the component under the cursor (or the pinned one).</li>
 *   <li>Shows an info panel with the target's class / bounds / padding and
 *       the screen's focused component.</li>
 *   <li>Shows a tree panel listing the UI hierarchy; clicking an entry pins
 *       that component.</li>
 *   <li>Lists all named {@link State}s with their current values.</li>
 *   <li>Click-and-hold on the main UI acts as an element picker (pinned
 *       target follows the cursor while dragging).</li>
 *   <li>Both panels are repositionable by dragging their header.</li>
 * </ul>
 *
 * <p>All state is held statically — one overlay per JVM.
 */
public final class DebugOverlay {

    // ==================================================================
    // Enablement / activation
    // ==================================================================

    private static boolean enabled = detectDefaultEnabled();
    private static boolean active = false;
    private static UIComponent pinned;

    private static KeyBinding toggleBinding;

    private static boolean detectDefaultEnabled() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Force the overlay on or off. When set to {@code false}, any active
     * session is terminated and pinned state is cleared.
     */
    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            active = false;
            pinned = null;
            dragState = DragState.IDLE;
        }
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * Install the key binding used to toggle the overlay. Must be called
     * during client initialization (before Minecraft finishes loading).
     */
    public static void setToggleKeyBinding(KeyBinding binding) {
        toggleBinding = binding;
    }

    // ==================================================================
    // Panels
    // ==================================================================

    private static final int PANEL_HEADER_H = 14;
    private static final int PANEL_TEXT_COLOR = 0xFF_DD_DD_DD;
    private static final int PANEL_DIM_COLOR = 0xFF_99_99_99;
    private static final int PANEL_BG = 0xE8_18_18_18;
    private static final int PANEL_HEADER_BG = 0xFF_28_28_28;
    private static final int PANEL_HIGHLIGHT = 0x40_55_88_FF;
    private static final int OUTLINE_COLOR = 0xFF_FF_55_55;
    private static final int PADDING_FILL = 0x40_55_AA_FF;

    private static int infoPanelX = 10;
    private static int infoPanelY = -1; // -1 ⇒ initialize to bottom on first render
    private static final int INFO_PANEL_W = 320;
    private static final int INFO_PANEL_H = 140;

    private static int treePanelX = -1; // -1 ⇒ initialize to right on first render
    private static int treePanelY = 20;
    private static final int TREE_PANEL_W = 220;
    private static final int TREE_PANEL_H = 300;
    private static final int TREE_ROW_H = 11;
    private static int treeScroll = 0;

    // ==================================================================
    // Drag state machine
    // ==================================================================

    private enum DragState { IDLE, DRAG_INFO_PANEL, DRAG_TREE_PANEL, PICKING }

    private static DragState dragState = DragState.IDLE;
    private static int dragOffsetX, dragOffsetY;

    // ==================================================================
    // Public entry points (called from UIScreen)
    // ==================================================================

    public static boolean handleKeyToggle(int keyCode, int scanCode) {
        if (!enabled) return false;
        boolean match = (toggleBinding != null && toggleBinding.matchesKey(keyCode, scanCode))
                || keyCode == GLFW.GLFW_KEY_F12; // fallback if binding not registered
        if (!match) return false;
        active = !active;
        if (!active) {
            pinned = null;
            dragState = DragState.IDLE;
        }
        return true;
    }

    public static boolean handleMouseClick(UIComponent root, double mx, double my,
                                           int screenW, int screenH) {
        if (!enabled || !active) return false;
        ensurePanelsInitialized(screenW, screenH);
        int x = (int) mx;
        int y = (int) my;

        // 1. Panel header → drag a panel
        if (inInfoPanelHeader(x, y)) {
            dragState = DragState.DRAG_INFO_PANEL;
            dragOffsetX = x - infoPanelX;
            dragOffsetY = y - infoPanelY;
            return true;
        }
        if (inTreePanelHeader(x, y)) {
            dragState = DragState.DRAG_TREE_PANEL;
            dragOffsetX = x - treePanelX;
            dragOffsetY = y - treePanelY;
            return true;
        }
        // 2. Click inside tree panel body → pick from tree
        if (inTreePanelBody(x, y)) {
            UIComponent picked = pickFromTree(root, x, y);
            if (picked != null) pinned = picked;
            return true;
        }
        // 3. Click inside info panel body → just consume (no action)
        if (inInfoPanelBody(x, y)) {
            return true;
        }
        // 4. Click on main UI → pin + enter picker mode
        UIComponent hit = hitTest(root, mx, my);
        pinned = hit;
        dragState = DragState.PICKING;
        return true;
    }

    public static boolean handleMouseDrag(UIComponent root, double mx, double my) {
        if (!enabled || !active) return false;
        switch (dragState) {
            case DRAG_INFO_PANEL:
                infoPanelX = (int) mx - dragOffsetX;
                infoPanelY = (int) my - dragOffsetY;
                return true;
            case DRAG_TREE_PANEL:
                treePanelX = (int) mx - dragOffsetX;
                treePanelY = (int) my - dragOffsetY;
                return true;
            case PICKING:
                UIComponent hit = hitTest(root, mx, my);
                if (hit != null) pinned = hit;
                return true;
            default:
                return false;
        }
    }

    public static boolean handleMouseRelease() {
        if (!enabled || !active) return false;
        if (dragState == DragState.IDLE) return false;
        dragState = DragState.IDLE;
        return true;
    }

    public static boolean handleMouseScroll(double mx, double my, double amount) {
        if (!enabled || !active) return false;
        int x = (int) mx;
        int y = (int) my;
        if (inTreePanel(x, y)) {
            treeScroll -= (int) (amount * TREE_ROW_H * 3);
            if (treeScroll < 0) treeScroll = 0;
            return true;
        }
        return false;
    }

    public static void render(DrawContext ctx, UIComponent root,
                              int mouseX, int mouseY,
                              int screenW, int screenH,
                              UIContext uiContext) {
        if (!enabled || !active) return;
        ensurePanelsInitialized(screenW, screenH);

        // Outline target
        UIComponent target = (pinned != null) ? pinned : hitTest(root, mouseX, mouseY);
        if (target != null) {
            drawTargetOverlay(ctx, target);
        }

        // Panels
        drawInfoPanel(ctx, target, uiContext);
        drawTreePanel(ctx, root, mouseX, mouseY);
    }

    // ==================================================================
    // Hit-test
    // ==================================================================

    private static UIComponent hitTest(UIComponent component, double mx, double my) {
        if (component == null) return null;
        if (!component.isPointInside(mx, my)) return null;
        if (component instanceof Container container) {
            List<UIComponent> children = container.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) {
                UIComponent hit = hitTest(children.get(i), mx, my);
                if (hit != null) return hit;
            }
        }
        return component;
    }

    // ==================================================================
    // Target overlay (outline + padding fill)
    // ==================================================================

    private static void drawTargetOverlay(DrawContext ctx, UIComponent c) {
        int x = c.getX();
        int y = c.getY();
        int w = c.getWidth();
        int h = c.getHeight();
        Style s = c.getStyle();
        Insets p = (s != null) ? s.getPadding() : Insets.ZERO;

        // Padding area fill (between outer box and content box)
        if (p.top > 0)    ctx.fill(x, y, x + w, y + p.top, PADDING_FILL);
        if (p.bottom > 0) ctx.fill(x, y + h - p.bottom, x + w, y + h, PADDING_FILL);
        if (p.left > 0)   ctx.fill(x, y + p.top, x + p.left, y + h - p.bottom, PADDING_FILL);
        if (p.right > 0)  ctx.fill(x + w - p.right, y + p.top, x + w, y + h - p.bottom, PADDING_FILL);

        // Outer outline
        drawRectOutline(ctx, x, y, w, h, OUTLINE_COLOR);
    }

    private static void drawRectOutline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    // ==================================================================
    // Info panel
    // ==================================================================

    private static void drawInfoPanel(DrawContext ctx, UIComponent target, UIContext uiCtx) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // Body
        ctx.fill(infoPanelX, infoPanelY, infoPanelX + INFO_PANEL_W, infoPanelY + INFO_PANEL_H, PANEL_BG);
        // Header
        ctx.fill(infoPanelX, infoPanelY, infoPanelX + INFO_PANEL_W, infoPanelY + PANEL_HEADER_H, PANEL_HEADER_BG);
        ctx.drawText(tr, "Inspector", infoPanelX + 6, infoPanelY + 3, PANEL_TEXT_COLOR, false);
        ctx.drawText(tr, "F12 to close", infoPanelX + INFO_PANEL_W - 72, infoPanelY + 3, PANEL_DIM_COLOR, false);

        int lineY = infoPanelY + PANEL_HEADER_H + 4;

        // --- Target section ---
        if (target != null) {
            ctx.drawText(tr, (pinned != null ? "pinned: " : "hover: ") + target.getClass().getSimpleName(),
                    infoPanelX + 6, lineY, PANEL_TEXT_COLOR, false);
            lineY += 10;
            ctx.drawText(tr, "bounds: x=" + target.getX() + " y=" + target.getY()
                            + " w=" + target.getWidth() + " h=" + target.getHeight(),
                    infoPanelX + 6, lineY, PANEL_DIM_COLOR, false);
            lineY += 10;
            Insets p = target.getStyle() != null ? target.getStyle().getPadding() : Insets.ZERO;
            ctx.drawText(tr, "padding: t=" + p.top + " r=" + p.right + " b=" + p.bottom + " l=" + p.left,
                    infoPanelX + 6, lineY, PANEL_DIM_COLOR, false);
            lineY += 10;
            if (target.getStyle() != null) {
                int bg = target.getStyle().getBackgroundColor();
                ctx.drawText(tr, "bg: " + formatColor(bg),
                        infoPanelX + 6, lineY, PANEL_DIM_COLOR, false);
                lineY += 10;
            }
        } else {
            ctx.drawText(tr, "(no target)", infoPanelX + 6, lineY, PANEL_DIM_COLOR, false);
            lineY += 10;
        }

        UIComponent focused = uiCtx != null ? uiCtx.getFocused() : null;
        ctx.drawText(tr, "focused: " + (focused != null ? focused.getClass().getSimpleName() : "—"),
                infoPanelX + 6, lineY, PANEL_DIM_COLOR, false);
        lineY += 12;

        // --- States section ---
        ctx.fill(infoPanelX + 6, lineY, infoPanelX + INFO_PANEL_W - 6, lineY + 1, 0xFF_33_33_33);
        lineY += 3;
        ctx.drawText(tr, "States (named)", infoPanelX + 6, lineY, PANEL_TEXT_COLOR, false);
        lineY += 10;

        List<State.NamedStateSnapshot> states = State.registeredStates();
        int maxY = infoPanelY + INFO_PANEL_H - 2;
        if (states.isEmpty()) {
            ctx.drawText(tr, "(none registered)", infoPanelX + 6, lineY, PANEL_DIM_COLOR, false);
        } else {
            for (State.NamedStateSnapshot s : states) {
                if (lineY + 10 > maxY) {
                    ctx.drawText(tr, "...", infoPanelX + 6, lineY, PANEL_DIM_COLOR, false);
                    break;
                }
                String line = s.name() + " = " + truncate(String.valueOf(s.value()), 32);
                ctx.drawText(tr, line, infoPanelX + 6, lineY, PANEL_TEXT_COLOR, false);
                lineY += 10;
            }
        }
    }

    // ==================================================================
    // Tree panel
    // ==================================================================

    private static void drawTreePanel(DrawContext ctx, UIComponent root, int mouseX, int mouseY) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // Body
        ctx.fill(treePanelX, treePanelY, treePanelX + TREE_PANEL_W, treePanelY + TREE_PANEL_H, PANEL_BG);
        // Header
        ctx.fill(treePanelX, treePanelY, treePanelX + TREE_PANEL_W, treePanelY + PANEL_HEADER_H, PANEL_HEADER_BG);
        ctx.drawText(tr, "Tree", treePanelX + 6, treePanelY + 3, PANEL_TEXT_COLOR, false);
        ctx.drawText(tr, "(scroll · click to pin)", treePanelX + 40, treePanelY + 3, PANEL_DIM_COLOR, false);

        int bodyX = treePanelX + 4;
        int bodyY = treePanelY + PANEL_HEADER_H + 2;
        int bodyH = TREE_PANEL_H - PANEL_HEADER_H - 2;

        ctx.enableScissor(treePanelX, bodyY, treePanelX + TREE_PANEL_W, bodyY + bodyH);
        try {
            List<TreeEntry> flat = new ArrayList<>();
            flatten(root, 0, flat);
            int y = bodyY - treeScroll;
            boolean mouseInside = inTreePanelBody(mouseX, mouseY);
            for (TreeEntry e : flat) {
                if (y + TREE_ROW_H >= bodyY && y <= bodyY + bodyH) {
                    int rowColor = e.component == pinned ? PANEL_HIGHLIGHT : 0;
                    if (mouseInside && mouseY >= y && mouseY < y + TREE_ROW_H) {
                        rowColor = PANEL_HIGHLIGHT;
                    }
                    if (rowColor != 0) {
                        ctx.fill(treePanelX, y, treePanelX + TREE_PANEL_W, y + TREE_ROW_H, rowColor);
                    }
                    String label = indent(e.depth) + e.component.getClass().getSimpleName();
                    int color = (e.component == pinned) ? 0xFF_FF_FF_FF : PANEL_TEXT_COLOR;
                    ctx.drawText(tr, label, bodyX, y + 2, color, false);
                }
                y += TREE_ROW_H;
            }
        } finally {
            ctx.disableScissor();
        }
    }

    private static UIComponent pickFromTree(UIComponent root, int mx, int my) {
        int bodyY = treePanelY + PANEL_HEADER_H + 2;
        int rel = my - bodyY + treeScroll;
        if (rel < 0) return null;
        int index = rel / TREE_ROW_H;
        List<TreeEntry> flat = new ArrayList<>();
        flatten(root, 0, flat);
        if (index < 0 || index >= flat.size()) return null;
        return flat.get(index).component;
    }

    private static void flatten(UIComponent c, int depth, List<TreeEntry> out) {
        if (c == null) return;
        out.add(new TreeEntry(c, depth));
        if (c instanceof Container container) {
            for (UIComponent child : container.getChildren()) {
                flatten(child, depth + 1, out);
            }
        }
    }

    private record TreeEntry(UIComponent component, int depth) {}

    private static String indent(int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) sb.append("  ");
        return sb.toString();
    }

    // ==================================================================
    // Panel region checks
    // ==================================================================

    private static void ensurePanelsInitialized(int screenW, int screenH) {
        if (infoPanelY < 0) {
            infoPanelY = Math.max(10, screenH - INFO_PANEL_H - 10);
        }
        if (treePanelX < 0) {
            treePanelX = Math.max(10, screenW - TREE_PANEL_W - 10);
        }
    }

    private static boolean inInfoPanel(int x, int y) {
        return x >= infoPanelX && x < infoPanelX + INFO_PANEL_W
                && y >= infoPanelY && y < infoPanelY + INFO_PANEL_H;
    }

    private static boolean inInfoPanelHeader(int x, int y) {
        return inInfoPanel(x, y) && y < infoPanelY + PANEL_HEADER_H;
    }

    private static boolean inInfoPanelBody(int x, int y) {
        return inInfoPanel(x, y) && y >= infoPanelY + PANEL_HEADER_H;
    }

    private static boolean inTreePanel(int x, int y) {
        return x >= treePanelX && x < treePanelX + TREE_PANEL_W
                && y >= treePanelY && y < treePanelY + TREE_PANEL_H;
    }

    private static boolean inTreePanelHeader(int x, int y) {
        return inTreePanel(x, y) && y < treePanelY + PANEL_HEADER_H;
    }

    private static boolean inTreePanelBody(int x, int y) {
        return inTreePanel(x, y) && y >= treePanelY + PANEL_HEADER_H;
    }

    // ==================================================================
    // Formatting helpers
    // ==================================================================

    private static String formatColor(int argb) {
        return String.format("#%08X", argb);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "null";
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    private DebugOverlay() {}
}
