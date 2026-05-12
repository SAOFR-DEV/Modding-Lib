package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.triggersstudio.moddinglib.client.ui.context.UIContext;
import org.triggersstudio.moddinglib.client.ui.styling.ObjectFit;
import org.triggersstudio.moddinglib.client.ui.styling.Size;
import org.triggersstudio.moddinglib.client.ui.styling.Style;
import org.triggersstudio.moddinglib.client.ui.video.VideoPlayer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * UI wrapper around a {@link VideoPlayer}. Each render frame the component
 * polls the player for a new RGBA buffer; if the version changed since the
 * last upload it copies into a {@link NativeImageBackedTexture} owned by
 * this component and uploads it to the GPU. The texture is then drawn at
 * the component's bounds via {@link DrawContext#drawTexture}.
 *
 * <p>Ownership: by default the component owns the player and disposes it on
 * detach. Pass {@code ownsPlayer=false} to share a single player across
 * multiple components or keep it alive across screen transitions.
 *
 * <p>Upload path: native memcpy from the FFmpeg scale buffer directly
 * into the {@code NativeImage.pointer} memory (exposed via the access
 * widener). One JNI call per frame instead of width × height
 * setColorArgb calls — keeps 1080p smooth on the render thread. If the
 * pointer field can't be accessed for some reason, falls back to a
 * per-pixel pack on a JVM-side {@code int[]} buffer.
 */
public class VideoComponent extends UIComponent {

    private static final AtomicLong INSTANCE_COUNTER = new AtomicLong(0);

    private final VideoPlayer player;
    private final boolean ownsPlayer;
    private final long instanceId = INSTANCE_COUNTER.getAndIncrement();

    private NativeImageBackedTexture texture;
    private Identifier textureId;
    /** Per-pixel ARGB scratch buffer for the slow fallback path. Lazily
     *  allocated the first time {@code NativeImage.pointer} isn't exposed,
     *  so the steady-state native-memcpy path doesn't hold ~8MB of heap
     *  per 1080p instance for nothing. */
    private int[] frameArgb;
    private long uploadedFrameVersion = 0L;

    public VideoComponent(VideoPlayer player, Style style, boolean ownsPlayer) {
        super(style);
        if (player == null) throw new IllegalArgumentException("player must not be null");
        this.player = player;
        this.ownsPlayer = ownsPlayer;
    }

    public VideoPlayer getPlayer() {
        return player;
    }

    @Override
    public void onAttach(UIContext ctx) {
        super.onAttach(ctx);
        // Auto-start playback when attached to a screen.
        player.play();
    }

