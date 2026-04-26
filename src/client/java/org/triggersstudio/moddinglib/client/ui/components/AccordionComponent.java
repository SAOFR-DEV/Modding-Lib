package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.triggersstudio.moddinglib.client.ui.context.UIContext;
import org.triggersstudio.moddinglib.client.ui.state.State;
import org.triggersstudio.moddinglib.client.ui.styling.Size;
import org.triggersstudio.moddinglib.client.ui.styling.Style;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Vertical stack of collapsible sections. Each section has a header (always
 * visible) and a body (visible only when open). Click on a header toggles
 * the section.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Multi-open</b> (default): independent toggles, any number of sections may be open.</li>
 *   <li><b>Single-open</b>: opening one section closes the others.</li>
 * </ul>
 *
 * <p>The component owns the open state through an internal
 * {@code State<Set<Integer>>}. Factories that take an external state bridge
 * it through this internal state. Bodies are attached for the entire
 * lifetime of the accordion so their internal state (focus, subscriptions)
 * persists through close/reopen cycles.
 */
public class AccordionComponent extends UIComponent {

    private static final int DEFAULT_WIDTH = 240;
    private static final int DEFAULT_HEADER_HEIGHT = 24;
    private static final int HEADER_PADDING_X = 8;
    private static final int TEXT_HEIGHT = 8;

    private static final Style DEFAULT_HEADER_STYLE = Style.backgroundColor(0xFF_2A_2A_2A)
            .textColor(0xFF_FF_FF_FF)
            .height(DEFAULT_HEADER_HEIGHT)
            .build();

    private final List<AccordionSection> sections;
    private final State<Set<Integer>> openSet;
    private final boolean singleOpen;
    private final Style headerStyle;

    private final int[] headerY;
    private final int[] headerH;
    private final int[] bodyY;
    private final int[] bodyH;

    public AccordionComponent(State<Set<Integer>> openSet, boolean singleOpen,
                              List<AccordionSection> sections, Style headerStyle) {
        super(Style.DEFAULT);
        if (openSet == null) throw new IllegalArgumentException("openSet must not be null");
        if (sections == null || sections.isEmpty()) {
            throw new IllegalArgumentException("Accordion requires at least one section");
        }
        this.openSet = openSet;
        this.singleOpen = singleOpen;
        this.sections = List.copyOf(sections);
        this.headerStyle = headerStyle != null ? headerStyle : DEFAULT_HEADER_STYLE;
        int n = this.sections.size();
        this.headerY = new int[n];
        this.headerH = new int[n];
        this.bodyY = new int[n];
        this.bodyH = new int[n];
    }

    @Override
    public void onAttach(UIContext ctx) {
        if (isAttached()) return;
        super.onAttach(ctx);
        for (AccordionSection s : sections) {
            s.body().onAttach(ctx);
        }
    }

    @Override
    public void onDetach() {
        if (!isAttached()) return;
        for (AccordionSection s : sections) {
            s.body().onDetach();
        }
        super.onDetach();
    }

    private boolean isOpen(int index) {
        Set<Integer> set = openSet.get();
        return set != null && set.contains(index);
    }

    private void toggle(int index) {
        Set<Integer> current = openSet.get();
        if (current == null) current = Set.of();
        Set<Integer> next;
        if (singleOpen) {
            next = current.contains(index) ? Set.of() : Set.of(index);
        } else {
            next = new HashSet<>(current);
            if (!next.add(index)) next.remove(index);
        }
        openSet.set(next);
    }

    // ===== Measure / Layout / Render =====

    private int resolveHeaderHeight() {
        int h = headerStyle.getHeight();
        if (Size.isFixed(h)) return h;
        return DEFAULT_HEADER_HEIGHT;
    }

    @Override
    public MeasureResult measure(int maxWidth, int maxHeight) {
        int outerW = resolveSize(style.getWidth(), DEFAULT_WIDTH, maxWidth);
        int hH = resolveHeaderHeight();
        int totalH = 0;
        for (int i = 0; i < sections.size(); i++) {
            totalH += hH;
            if (isOpen(i)) {
                MeasureResult bm = sections.get(i).body().measure(outerW, Math.max(0, maxHeight - totalH));
                totalH += bm.height;
            }
        }
        int outerH = resolveSize(style.getHeight(), totalH, maxHeight);
        return new MeasureResult(outerW, outerH);
    }

