package org.triggersstudio.moddinglib.client.ui.toast;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.triggersstudio.moddinglib.client.ui.animation.Easing;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Global queue of active toast notifications. Rendered at the top-right of
 * any {@link org.triggersstudio.moddinglib.client.ui.screen.UIScreen} on every
 * frame; finished toasts are pruned automatically.
 *
 * <p>Thread-safe enqueue: calls from non-render threads are forwarded onto the
 * Minecraft execute queue. Rendering and pruning happen on the render thread.
 *
 * <p>Stack grows downward. When a toast is removed, the ones below jump up
 * by their height + gap (no smooth shift animation — kept intentionally simple).
 */
public final class ToastManager {

    private static final int TOAST_WIDTH = 260;
    private static final int TOAST_HEIGHT = 44;
    private static final int RIGHT_MARGIN = 12;
    private static final int TOP_MARGIN = 12;
    private static final int GAP = 6;
    private static final int ACCENT_BAR_WIDTH = 4;
    private static final int BG_COLOR = 0xF0_15_15_1C;
    private static final int BORDER_COLOR = 0xFF_2A_2A_36;
    private static final int LABEL_COLOR = 0xFF_BB_BB_BB;
    private static final int MESSAGE_COLOR = 0xFF_F0_F0_F0;

    private static final List<Toast> active = new ArrayList<>();

    private ToastManager() {}

    public static void show(String message, ToastType type) {
        show(message, type, Toast.DEFAULT_DURATION_MS);
    }

    public static void show(String message, ToastType type, long durationMs) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && !client.isOnThread()) {
            client.execute(() -> active.add(new Toast(message, type, durationMs)));
            return;
        }
        active.add(new Toast(message, type, durationMs));
    }

    /**
     * Drop every currently-active toast. Useful when switching screens if you
     * want a clean slate.
     */
    public static void clear() {
        active.clear();
    }

    public static int activeCount() {
        return active.size();
    }

    public static void render(DrawContext drawContext, int screenWidth, int screenHeight) {
        if (active.isEmpty()) return;

        // Prune finished toasts first.
        Iterator<Toast> it = active.iterator();
        while (it.hasNext()) {
            if (it.next().isFinished()) it.remove();
        }
        if (active.isEmpty()) return;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int targetX = screenWidth - TOAST_WIDTH - RIGHT_MARGIN;
        int currentY = TOP_MARGIN;

        for (Toast toast : active) {
            double slideEased = Easing.OUT_CUBIC.applyAsDouble(toast.slideInProgress());
            double exit = toast.exitProgress();
            // Off-screen offset starts at TOAST_WIDTH + RIGHT_MARGIN, lerps to 0.
            int slideOffset = (int) ((TOAST_WIDTH + RIGHT_MARGIN) * (1.0 - slideEased));
            // During exit, slide back out and fade.
            int exitOffset = (int) ((TOAST_WIDTH + RIGHT_MARGIN) * (1.0 - exit));
            int x = targetX + slideOffset + exitOffset;
            int y = currentY;

            float alpha = (float) (slideEased * exit);
            if (alpha > 0f) {
                drawToast(drawContext, tr, toast, x, y, alpha);
            }
            currentY += TOAST_HEIGHT + GAP;
        }
    }

    private static void drawToast(DrawContext drawContext, TextRenderer tr, Toast toast,
                                  int x, int y, float alpha) {
        int bg = withAlpha(BG_COLOR, alpha);
        int border = withAlpha(BORDER_COLOR, alpha);
        int accent = withAlpha(toast.type.accentColor, alpha);
        int label = withAlpha(LABEL_COLOR, alpha);
        int msg = withAlpha(MESSAGE_COLOR, alpha);

        // Background.
        drawContext.fill(x, y, x + TOAST_WIDTH, y + TOAST_HEIGHT, bg);
        // 1px border.
        drawContext.fill(x, y, x + TOAST_WIDTH, y + 1, border);
        drawContext.fill(x, y + TOAST_HEIGHT - 1, x + TOAST_WIDTH, y + TOAST_HEIGHT, border);
        drawContext.fill(x, y, x + 1, y + TOAST_HEIGHT, border);
        drawContext.fill(x + TOAST_WIDTH - 1, y, x + TOAST_WIDTH, y + TOAST_HEIGHT, border);
        // Accent bar (left).
        drawContext.fill(x, y, x + ACCENT_BAR_WIDTH, y + TOAST_HEIGHT, accent);

        // We rely on per-color alpha to fade text — Minecraft draws text with the
        // color's alpha channel honored when shadow is true.
        int textX = x + ACCENT_BAR_WIDTH + 8;
        int textY = y + 8;
        drawContext.drawText(tr, toast.type.label, textX, textY, label, true);

        // Truncate message if too wide.
        String message = truncate(tr, toast.message, TOAST_WIDTH - ACCENT_BAR_WIDTH - 16);
        drawContext.drawText(tr, message, textX, textY + 14, msg, true);

        // Defensive shader-color reset (some text paths leave it tinted).
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static int withAlpha(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int newA = Math.round(a * Math.max(0f, Math.min(1f, factor)));
        return (argb & 0x00_FF_FF_FF) | (newA << 24);
    }

    private static String truncate(TextRenderer tr, String s, int maxWidth) {
        if (tr.getWidth(s) <= maxWidth) return s;
        String ellipsis = "...";
        int ellW = tr.getWidth(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            sb.append(s.charAt(i));
            if (tr.getWidth(sb.toString()) + ellW > maxWidth) {
                if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
                sb.append(ellipsis);
                return sb.toString();
            }
        }
        return s;
    }
}