    @Override
    public void onDetach() {
        if (textureId != null) {
            // destroyTexture removes the registration from TextureManager AND
            // closes the underlying texture/NativeImage. Calling texture.close()
            // afterwards would be a double-free; calling close() *without*
            // destroyTexture leaves a dangling map entry per screen reopen,
            // which the auditor flagged as a VRAM leak.
            MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);
            textureId = null;
            texture = null;
        }
        if (ownsPlayer) {
            player.close();
        }
        super.onDetach();
    }

    @Override
    public MeasureResult measure(int maxWidth, int maxHeight) {
        int w = resolveSize(style.getWidth(), player.getWidth(), maxWidth);
        int h = resolveSize(style.getHeight(), player.getHeight(), maxHeight);
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
    public void render(DrawContext ctx) {
        // Drive the audio pump once per render frame so AL buffer recycling
        // and the queue refill happen on the render thread (where Minecraft's
        // OpenAL context is current). No-op for sources without audio.
        player.pumpRenderTick();

        long latestVersion = player.frameVersion();
        if (latestVersion > 0 && latestVersion != uploadedFrameVersion) {
            ensureTexture();
            if (texture != null) {
                NativeImage img = texture.getImage();
                long actualVersion = uploadedFrameVersion;
                // Fast path: native memcpy directly into the NativeImage's
                // GPU-bound buffer. Skips the per-pixel setColorArgb JNI.
                if (img != null && img.pointer != 0L) {
                    actualVersion = player.copyLatestFrameToNative(img.pointer, uploadedFrameVersion);
                } else if (img != null) {
                    // Fallback: per-pixel pack via setColorArgb. Slower, used
                    // only if the access widener didn't expose the pointer.
                    if (frameArgb == null) {
                        frameArgb = new int[player.getWidth() * player.getHeight()];
                    }
                    actualVersion = player.readLatestFrameArgb(frameArgb, uploadedFrameVersion);
                    if (actualVersion != uploadedFrameVersion) {
                        copyFrameToImage(img);
                    }
                }
                if (actualVersion != uploadedFrameVersion) {
                    texture.upload();
                    uploadedFrameVersion = actualVersion;
                }
            }
        }

        if (textureId != null) {
            int texW = player.getWidth();
            int texH = player.getHeight();
            ObjectFit fit = style.getObjectFit();
            if (fit == null) fit = ObjectFit.CONTAIN;
            drawFitted(ctx, fit, texW, texH);
        } else if (style.getBackgroundColor() != 0) {
            // Show background while waiting for the first decoded frame.
            ctx.fill(x, y, x + width, y + height, style.getBackgroundColor());
        }
    }

    /**
     * Pick the source-rect (u, v, regionW, regionH) and destination-rect
     * (drawX, drawY, drawW, drawH) corresponding to the requested fit, then
     * issue one 12-arg {@code drawTexture} call.
     *
     * <p>Letterbox/pillarbox bars (CONTAIN, NONE) are painted with the
     * style's background color so the player background carries through to
     * the empty margin. If no background is set, the margin shows the
     * underlying screen — usually fine for a single video, less great when
     * the video sits in a card.
     */
    private void drawFitted(DrawContext ctx, ObjectFit fit, int texW, int texH) {
        float u = 0f, v = 0f;
        int regionW = texW, regionH = texH;
        int drawX = x, drawY = y, drawW = width, drawH = height;

        switch (fit) {
            case STRETCH:
                // Fall through to the defaults above: full texture, full rect.
                break;
            case CONTAIN: {
                double scale = Math.min((double) width / texW, (double) height / texH);
                int fitW = Math.max(1, (int) Math.round(texW * scale));
                int fitH = Math.max(1, (int) Math.round(texH * scale));
                drawX = x + (width - fitW) / 2;
                drawY = y + (height - fitH) / 2;
                drawW = fitW;
                drawH = fitH;
                paintLetterboxBars(ctx, drawX, drawY, drawW, drawH);
                break;
            }
            case COVER: {
                // Pick the source crop so that scaling it to the bounds
                // preserves aspect. The crop is centered on the texture.
                double scale = Math.max((double) width / texW, (double) height / texH);
                int srcW = Math.max(1, (int) Math.round(width / scale));
                int srcH = Math.max(1, (int) Math.round(height / scale));
                u = (texW - srcW) / 2f;
                v = (texH - srcH) / 2f;
                regionW = srcW;
                regionH = srcH;
                // drawX/Y/W/H unchanged — fill bounds.
                break;
            }
            case NONE: {
                // Draw at native pixel size, centered. If the texture is
                // smaller than bounds we get margins (paint as letterbox);
                // if larger, crop centered via u/v.
                if (texW <= width && texH <= height) {
                    drawX = x + (width - texW) / 2;
                    drawY = y + (height - texH) / 2;
                    drawW = texW;
                    drawH = texH;
                    paintLetterboxBars(ctx, drawX, drawY, drawW, drawH);
                } else {
                    int srcW = Math.min(texW, width);
                    int srcH = Math.min(texH, height);
                    u = (texW - srcW) / 2f;
                    v = (texH - srcH) / 2f;
                    regionW = srcW;
                    regionH = srcH;
                    drawX = x + (width - srcW) / 2;
                    drawY = y + (height - srcH) / 2;
                    drawW = srcW;
                    drawH = srcH;
                }
                break;
            }
            default:
                break;
        }

        ctx.drawTexture(RenderLayer::getGuiTextured, textureId,
                drawX, drawY,
                u, v,
                drawW, drawH,
                regionW, regionH,
                texW, texH);
    }

    private void paintLetterboxBars(DrawContext ctx, int drawX, int drawY, int drawW, int drawH) {
        int bg = style.getBackgroundColor();
        if (bg == 0) return;
        int right = x + width;
        int bottom = y + height;
        // Top bar
        if (drawY > y) ctx.fill(x, y, right, drawY, bg);
        // Bottom bar
        if (drawY + drawH < bottom) ctx.fill(x, drawY + drawH, right, bottom, bg);
        // Left bar (only between the top and bottom bars to avoid overdraw)
        if (drawX > x) ctx.fill(x, drawY, drawX, drawY + drawH, bg);
        // Right bar
        if (drawX + drawW < right) ctx.fill(drawX + drawW, drawY, right, drawY + drawH, bg);
    }

    private void copyFrameToImage(NativeImage img) {
        // frameArgb is already ARGB-packed thanks to sws_scale → BGRA + LE
        // int read on the player side. Just push each pixel into the image.
        int w = player.getWidth();
        int h = player.getHeight();
        for (int yy = 0; yy < h; yy++) {
            int row = yy * w;
            for (int xx = 0; xx < w; xx++) {
                img.setColorArgb(xx, yy, frameArgb[row + xx]);
            }
        }
    }

    private void ensureTexture() {
        if (texture != null) return;
        int w = player.getWidth();
        int h = player.getHeight();
        NativeImage img = new NativeImage(w, h, false);
        texture = new NativeImageBackedTexture(img);
        // 1.21.4 dropped registerDynamicTexture — caller now supplies the id.
        textureId = Identifier.of("moddinglib", "video/" + instanceId);
        MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
    }

    private static int resolveSize(int constraint, int fallback, int max) {
        if (Size.isWrapContent(constraint)) return Math.min(fallback, max);
        if (Size.isMatchParent(constraint)) return max;
        if (constraint <= 0) return Math.min(fallback, max);
        return Math.min(constraint, max);
    }
}
