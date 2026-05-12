package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;
import org.triggersstudio.moddinglib.client.ui.context.UIContext;
import org.triggersstudio.moddinglib.client.ui.rendering.PaintRenderer;
import org.triggersstudio.moddinglib.client.ui.rendering.Shapes;
import org.triggersstudio.moddinglib.client.ui.state.State;
import org.triggersstudio.moddinglib.client.ui.styling.Size;
import org.triggersstudio.moddinglib.client.ui.styling.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-line editable text area bound to a {@code State<String>}. Lines are
 * stored as a {@link List} of {@link StringBuilder}; the bound state holds
 * the joined buffer with {@code \n} separators.
 *
 * <p>Editing:
 * <ul>
 *   <li>Caret + selection (click, drag, shift+click, shift+arrow keys).</li>
 *   <li>Navigation: ←/→/↑/↓, Home/End (line), Ctrl+Home/End (document),
 *       PageUp/PageDown.</li>
 *   <li>Edit: typing, Backspace, Delete, Enter (newline), Tab (inserts
 *       configurable spaces).</li>
 *   <li>Clipboard: Ctrl+A, Ctrl+C, Ctrl+X, Ctrl+V (multiline-aware).</li>
 *   <li>Vertical scroll auto-tracks the caret.</li>
 *   <li>Optional {@code maxLength} cap on total character count (counted
 *       against the joined buffer, including newlines).</li>
 * </ul>
 *
 * <p>The blink phase resets on caret movement / typing so the caret is
 * visible at the moment of an edit (same convention as TextField).
 */
public class TextAreaComponent extends UIComponent {

    private static final int DEFAULT_WIDTH = 240;
    private static final int DEFAULT_HEIGHT = 120;
    private static final int LINE_HEIGHT = 11;
    private static final int DEFAULT_SELECTION_COLOR = 0x80_55_88_FF;
    private static final long CARET_BLINK_INTERVAL_MS = 500L;

    private final State<String> state;
    private final String placeholder;
    private final int maxLength; // <= 0 means no cap
    private final int tabSpaces;

    private final List<StringBuilder> lines = new ArrayList<>();

    private int caretRow = 0, caretCol = 0;
    private int anchorRow = -1, anchorCol = -1;
    private int scrollRows = 0;
    private boolean mouseDragging = false;
    private long lastCaretActivityMs = System.currentTimeMillis();

    public TextAreaComponent(State<String> state, String placeholder, Style style,
                             int maxLength, int tabSpaces) {
        super(style);
        if (state == null) throw new IllegalArgumentException("TextArea requires a non-null State");
        this.state = state;
        this.placeholder = placeholder != null ? placeholder : "";
        this.maxLength = maxLength;
        this.tabSpaces = tabSpaces > 0 ? tabSpaces : 4;
        loadFromString(state.get());
    }

    @Override
    public void onAttach(UIContext ctx) {
        if (isAttached()) return;
        super.onAttach(ctx);
        track(state.onChange(this::syncFromState));
    }

    private void loadFromString(String text) {
        lines.clear();
        if (text == null || text.isEmpty()) {
            lines.add(new StringBuilder());
            return;
        }
        for (String s : text.split("\n", -1)) {
            lines.add(new StringBuilder(s));
        }
    }

    private void syncFromState(String v) {
        if (v == null) v = "";
        if (currentText().equals(v)) return; // our own push echoing back
        loadFromString(v);
        clampCaret();
        clearSelection();
        ensureCaretVisible();
    }

