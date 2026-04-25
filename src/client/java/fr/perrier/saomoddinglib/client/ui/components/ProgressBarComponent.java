package fr.perrier.saomoddinglib.client.ui.components;

import fr.perrier.saomoddinglib.client.ui.styling.Size;
import fr.perrier.saomoddinglib.client.ui.styling.Style;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/**
 * Read-only progress indicator. Reads the current value through a
 * {@link DoubleSupplier} on every frame and paints two layers:
 * <ul>
 *   <li>The track ({@code barStyle.backgroundColor}, full width).</li>
 *   <li>The fill ({@code fillStyle.backgroundColor}, width = {@code ratio × bar width}).</li>
 * </ul>
 *
 * <p>Optionally draws a centered label whose content is computed by a
 * {@link DoubleFunction} from the current value. Pass {@code null} as the
 * format to disable the label entirely. The default format renders
 * {@code "Progression XX%"} where XX is the rounded percent of {@code (value - min) / (max - min)}.
 *
 * <p>Construct via the {@code Components.ProgressBar(...)} factories.
 */
public class ProgressBarComponent extends UIComponent {

    private static final Style DEFAULT_BAR_STYLE = Style.backgroundColor(0xFF_33_33_33)
            .textColor(0xFF_FF_FF_FF)
            .width(200)
            .height(16)
            .build();
    private static final Style DEFAULT_FILL_STYLE = Style.backgroundColor(0xFF_55_88_FF).build();

    private static final int DEFAULT_BAR_WIDTH = 200;
    private static final int DEFAULT_BAR_HEIGHT = 16;
    private static final int TEXT_HEIGHT = 8;

    private final Style fillStyle;
    private final double min;
    private final double max;
    private final DoubleSupplier reader;
    private final DoubleFunction<String> labelFormat; // null ⇒ no label

    public ProgressBarComponent(Style barStyle, Style fillStyle,
                                double min, double max,
                                DoubleSupplier reader,
                                DoubleFunction<String> labelFormat) {
        super(barStyle != null ? barStyle : DEFAULT_BAR_STYLE);
        this.fillStyle = fillStyle != null ? fillStyle : DEFAULT_FILL_STYLE;
        if (max <= min) {
            throw new IllegalArgumentException("ProgressBar max (" + max + ") must be > min (" + min + ")");
        }
        this.min = min;
        this.max = max;
        this.reader = reader;
        this.labelFormat = labelFormat;
    }

    @Override
    public MeasureResult measure(int maxWidth, int maxHeight) {
        int w = resolveSize(style.getWidth(), DEFAULT_BAR_WIDTH, maxWidth);
        int h = resolveSize(style.getHeight(), DEFAULT_BAR_HEIGHT, maxHeight);
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
        double value = reader.getAsDouble();
        double ratio = (value - min) / (max - min);
        if (ratio < 0) ratio = 0;
        if (ratio > 1) ratio = 1;

        // Track
        int barColor = style.getBackgroundColor();
        if (barColor != 0) {
            drawContext.fill(x, y, x + width, y + height, barColor);
        }

        // Fill
        int fillColor = fillStyle.getBackgroundColor();
        int fillEnd = x + (int) Math.round(ratio * width);
        if (fillColor != 0 && fillEnd > x) {
            drawContext.fill(x, y, fillEnd, y + height, fillColor);
        }

        // Border (on top of fill so it stays visible)
        if (style.getBorderWidth() > 0) {
            drawBorder(drawContext);
        }

        // Label
        if (labelFormat != null) {
            String label = labelFormat.apply(value);
            if (label != null && !label.isEmpty()) {
                TextRenderer tr = MinecraftClient.getInstance().textRenderer;
                int labelWidth = tr.getWidth(label);
                int labelX = x + (width - labelWidth) / 2;
                int labelY = y + (height - TEXT_HEIGHT) / 2;
                drawContext.drawText(tr, label, labelX, labelY, style.getTextColor(), false);
            }
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
     * Build the default label formatter ({@code "Progression XX%"}) for a
     * given range. Used by the no-label-format factory overloads.
     */
    public static DoubleFunction<String> defaultLabelFormat(double min, double max) {
        double span = max - min;
        return v -> {
            double ratio = (v - min) / span;
            if (ratio < 0) ratio = 0;
            if (ratio > 1) ratio = 1;
            int percent = (int) Math.round(ratio * 100);
            return "Progression " + percent + "%";
        };
    }

    private static int resolveSize(int constraint, int fallback, int max) {
        if (Size.isWrapContent(constraint)) return Math.min(fallback, max);
        if (Size.isMatchParent(constraint)) return max;
        if (constraint <= 0) return Math.min(fallback, max);
        return Math.min(constraint, max);
    }
}
