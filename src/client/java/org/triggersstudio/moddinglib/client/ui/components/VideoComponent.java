package org.triggersstudio.moddinglib.client.ui.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.triggersstudio.moddinglib.client.ui.context.UIContext;
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
 * <p>Phase-1 limitations:
 * <ul>
 *   <li>Per-pixel ARGB pack on render thread — slow at 1080p+. Phase 3
 *       will wire sws_scale directly into the NativeImage buffer.</li>
 *   <li>No volume / audio output (player decodes video stream only).</li>
 *   <li>If the source ends and {@code isEnded()}, the last frame is held.</li>
 * </ul>
 */
public class VideoComponent extends UIComponent {

    private static final AtomicLong INSTANCE_COUNTER = new AtomicLong(0);

    private final VideoPlayer player;
    private final boolean ownsPlayer;
    private final long instanceId = INSTANCE_COUNTER.getAndIncrement();

    private NativeImageBackedTexture texture;
    private Identifier textureId;
    private final int[] frameArgb;
    private long uploadedFrameVersion = 0L;

    public VideoComponent(VideoPlayer player, Style style, boolean ownsPlayer) {
        super(style);
        if (player == null) throw new IllegalArgumentException("player must not be null");
        this.player = player;
        this.ownsPlayer = ownsPlayer;
        this.frameArgb = new int[player.getWidth() * player.getHeight()];
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
        if (texture != null) {
            texture.close();
            texture = null;
            textureId = null;
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
            long actualVersion = player.readLatestFrameArgb(frameArgb, uploadedFrameVersion);
            if (actualVersion != uploadedFrameVersion && texture != null && texture.getImage() != null) {
                copyFrameToImage(texture.getImage());
                texture.upload();
                uploadedFrameVersion = actualVersion;
            }
        }

        if (textureId != null) {
            // 12-arg drawTexture so the whole video is sampled (regionW/H =
            // full texture dims) and stretched into the component bounds
            // (width/height = on-screen rect). The 10-arg overload assumes
            // regionW=width / regionH=height (1:1 pixels) and would crop.
            int texW = player.getWidth();
            int texH = player.getHeight();
            ctx.drawTexture(RenderLayer::getGuiTextured, textureId,
                    x, y,
                    0f, 0f,
                    width, height,
                    texW, texH,
                    texW, texH);
        } else if (style.getBackgroundColor() != 0) {
            // Show background while waiting for the first decoded frame.
            ctx.fill(x, y, x + width, y + height, style.getBackgroundColor());
        }
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
