package org.triggersstudio.moddinglib.client.ui.components;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.triggersstudio.moddinglib.client.ui.context.UIContext;
import org.triggersstudio.moddinglib.client.ui.styling.Size;
import org.triggersstudio.moddinglib.client.ui.styling.Style;
import org.triggersstudio.moddinglib.client.ui.video.VideoPlayer;

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
 *   <li>Per-pixel ABGR pack on render thread — slow at 1080p+. Phase 3
 *       will wire sws_scale directly into the NativeImage buffer.</li>
 *   <li>No volume / audio output (player decodes video stream only).</li>
 *   <li>If the source ends and {@code isEnded()}, the last frame is held.</li>
 * </ul>
 */
public class VideoComponent extends UIComponent {

    private final VideoPlayer player;
    private final boolean ownsPlayer;

    private NativeImageBackedTexture texture;
    private Identifier textureId;
    private byte[] frameBuffer;
    private long uploadedFrameVersion = 0L;

    public VideoComponent(VideoPlayer player, Style style, boolean ownsPlayer) {
        super(style);
        if (player == null) throw new IllegalArgumentException("player must not be null");
        this.player = player;
        this.ownsPlayer = ownsPlayer;
        this.frameBuffer = new byte[player.getWidth() * player.getHeight() * 4];
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
        long latestVersion = player.frameVersion();
        if (latestVersion > 0 && latestVersion != uploadedFrameVersion) {
            ensureTexture();
            long actualVersion = player.readLatestFrame(frameBuffer, uploadedFrameVersion);
            if (actualVersion != uploadedFrameVersion && texture != null && texture.getImage() != null) {
                copyFrameToImage(texture.getImage());
                texture.upload();
                uploadedFrameVersion = actualVersion;
            }
        }

        if (textureId != null) {
            // Stretch to component bounds. The native source dims are passed
            // as texW/texH so DrawContext computes correct UVs.
            ctx.drawTexture(RenderLayer::getGuiTextured, textureId,
                    x, y, 0f, 0f, width, height,
                    player.getWidth(), player.getHeight());
        } else if (style.getBackgroundColor() != 0) {
            // Show background while waiting for the first decoded frame.
            ctx.fill(x, y, x + width, y + height, style.getBackgroundColor());
        }
    }

    private void copyFrameToImage(NativeImage img) {
        int w = player.getWidth();
        int h = player.getHeight();
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                int i = (yy * w + xx) * 4;
                int r = frameBuffer[i] & 0xff;
                int g = frameBuffer[i + 1] & 0xff;
                int b = frameBuffer[i + 2] & 0xff;
                int a = frameBuffer[i + 3] & 0xff;
                // NativeImage in MC 1.21.x packs as ABGR8888 (alpha-MSB,
                // then blue-green-red). FFmpeg AV_PIX_FMT_RGBA delivers
                // R-G-B-A bytes in memory.
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                img.setColor(xx, yy, abgr);
            }
        }
    }

    private void ensureTexture() {
        if (texture != null) return;
        int w = player.getWidth();
        int h = player.getHeight();
        NativeImage img = new NativeImage(w, h, false);
        texture = new NativeImageBackedTexture(img);
        textureId = MinecraftClient.getInstance().getTextureManager()
                .registerDynamicTexture("moddinglib_video", texture);
    }

    private static int resolveSize(int constraint, int fallback, int max) {
        if (Size.isWrapContent(constraint)) return Math.min(fallback, max);
        if (Size.isMatchParent(constraint)) return max;
        if (constraint <= 0) return Math.min(fallback, max);
        return Math.min(constraint, max);
    }
}