    private String currentText() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            out.append(lines.get(i));
            if (i < lines.size() - 1) out.append('\n');
        }
        return out.toString();
    }

    private void pushState() {
        state.set(currentText());
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
        int radius = style.getBorderRadius();
        if (style.getBackgroundColor() != 0) {
            PaintRenderer.fillRect(drawContext, x, y, width, height,
                    style.getBackgroundPaint(), radius);
        }
        if (style.getBorderWidth() > 0) {
            Shapes.drawRoundRectBorder(drawContext, x, y, width, height,
                    radius, style.getBorderWidth(), style.getBorderColor());
        }

        int innerX = x + style.getPadding().left;
        int innerY = y + style.getPadding().top;
        int innerW = Math.max(0, width - style.getPadding().getHorizontal());
        int innerH = Math.max(0, height - style.getPadding().getVertical());

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        drawContext.enableScissor(innerX, innerY, innerX + innerW, innerY + innerH);
        try {
            boolean isEmpty = lines.size() == 1 && lines.get(0).length() == 0;
            if (isEmpty && !isFocused() && !placeholder.isEmpty()) {
                drawContext.drawText(tr, placeholder, innerX, innerY, resolvePlaceholderColor(), false);
                return;
            }

            int visibleRows = Math.max(1, innerH / LINE_HEIGHT);
            int firstRow = scrollRows;
            int lastRow = Math.min(lines.size(), firstRow + visibleRows + 1);

            // Selection rects first, so text draws on top.
            if (hasSelection()) {
                int sStart = Math.min(rowColToOffset(anchorRow, anchorCol),
                        rowColToOffset(caretRow, caretCol));
                int sEnd = Math.max(rowColToOffset(anchorRow, anchorCol),
                        rowColToOffset(caretRow, caretCol));
                int[] startRC = offsetToRowCol(sStart);
                int[] endRC = offsetToRowCol(sEnd);
                drawSelection(drawContext, tr, innerX, innerY,
                        startRC[0], startRC[1], endRC[0], endRC[1]);
            }

            for (int row = firstRow; row < lastRow; row++) {
                int ty = innerY + (row - firstRow) * LINE_HEIGHT;
                drawContext.drawText(tr, lines.get(row).toString(), innerX, ty,
                        style.getTextColor(), false);
            }

            if (isFocused() && caretVisible()) {
                int cy = innerY + (caretRow - firstRow) * LINE_HEIGHT;
                if (cy >= innerY && cy + LINE_HEIGHT <= innerY + innerH) {
                    int cx = innerX + tr.getWidth(lines.get(caretRow).substring(0, caretCol));
                    drawContext.fill(cx, cy - 1, cx + 1, cy + LINE_HEIGHT - 1, style.getTextColor());
                }
            }
        } finally {
            drawContext.disableScissor();
        }
    }

    private void drawSelection(DrawContext ctx, TextRenderer tr, int innerX, int innerY,
                               int sRow, int sCol, int eRow, int eCol) {
        int visibleRows = Math.max(1, (height - style.getPadding().getVertical()) / LINE_HEIGHT);
        int firstRow = scrollRows;
        int lastRow = Math.min(lines.size(), firstRow + visibleRows + 1);
        for (int row = Math.max(firstRow, sRow); row <= Math.min(lastRow - 1, eRow); row++) {
            int colStart = (row == sRow) ? sCol : 0;
            int colEnd = (row == eRow) ? eCol : lines.get(row).length();
            String line = lines.get(row).toString();
            int xStart = innerX + tr.getWidth(line.substring(0, colStart));
            int xEnd = innerX + tr.getWidth(line.substring(0, colEnd));
            // Render an extra px on multi-line selections so trailing newlines look highlighted.
            if (row != eRow) xEnd = Math.max(xEnd, xStart + 4);
            int ty = innerY + (row - firstRow) * LINE_HEIGHT;
            ctx.fill(xStart, ty - 1, xEnd, ty + LINE_HEIGHT - 1, DEFAULT_SELECTION_COLOR);
        }
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
        int[] rc = pixelToRowCol(mx, my);
        if (Screen.hasShiftDown()) {
            if (anchorRow < 0) {
                anchorRow = caretRow;
                anchorCol = caretCol;
            }
            caretRow = rc[0];
            caretCol = rc[1];
            if (anchorRow == caretRow && anchorCol == caretCol) clearSelection();
        } else {
            caretRow = rc[0];
            caretCol = rc[1];
            clearSelection();
        }
        mouseDragging = true;
        ensureCaretVisible();
        return true;
    }

    @Override
    public boolean onMouseDrag(double mx, double my, double dragX, double dragY, int button) {
        if (!mouseDragging) return false;
        int[] rc = pixelToRowCol(mx, my);
        if (rc[0] != caretRow || rc[1] != caretCol) {
            if (anchorRow < 0) {
                anchorRow = caretRow;
                anchorCol = caretCol;
            }
            caretRow = rc[0];
            caretCol = rc[1];
            if (anchorRow == caretRow && anchorCol == caretCol) clearSelection();
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

    @Override
    public boolean onMouseScroll(double mx, double my, double scrollDelta) {
        if (!isPointInside(mx, my)) return false;
        scrollRows -= (int) Math.signum(scrollDelta);
        clampScroll();
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
        boolean ctrl = Screen.hasControlDown();

        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT:
                moveCaretLeft(shift);
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                moveCaretRight(shift);
                return true;
            case GLFW.GLFW_KEY_UP:
                moveCaretVertical(-1, shift);
                return true;
            case GLFW.GLFW_KEY_DOWN:
                moveCaretVertical(1, shift);
                return true;
            case GLFW.GLFW_KEY_HOME:
                if (ctrl) moveCaretTo(0, 0, shift);
                else      moveCaretTo(caretRow, 0, shift);
                return true;
            case GLFW.GLFW_KEY_END:
                if (ctrl) moveCaretTo(lines.size() - 1, lines.get(lines.size() - 1).length(), shift);
                else      moveCaretTo(caretRow, lines.get(caretRow).length(), shift);
                return true;
            case GLFW.GLFW_KEY_PAGE_UP:
                moveCaretVertical(-pageSize(), shift);
                return true;
            case GLFW.GLFW_KEY_PAGE_DOWN:
                moveCaretVertical(pageSize(), shift);
                return true;
            case GLFW.GLFW_KEY_BACKSPACE:
                if (hasSelection()) deleteSelection();
                else backspace();
                pushState();
                return true;
            case GLFW.GLFW_KEY_DELETE:
                if (hasSelection()) deleteSelection();
                else deleteForward();
                pushState();
                return true;
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
                insertChar('\n');
                pushState();
                return true;
            case GLFW.GLFW_KEY_TAB:
                insertText(" ".repeat(tabSpaces));
                pushState();
                return true;
            case GLFW.GLFW_KEY_ESCAPE:
                if (context != null) context.clearFocus();
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean onCharTyped(char chr, int modifiers) {
        if (chr < ' ' || chr == 127) return false;
        insertChar(chr);
        pushState();
        return true;
    }

    @Override
    public void onFocus() {
        resetCaretBlink();
    }

    @Override
    public void onBlur() {
        clearSelection();
        mouseDragging = false;
    }

    // ===== Edit ops =====

    private void backspace() {
        if (caretCol > 0) {
            lines.get(caretRow).deleteCharAt(--caretCol);
        } else if (caretRow > 0) {
            int prevLen = lines.get(caretRow - 1).length();
            lines.get(caretRow - 1).append(lines.get(caretRow));
            lines.remove(caretRow);
            caretRow--;
            caretCol = prevLen;
        }
    }

    private void deleteForward() {
        StringBuilder line = lines.get(caretRow);
        if (caretCol < line.length()) {
            line.deleteCharAt(caretCol);
        } else if (caretRow < lines.size() - 1) {
            line.append(lines.get(caretRow + 1));
            lines.remove(caretRow + 1);
        }
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        int s = Math.min(rowColToOffset(anchorRow, anchorCol),
                rowColToOffset(caretRow, caretCol));
        int e = Math.max(rowColToOffset(anchorRow, anchorCol),
                rowColToOffset(caretRow, caretCol));
        StringBuilder full = new StringBuilder(currentText());
        full.delete(s, e);
        loadFromString(full.toString());
        int[] rc = offsetToRowCol(s);
        caretRow = rc[0];
        caretCol = rc[1];
        clearSelection();
    }

    private void insertChar(char c) {
        insertText(String.valueOf(c));
    }

    private void insertText(String text) {
        if (text.isEmpty()) return;
        if (hasSelection()) deleteSelection();

        int currentLen = currentTextLength();
        int allowed = maxLength > 0 ? Math.max(0, maxLength - currentLen) : Integer.MAX_VALUE;
        if (text.length() > allowed) {
            text = text.substring(0, allowed);
            if (text.isEmpty()) return;
        }

        // Split insertion on newlines so multi-line paste works.
        String[] segments = text.split("\n", -1);
        if (segments.length == 1) {
            lines.get(caretRow).insert(caretCol, text);
            caretCol += text.length();
            return;
        }
        StringBuilder cur = lines.get(caretRow);
        String tail = cur.substring(caretCol);
        cur.setLength(caretCol);
        cur.append(segments[0]);
        for (int i = 1; i < segments.length; i++) {
            caretRow++;
            StringBuilder nl = new StringBuilder(segments[i]);
            lines.add(caretRow, nl);
            caretCol = nl.length();
        }
        // Append any remaining tail of the original line at the new caret line.
        lines.get(caretRow).append(tail);
    }

    private int currentTextLength() {
        int total = 0;
        for (int i = 0; i < lines.size(); i++) {
            total += lines.get(i).length();
            if (i < lines.size() - 1) total++; // newline
        }
        return total;
    }

    // ===== Caret movement =====

    private void moveCaretLeft(boolean extend) {
        if (caretCol > 0) {
            stage(extend);
            caretCol--;
        } else if (caretRow > 0) {
            stage(extend);
            caretRow--;
            caretCol = lines.get(caretRow).length();
        }
        if (!extend) clearSelection();
        else if (anchorRow == caretRow && anchorCol == caretCol) clearSelection();
        ensureCaretVisible();
    }

    private void moveCaretRight(boolean extend) {
        if (caretCol < lines.get(caretRow).length()) {
            stage(extend);
            caretCol++;
        } else if (caretRow < lines.size() - 1) {
            stage(extend);
            caretRow++;
            caretCol = 0;
        }
        if (!extend) clearSelection();
        else if (anchorRow == caretRow && anchorCol == caretCol) clearSelection();
        ensureCaretVisible();
    }

    private void moveCaretVertical(int delta, boolean extend) {
        int newRow = Math.max(0, Math.min(lines.size() - 1, caretRow + delta));
        int newCol = Math.min(caretCol, lines.get(newRow).length());
        moveCaretTo(newRow, newCol, extend);
    }

    private void moveCaretTo(int row, int col, boolean extend) {
        stage(extend);
        caretRow = Math.max(0, Math.min(lines.size() - 1, row));
        caretCol = Math.max(0, Math.min(lines.get(caretRow).length(), col));
        if (!extend) clearSelection();
        else if (anchorRow == caretRow && anchorCol == caretCol) clearSelection();
        ensureCaretVisible();
    }

    private void stage(boolean extend) {
        if (extend && anchorRow < 0) {
            anchorRow = caretRow;
            anchorCol = caretCol;
        }
    }

    private boolean hasSelection() {
        return anchorRow >= 0 && (anchorRow != caretRow || anchorCol != caretCol);
    }

    private void clearSelection() {
        anchorRow = -1;
        anchorCol = -1;
    }

    private void selectAll() {
        if (lines.size() == 1 && lines.get(0).length() == 0) return;
        anchorRow = 0;
        anchorCol = 0;
        caretRow = lines.size() - 1;
        caretCol = lines.get(caretRow).length();
        ensureCaretVisible();
    }

    private void copy() {
        if (!hasSelection()) return;
        int s = Math.min(rowColToOffset(anchorRow, anchorCol),
                rowColToOffset(caretRow, caretCol));
        int e = Math.max(rowColToOffset(anchorRow, anchorCol),
                rowColToOffset(caretRow, caretCol));
        String selected = currentText().substring(s, e);
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
        // Normalize line endings, drop control chars except newline.
        clip = clip.replace("\r\n", "\n").replace("\r", "\n");
        StringBuilder filtered = new StringBuilder(clip.length());
        for (int i = 0; i < clip.length(); i++) {
            char c = clip.charAt(i);
            if (c == '\n' || (c >= ' ' && c != 127)) filtered.append(c);
        }
        if (filtered.length() == 0) return;
        insertText(filtered.toString());
        pushState();
    }

    // ===== Geometry / scroll =====

    private int[] pixelToRowCol(double mx, double my) {
        int innerX = x + style.getPadding().left;
        int innerY = y + style.getPadding().top;
        int row = scrollRows + (int) ((my - innerY) / LINE_HEIGHT);
        if (row < 0) row = 0;
        if (row >= lines.size()) row = lines.size() - 1;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String line = lines.get(row).toString();
        int totalWidth = tr.getWidth(line);
        double localX = mx - innerX;
        int col;
        if (localX <= 0) col = 0;
        else if (localX >= totalWidth) col = line.length();
        else col = tr.trimToWidth(line, (int) localX).length();
        return new int[]{row, col};
    }

    private int rowColToOffset(int row, int col) {
        int offset = 0;
        for (int i = 0; i < row && i < lines.size(); i++) {
            offset += lines.get(i).length() + 1; // +1 for the newline
        }
        return offset + col;
    }

    private int[] offsetToRowCol(int offset) {
        int remaining = offset;
        for (int i = 0; i < lines.size(); i++) {
            int len = lines.get(i).length();
            if (remaining <= len) return new int[]{i, remaining};
            remaining -= len + 1; // newline
        }
        int last = lines.size() - 1;
        return new int[]{last, lines.get(last).length()};
    }

    private void clampCaret() {
        if (caretRow >= lines.size()) caretRow = lines.size() - 1;
        if (caretRow < 0) caretRow = 0;
        if (caretCol > lines.get(caretRow).length()) caretCol = lines.get(caretRow).length();
        if (caretCol < 0) caretCol = 0;
    }

    private int pageSize() {
        int innerH = Math.max(LINE_HEIGHT, height - style.getPadding().getVertical());
        return Math.max(1, innerH / LINE_HEIGHT);
    }

    private int visibleRows() {
        int innerH = Math.max(0, height - style.getPadding().getVertical());
        return Math.max(1, innerH / LINE_HEIGHT);
    }

    private void ensureCaretVisible() {
        int visible = visibleRows();
        if (caretRow < scrollRows) scrollRows = caretRow;
        else if (caretRow >= scrollRows + visible) scrollRows = caretRow - visible + 1;
        clampScroll();
        resetCaretBlink();
    }

    private void clampScroll() {
        int visible = visibleRows();
        int maxScroll = Math.max(0, lines.size() - visible);
        if (scrollRows > maxScroll) scrollRows = maxScroll;
        if (scrollRows < 0) scrollRows = 0;
    }

    private boolean caretVisible() {
        long since = System.currentTimeMillis() - lastCaretActivityMs;
        return ((since / CARET_BLINK_INTERVAL_MS) & 1L) == 0L;
    }

    private void resetCaretBlink() {
        lastCaretActivityMs = System.currentTimeMillis();
    }

    private static int resolveSize(int constraint, int fallback, int max) {
        if (Size.isWrapContent(constraint)) return Math.min(fallback, max);
        if (Size.isMatchParent(constraint)) return max;
        if (constraint <= 0) return Math.min(fallback, max);
        return Math.min(constraint, max);
    }
}
