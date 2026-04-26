package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.triggersstudio.moddinglib.client.ui.context.UIContext;
import org.triggersstudio.moddinglib.client.ui.state.State;
import org.triggersstudio.moddinglib.client.ui.styling.Size;
import org.triggersstudio.moddinglib.client.ui.styling.Style;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Month-view calendar bound to a {@link State<LocalDate>}. Header navigates
 * the displayed month with prev/next arrows; the day grid is 7×6 cells (a
 * full max-month layout). Days from adjacent months are dimmed and clickable
 * — picking one pans to that month.
 *
 * <p>The component holds an internal {@code State<YearMonth>} for the
 * displayed page. It auto-pans to follow the external selection when that
 * changes from outside, and writes back the {@link LocalDate} on click.
 *
 * <p>Three style modifiers: {@code headerStyle} for the top bar (background
 * + text color), {@code dayStyle} for regular days, {@code selectedDayStyle}
 * for the highlighted day. Outside-month cells derive a dim text color from
 * {@code dayStyle}.
 */
public class CalendarComponent extends UIComponent {

    private static final int CELL_W = 32;
    private static final int CELL_H = 24;
    private static final int HEADER_H = 28;
    private static final int WEEKDAY_H = 14;
    private static final int GRID_ROWS = 6;
    private static final int GRID_COLS = 7;
    private static final int ARROW_W = 24;
    private static final int TEXT_HEIGHT = 8;

    private static final int DEFAULT_WIDTH = CELL_W * GRID_COLS;
    private static final int DEFAULT_HEIGHT = HEADER_H + WEEKDAY_H + CELL_H * GRID_ROWS;

    private static final Style DEFAULT_HEADER_STYLE = Style.backgroundColor(0xFF_2A_2A_2A)
            .textColor(0xFF_FF_FF_FF)
            .build();
    private static final Style DEFAULT_DAY_STYLE = Style.backgroundColor(0xFF_22_22_22)
            .textColor(0xFF_DD_DD_DD)
            .build();
    private static final Style DEFAULT_SELECTED_DAY_STYLE = Style.backgroundColor(0xFF_55_88_FF)
            .textColor(0xFF_FF_FF_FF)
            .build();

    private final State<LocalDate> selected;
    private final State<YearMonth> displayed;
    private final Style headerStyle;
    private final Style dayStyle;
    private final Style selectedDayStyle;

    public CalendarComponent(State<LocalDate> selected,
                             Style headerStyle, Style dayStyle, Style selectedDayStyle) {
        super(Style.DEFAULT);
        if (selected == null) throw new IllegalArgumentException("selected state must not be null");
        LocalDate initial = selected.get() != null ? selected.get() : LocalDate.now();
        if (selected.get() == null) selected.set(initial);
        this.selected = selected;
        this.displayed = State.of(YearMonth.from(initial));
        this.headerStyle = headerStyle != null ? headerStyle : DEFAULT_HEADER_STYLE;
        this.dayStyle = dayStyle != null ? dayStyle : DEFAULT_DAY_STYLE;
        this.selectedDayStyle = selectedDayStyle != null ? selectedDayStyle : DEFAULT_SELECTED_DAY_STYLE;

        // Keep displayed month in sync when an external mutation moves the
        // selection to a different month. The equals-check on State.set
        // prevents loops.
        track(selected.onChange(d -> {
            if (d == null) return;
            YearMonth ym = YearMonth.from(d);
            if (!ym.equals(displayed.get())) {
                displayed.set(ym);
            }
        }));
    }

    @Override
    public void onAttach(UIContext ctx) {
        if (isAttached()) return;
        super.onAttach(ctx);
    }

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

    @Override
    public void render(DrawContext drawContext) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int cellW = width / GRID_COLS;
        int gridStartY = y + HEADER_H + WEEKDAY_H;

        // Background of the whole calendar (so gaps between cells aren't bleeding the screen)
        drawContext.fill(x, y, x + width, y + height, 0xFF_18_18_18);

        // ---- Header ----
        drawContext.fill(x, y, x + width, y + HEADER_H, headerStyle.getBackgroundColor());
        int headerTextY = y + (HEADER_H - TEXT_HEIGHT) / 2;
        // Prev arrow
        drawContext.drawText(tr, "‹", x + 8, headerTextY, headerStyle.getTextColor(), false);
        // Next arrow
        int nextLabelW = tr.getWidth("›");
        drawContext.drawText(tr, "›", x + width - 8 - nextLabelW, headerTextY, headerStyle.getTextColor(), false);
        // Month label, centered
        YearMonth month = displayed.get();
        String monthLabel = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " " + month.getYear();
        int labelW = tr.getWidth(monthLabel);
        drawContext.drawText(tr, monthLabel, x + (width - labelW) / 2, headerTextY,
                headerStyle.getTextColor(), false);

