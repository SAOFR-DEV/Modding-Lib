package org.triggersstudio.moddinglib.client.ui.components;

import org.triggersstudio.moddinglib.client.ui.context.UIContext;
import org.triggersstudio.moddinglib.client.ui.state.State;
import org.triggersstudio.moddinglib.client.ui.styling.Size;
import org.triggersstudio.moddinglib.client.ui.styling.Style;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Single-line editable text field bound bidirectionally to a {@link State<String>}.
 *
 * <p>Features:
 * <ul>
 *   <li>Caret + selection (click, shift+click, click+drag, shift+arrows).</li>
 *   <li>Navigation: ←/→, Home/End.</li>
 *   <li>Edit: typing, Backspace, Delete, replace selection on type.</li>
 *   <li>Clipboard: Ctrl+A (select all), Ctrl+C, Ctrl+X, Ctrl+V.</li>
 *   <li>Horizontal scroll to keep the caret visible when the text overflows.</li>
 *   <li>Placeholder shown when the buffer is empty and the field isn't focused.</li>
 * </ul>
 *
 * <p>The field subscribes to its {@link State} at attach time and keeps its
 * internal buffer in sync. On every edit, the new content is pushed back to
 * the state; the equals-check inside {@link State#set} prevents re-entry.
 */
public class TextFieldComponent extends UIComponent {

    private static final int DEFAULT_WIDTH = 150;
    private static final int DEFAULT_HEIGHT = 20;
    private static final int TEXT_HEIGHT = 10;
    private static final int DEFAULT_SELECTION_COLOR = 0x80_55_88_FF;
    private static final long CARET_BLINK_INTERVAL_MS = 500L;

    private final State<String> state;
    private final String placeholder;
    private final StringBuilder buffer;
    private Consumer<String> onSubmit;

    private int caret;
    private int anchor = -1; // -1 = no selection
    private int scrollOffset = 0;
    private boolean mouseDragging = false;
    private long lastCaretActivityMs = System.currentTimeMillis();

    public TextFieldComponent(State<String> state, String placeholder, Style style) {
        this(state, placeholder, style, null);
    }

    public TextFieldComponent(State<String> state, String placeholder, Style style,
                              Consumer<String> onSubmit) {
        super(style);
        if (state == null) {
            throw new IllegalArgumentException("TextField requires a non-null State");
        }
        this.state = state;
        this.placeholder = placeholder != null ? placeholder : "";
        String initial = state.get();
        this.buffer = new StringBuilder(initial != null ? initial : "");
        this.caret = this.buffer.length();
        this.onSubmit = onSubmit;
    }

    /**
     * Set or replace the submission callback fired when the user presses Enter.
     * The current buffer contents (post-edit) is passed to the consumer. Pass
     * {@code null} to disable.
     */
    public TextFieldComponent setOnSubmit(Consumer<String> onSubmit) {
        this.onSubmit = onSubmit;
        return this;
    }

    @Override
    public void onAttach(UIContext ctx) {
        if (isAttached()) return;
        super.onAttach(ctx);
        track(state.onChange(this::syncFromState));
    }

    private void syncFromState(String v) {
        if (v == null) v = "";
        if (buffer.toString().equals(v)) return; // our own push echoing back
        buffer.setLength(0);
        buffer.append(v);
        if (caret > buffer.length()) caret = buffer.length();
        if (anchor > buffer.length()) anchor = buffer.length();
        if (anchor == caret) anchor = -1;
        ensureCaretVisible();
    }

    // ===== Measure / Layout =====

    @Override
    public MeasureResult measure(int maxWidth, int maxHeight) {
        int w = resolveSize(style.getWidth(), DEFAULT_WIDTH, maxWidth);
        int h = resolveSize(style.getHeight(), DEFAULT_HEIGHT, maxHeight);
        return new MeasureResult(w, h);
    }

    @Override
    public void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // ===== Render =====

    @Override
    public void render(DrawContext drawContext) {
        if (style.getBackgroundColor() != 0) {
            drawContext.fill(x, y, x + width, y + height, style.getBackgroundColor());
        }
        if (style.getBorderWidth() > 0) {
            drawBorder(drawContext);
        }

        int innerX = x + style.getPadding().left;
        int innerY = y + style.getPadding().top;
        int innerW = Math.max(0, width - style.getPadding().getHorizontal());
        int innerH = Math.max(0, height - style.getPadding().getVertical());
        int textY = innerY + Math.max(0, (innerH - TEXT_HEIGHT) / 2);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        drawContext.enableScissor(innerX, innerY, innerX + innerW, innerY + innerH);
        try {
            boolean showPlaceholder = buffer.length() == 0 && !isFocused() && !placeholder.isEmpty();
            if (showPlaceholder) {
                drawContext.drawText(tr, placeholder, innerX, textY, resolvePlaceholderColor(), false);
            } else {
                String bufStr = buffer.toString();

                if (hasSelection()) {
                    int s = selStart();
                    int e = selEnd();
                    int sPx = tr.getWidth(bufStr.substring(0, s));
                    int ePx = tr.getWidth(bufStr.substring(0, e));
                    drawContext.fill(
                            innerX + sPx - scrollOffset,
                            textY - 1,
                            innerX + ePx - scrollOffset,
                            textY + TEXT_HEIGHT,
                            DEFAULT_SELECTION_COLOR
                    );
                }

                drawContext.drawText(tr, bufStr, innerX - scrollOffset, textY, style.getTextColor(), false);

                if (isFocused() && caretVisible()) {
                    int caretPx = tr.getWidth(bufStr.substring(0, caret));
                    int caretX = innerX + caretPx - scrollOffset;
                    drawContext.fill(caretX, textY - 1, caretX + 1, textY + TEXT_HEIGHT, style.getTextColor());
                }
            }
        } finally {
            drawContext.disableScissor();
        }
    }

    private void drawBorder(DrawContext drawContext) {
        int bw = style.getBorderWidth();
        int c = style.getBorderColor();
        drawContext.fill(x, y, x + width, y + bw, c);
        drawContext.fill(x, y + height - bw, x + width, y + height, c);
        drawContext.fill(x, y, x + bw, y + height, c);
        drawContext.fill(x + width - bw, y, x + width, y + height, c);
    }

    /**
     * Caret blinks 1Hz: solid on for {@link #CARET_BLINK_INTERVAL_MS}ms, off
     * for the same. The phase resets on caret movement / typing so the caret
     * is always visible at the moment of an edit.
     */
    private boolean caretVisible() {
        long since = System.currentTimeMillis() - lastCaretActivityMs;
        return ((since / CARET_BLINK_INTERVAL_MS) & 1L) == 0L;
    }

    private void resetCaretBlink() {
        lastCaretActivityMs = System.currentTimeMillis();
    }

    private int resolvePlaceholderColor() {
        int explicit = style.getPlaceholderColor();
        if (explicit != 0) return explicit;
        int tc = style.getTextColor();
        return (tc & 0x00_FF_FF_FF) | 0x80_00_00_00;
    }

    // ===== Mouse =====

    @Override
    public boolean onMouseClick(double mx, double my, int button) {
        if (button != 0) return false;
        if (!isPointInside(mx, my)) return false;
        requestFocus();
        int innerX = x + style.getPadding().left;
        int idx = pixelToIndex(mx - innerX + scrollOffset);
        if (Screen.hasShiftDown()) {
            if (anchor < 0) anchor = caret;
            caret = idx;
            if (anchor == caret) anchor = -1;
        } else {
            caret = idx;
            anchor = -1;
        }
        mouseDragging = true;
        ensureCaretVisible();
        return true;
    }

    @Override
    public boolean onMouseDrag(double mx, double my, double dragX, double dragY, int button) {
        if (!mouseDragging) return false;
        int innerX = x + style.getPadding().left;
        int idx = pixelToIndex(mx - innerX + scrollOffset);
        if (idx != caret) {
            if (anchor < 0) anchor = caret;
            caret = idx;
            if (anchor == caret) anchor = -1;
            ensureCaretVisible();
        }
        return true;
    }

    @Override
    public boolean onMouseRelease(double mx, double my, int button) {
        if (!mouseDragging) return false;
        mouseDragging = false;
        return true;
    }

    // ===== Keyboard =====

    @Override
    public boolean onKeyPress(int keyCode, int scanCode, int modifiers) {
        if (Screen.isSelectAll(keyCode)) { selectAll(); return true; }
        if (Screen.isCopy(keyCode))      { copy();      return true; }
        if (Screen.isPaste(keyCode))     { paste();     return true; }
        if (Screen.isCut(keyCode))       { cut();       return true; }

        boolean shift = Screen.hasShiftDown();
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT:
                moveCaret(caret - 1, shift);
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                moveCaret(caret + 1, shift);
                return true;
            case GLFW.GLFW_KEY_HOME:
                moveCaret(0, shift);
                return true;
            case GLFW.GLFW_KEY_END:
                moveCaret(buffer.length(), shift);
                return true;
            case GLFW.GLFW_KEY_BACKSPACE:
                if (hasSelection()) {
                    deleteSelection();
                } else if (caret > 0) {
                    buffer.deleteCharAt(--caret);
                }
                pushState();
                return true;
            case GLFW.GLFW_KEY_DELETE:
                if (hasSelection()) {
                    deleteSelection();
                } else if (caret < buffer.length()) {
                    buffer.deleteCharAt(caret);
                }
                pushState();
                return true;
            case GLFW.GLFW_KEY_ESCAPE:
                // Blur rather than let the screen close.
                if (context != null) context.clearFocus();
                return true;
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
                if (onSubmit != null) {
                    onSubmit.accept(buffer.toString());
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onCharTyped(char chr, int modifiers) {
        if (chr < ' ' || chr == 127) return false;
        insertText(String.valueOf(chr));
        pushState();
        return true;
    }

    @Override
    public void onFocus() {
        resetCaretBlink();
    }

    @Override
    public void onBlur() {
        anchor = -1;
        mouseDragging = false;
    }

    // ===== Selection + edit helpers =====

    private boolean hasSelection() {
        return anchor >= 0 && anchor != caret;
    }

    private int selStart() {
        return Math.min(caret, anchor);
    }

    private int selEnd() {
        return Math.max(caret, anchor);
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        int s = selStart();
        int e = selEnd();
        buffer.delete(s, e);
        caret = s;
        anchor = -1;
    }

    private void insertText(String text) {
        if (hasSelection()) deleteSelection();
        buffer.insert(caret, text);
        caret += text.length();
        anchor = -1;
    }

    private void moveCaret(int to, boolean extend) {
        int clamped = Math.max(0, Math.min(buffer.length(), to));
        if (extend) {
            if (anchor < 0) anchor = caret;
            caret = clamped;
            if (anchor == caret) anchor = -1;
        } else {
            caret = clamped;
            anchor = -1;
        }
        ensureCaretVisible();
    }

    private void selectAll() {
        if (buffer.length() == 0) return;
        anchor = 0;
        caret = buffer.length();
        ensureCaretVisible();
    }

    private void copy() {
        if (!hasSelection()) return;
        String selected = buffer.substring(selStart(), selEnd());
        MinecraftClient.getInstance().keyboard.setClipboard(selected);
    }

    private void cut() {
        if (!hasSelection()) return;
        copy();
        deleteSelection();
        pushState();
    }

    private void paste() {
        String clip = MinecraftClient.getInstance().keyboard.getClipboard();
        if (clip == null || clip.isEmpty()) return;
        StringBuilder filtered = new StringBuilder(clip.length());
        for (int i = 0; i < clip.length(); i++) {
            char c = clip.charAt(i);
            if (c >= ' ' && c != 127) filtered.append(c);
        }
        if (filtered.length() == 0) return;
        insertText(filtered.toString());
        pushState();
    }

    private void pushState() {
        state.set(buffer.toString());
        ensureCaretVisible();
    }

    // ===== Geometry helpers =====

    private int pixelToIndex(double px) {
        if (px <= 0) return 0;
        String text = buffer.toString();
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int totalWidth = tr.getWidth(text);
        if (px >= totalWidth) return text.length();
        String fit = tr.trimToWidth(text, (int) px);
        return fit.length();
    }

    private void ensureCaretVisible() {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int innerW = Math.max(1, width - style.getPadding().getHorizontal());
        int caretPx = tr.getWidth(buffer.substring(0, caret));
        if (caretPx < scrollOffset) {
            scrollOffset = caretPx;
        } else if (caretPx > scrollOffset + innerW - 1) {
            scrollOffset = caretPx - innerW + 1;
        }
        if (scrollOffset < 0) scrollOffset = 0;
        resetCaretBlink();
    }

    private static int resolveSize(int constraint, int fallback, int max) {
        if (Size.isWrapContent(constraint)) return Math.min(fallback, max);
        if (Size.isMatchParent(constraint)) return max;
        if (constraint <= 0) return Math.min(fallback, max);
        return Math.min(constraint, max);
    }
}