    @Override
    public void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        int hH = resolveHeaderHeight();
        int currentY = y;
        for (int i = 0; i < sections.size(); i++) {
            headerY[i] = currentY;
            headerH[i] = hH;
            currentY += hH;
            if (isOpen(i)) {
                UIComponent body = sections.get(i).body();
                MeasureResult bm = body.measure(width, Math.max(0, height - (currentY - y)));
                body.layout(x, currentY, width, bm.height);
                bodyY[i] = currentY;
                bodyH[i] = bm.height;
                currentY += bm.height;
            } else {
                bodyY[i] = -1;
                bodyH[i] = 0;
            }
        }
    }

    @Override
    public void render(DrawContext drawContext) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int headerBg = headerStyle.getBackgroundColor();
        int headerText = headerStyle.getTextColor();

        for (int i = 0; i < sections.size(); i++) {
            int hY = headerY[i];
            int hH = headerH[i];
            // Header background
            if (headerBg != 0) {
                drawContext.fill(x, hY, x + width, hY + hH, headerBg);
            }
            int textY = hY + Math.max(0, (hH - TEXT_HEIGHT) / 2);
            // Title (left)
            String title = sections.get(i).title();
            drawContext.drawText(tr, title, x + HEADER_PADDING_X, textY, headerText, false);
            // Indicator (right)
            String indicator = isOpen(i) ? "▼" : "▶";
            int indW = tr.getWidth(indicator);
            drawContext.drawText(tr, indicator, x + width - HEADER_PADDING_X - indW, textY, headerText, false);
            // Body (only if open)
            if (isOpen(i)) {
                sections.get(i).body().render(drawContext);
            }
        }
    }

    // ===== Mouse routing =====

    @Override
    public boolean onMouseClick(double mx, double my, int button) {
        if (!isPointInside(mx, my)) return false;
        if (button == 0) {
            for (int i = 0; i < sections.size(); i++) {
                if (mx >= x && mx < x + width && my >= headerY[i] && my < headerY[i] + headerH[i]) {
                    toggle(i);
                    return true;
                }
            }
        }
        for (int i = 0; i < sections.size(); i++) {
            if (isOpen(i) && sections.get(i).body().onMouseClick(mx, my, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onMouseDrag(double mx, double my, double dragX, double dragY, int button) {
        boolean consumed = false;
        for (int i = 0; i < sections.size(); i++) {
            if (isOpen(i) && sections.get(i).body().onMouseDrag(mx, my, dragX, dragY, button)) {
                consumed = true;
            }
        }
        return consumed;
    }

    @Override
    public boolean onMouseRelease(double mx, double my, int button) {
        boolean consumed = false;
        for (int i = 0; i < sections.size(); i++) {
            if (isOpen(i) && sections.get(i).body().onMouseRelease(mx, my, button)) {
                consumed = true;
            }
        }
        return consumed;
    }

    @Override
    public boolean onMouseScroll(double mx, double my, double scrollDelta) {
        for (int i = 0; i < sections.size(); i++) {
            if (isOpen(i) && sections.get(i).body().onMouseScroll(mx, my, scrollDelta)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onMouseMove(double mx, double my) {
        for (int i = 0; i < sections.size(); i++) {
            if (isOpen(i)) {
                sections.get(i).body().onMouseMove(mx, my);
            }
        }
    }

    // ===== Helpers =====

    private static int resolveSize(int constraint, int fallback, int max) {
        if (Size.isWrapContent(constraint)) return Math.min(fallback, max);
        if (Size.isMatchParent(constraint)) return max;
        if (constraint <= 0) return Math.min(fallback, max);
        return Math.min(constraint, max);
    }

    /**
     * Build the initial open set from the {@code defaultOpen} flags on the
     * provided sections. In single-open mode, only the first flagged section
     * is honored. Used by factories that create internal state.
     */
    public static Set<Integer> initialOpenSet(List<AccordionSection> sections, boolean singleOpen) {
        Set<Integer> out = new HashSet<>();
        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i).defaultOpen()) {
                out.add(i);
                if (singleOpen) break;
            }
        }
        return out;
    }
}