        // ---- Weekday labels ----
        int weekdayY = y + HEADER_H + (WEEKDAY_H - TEXT_HEIGHT) / 2;
        int dimText = dimAlpha(dayStyle.getTextColor());
        for (int c = 0; c < GRID_COLS; c++) {
            String label = dayOfWeekShortName(c);
            int colCenterX = x + c * cellW + cellW / 2;
            int textX = colCenterX - tr.getWidth(label) / 2;
            drawContext.drawText(tr, label, textX, weekdayY, dimText, false);
        }

        // ---- Day grid ----
        LocalDate sel = selected.get();
        LocalDate today = LocalDate.now();
        for (int r = 0; r < GRID_ROWS; r++) {
            for (int c = 0; c < GRID_COLS; c++) {
                LocalDate day = dayAt(r, c);
                int cellX = x + c * cellW;
                int cellY = gridStartY + r * CELL_H;
                boolean inMonth = YearMonth.from(day).equals(month);
                boolean isSelected = day.equals(sel);
                boolean isToday = day.equals(today);

                int bg = isSelected ? selectedDayStyle.getBackgroundColor() : dayStyle.getBackgroundColor();
                int fg = isSelected ? selectedDayStyle.getTextColor() : dayStyle.getTextColor();
                if (!inMonth && !isSelected) {
                    fg = dimAlpha(fg);
                    bg = (bg == 0) ? 0 : dimAlpha(bg);
                }

                // Cell background with a 1px gap between cells
                drawContext.fill(cellX + 1, cellY + 1, cellX + cellW - 1, cellY + CELL_H - 1, bg);

                // Today: subtle accent border
                if (isToday && !isSelected) {
                    int accent = selectedDayStyle.getBackgroundColor();
                    int x1 = cellX + 1, y1 = cellY + 1, x2 = cellX + cellW - 1, y2 = cellY + CELL_H - 1;
                    drawContext.fill(x1, y1, x2, y1 + 1, accent);
                    drawContext.fill(x1, y2 - 1, x2, y2, accent);
                    drawContext.fill(x1, y1, x1 + 1, y2, accent);
                    drawContext.fill(x2 - 1, y1, x2, y2, accent);
                }

                // Day number, centered
                String label = String.valueOf(day.getDayOfMonth());
                int labelTextX = cellX + (cellW - tr.getWidth(label)) / 2;
                int labelTextY = cellY + (CELL_H - TEXT_HEIGHT) / 2;
                drawContext.drawText(tr, label, labelTextX, labelTextY, fg, false);
            }
        }
    }

    @Override
    public boolean onMouseClick(double mx, double my, int button) {
        if (button != 0) return false;
        if (!isPointInside(mx, my)) return false;
        int relX = (int) (mx - x);
        int relY = (int) (my - y);

        // Header zone
        if (relY < HEADER_H) {
            if (relX < ARROW_W) {
                displayed.set(displayed.get().minusMonths(1));
                return true;
            }
            if (relX >= width - ARROW_W) {
                displayed.set(displayed.get().plusMonths(1));
                return true;
            }
            return true; // consume clicks elsewhere on the header
        }

        // Weekday row: non-interactive
        if (relY < HEADER_H + WEEKDAY_H) return true;

        int row = (relY - HEADER_H - WEEKDAY_H) / CELL_H;
        int col = relX / (width / GRID_COLS);
        if (row < 0 || row >= GRID_ROWS || col < 0 || col >= GRID_COLS) return false;

        LocalDate day = dayAt(row, col);
        selected.set(day);
        // If clicked a day from an adjacent month, pan to it.
        YearMonth ym = YearMonth.from(day);
        if (!ym.equals(displayed.get())) {
            displayed.set(ym);
        }
        return true;
    }

    // ===== Helpers =====

    private LocalDate dayAt(int row, int col) {
        YearMonth month = displayed.get();
        LocalDate firstDay = month.atDay(1);
        // Monday=0 ... Sunday=6 (ISO-aligned)
        int firstDayOfWeek = firstDay.getDayOfWeek().getValue() - 1;
        int offset = row * GRID_COLS + col - firstDayOfWeek;
        return firstDay.plusDays(offset);
    }

    private static String dayOfWeekShortName(int col) {
        // col 0 = Monday, col 6 = Sunday
        return java.time.DayOfWeek.of(col + 1)
                .getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                .substring(0, 2);
    }

    private static int dimAlpha(int color) {
        return (color & 0x00_FF_FF_FF) | 0x60_00_00_00;
    }

    private static int resolveSize(int constraint, int fallback, int max) {
        if (Size.isWrapContent(constraint)) return Math.min(fallback, max);
        if (Size.isMatchParent(constraint)) return max;
        if (constraint <= 0) return Math.min(fallback, max);
        return Math.min(constraint, max);
    }
}
