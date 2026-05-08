package org.triggersstudio.moddinglib.client.ui.video;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.PointerPointer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swscale.*;

/**
 * Phase-1 video decoder (video stream only, no audio).
 *
 * <p>Opens any URL FFmpeg understands — local file path, http://, rtsp://,
 * etc. — and runs a daemon thread that decodes the video stream as fast as
 * the source's PTS dictates. The latest decoded frame is exposed in RGBA
 * via {@link #readLatestFrame(byte[], long)}, which the render thread polls
 * once per frame.
 *
 * <p><b>Threading model:</b>
 * <ul>
 *   <li>One decoder thread runs {@link #decodeLoop()}.</li>
 *   <li>Frame data lives in a single {@link BytePointer} (native memory).
 *       The decoder writes via sws_scale; the render thread reads via
 *       {@link ByteBuffer#get(byte[], int, int)} under {@code frameLock}.</li>
 *   <li>{@link #frameVersion} is incremented atomically once per published
 *       frame so consumers can skip uploads when nothing has changed.</li>
 * </ul>
 *
 * <p><b>Lifecycle:</b> {@link #close()} stops the decoder thread (interrupt
 * + join with timeout) and frees every native handle in reverse-creation
 * order. Call once and only once. Idempotent.
 *
 * <p><b>Limitations of phase 1:</b>
 * <ul>
 *   <li>No audio stream is decoded.</li>
 *   <li>No seek / loop / rate control.</li>
 *   <li>Hardware decode is not probed; everything is software.</li>
 *   <li>Per-pixel render-side ABGR pack is slow at 1080p+. Phase 3 will
 *       wire sws_scale directly into the {@code NativeImage} buffer.</li>
 * </ul>
 */
public final class VideoPlayer implements AutoCloseable {

    private final String source;
    private final int width;
    private final int height;
    private final double timeBase;

    private AVFormatContext formatCtx;
    private AVCodecContext videoCodecCtx;
    private SwsContext swsCtx;
    private final int videoStreamIdx;
    private AVFrame decodedFrame;
    private AVFrame rgbFrame;
    private AVPacket packet;
    private BytePointer rgbBuffer;
    private final ByteBuffer rgbView;

    private Thread decoderThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean paused = false;
    private final AtomicBoolean ended = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final Object frameLock = new Object();
    private final AtomicLong frameVersion = new AtomicLong(0);

    private long startNanos = -1L;
    private long pausedAtNanos = -1L;
    private long pauseAccumNanos = 0L;

    private AudioStream audio; // nullable — sources without audio still play

    /** When true, decoder rewinds to start on EOF instead of ending. */
    private volatile boolean loop = false;
    /** Seek command from any thread → consumed by decoder thread. */
    private volatile boolean seekRequested = false;
    private volatile long seekTargetMicros = 0L;

    private VideoPlayer(String source, AVFormatContext formatCtx, int videoStreamIdx,
                        AVCodecContext videoCodecCtx, SwsContext swsCtx,
                        AVFrame decodedFrame, AVFrame rgbFrame, AVPacket packet,
                        BytePointer rgbBuffer, double timeBase, AudioStream audio) {
        this.source = source;
        this.formatCtx = formatCtx;
        this.videoStreamIdx = videoStreamIdx;
        this.videoCodecCtx = videoCodecCtx;
        this.swsCtx = swsCtx;
        this.decodedFrame = decodedFrame;
        this.rgbFrame = rgbFrame;
        this.packet = packet;
        this.rgbBuffer = rgbBuffer;
        this.width = videoCodecCtx.width();
        this.height = videoCodecCtx.height();
        this.timeBase = timeBase;
        // BGRA bytes read as little-endian int = ARGB packed.
        this.rgbView = rgbBuffer.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        this.audio = audio;
    }

    private static final AtomicBoolean NETWORK_INIT_DONE = new AtomicBoolean(false);

    private static void ensureNetworkInit() {
        if (NETWORK_INIT_DONE.compareAndSet(false, true)) {
            avformat_network_init();
        }
    }

    private static String ffmpegError(int code) {
        BytePointer buf = new BytePointer(256);
        try {
            av_strerror(code, buf, 256);
            String msg = buf.getString();
            return (msg != null && !msg.isEmpty() ? msg : "(no message)") + " [code=" + code + "]";
        } finally {
            buf.close();
        }
    }

    /**
     * Open a video source. The {@code source} string is passed directly to
     * {@code avformat_open_input} — it can be a file path, {@code file://},
     * {@code http://}, {@code https://}, {@code rtsp://}, etc. depending on
     * the FFmpeg build's protocol support.
     *
     * <p>Network init is performed once on first open; protocols requiring
     * TLS need an FFmpeg build with OpenSSL/GnuTLS support (the JavaCPP
     * presets ship one).
     *
     * @throws VideoOpenException if any step of the FFmpeg open dance fails.
     */
    public static VideoPlayer open(String source) {
        ensureNetworkInit();
        AVFormatContext formatCtx = new AVFormatContext(null);

        // Bump probe + analyze so the H.264 SPS lands inside the analysis
        // window even on slow / partial streams. Without this, FFmpeg can
        // return a stream with pix_fmt=NONE and the next sws_getContext
        // hits an assertion in libswscale.
        AVDictionary openOpts = new AVDictionary(null);
        av_dict_set(openOpts, "probesize", "20000000", 0);
        av_dict_set(openOpts, "analyzeduration", "20000000", 0);

        int ret = avformat_open_input(formatCtx, source, null, openOpts);
        av_dict_free(openOpts);
        if (ret < 0) {
            throw new VideoOpenException("avformat_open_input failed for " + source + ": " + ffmpegError(ret));
        }
        // Mirror the bumped values on the context for find_stream_info too.
        formatCtx.probesize(20_000_000L);
        formatCtx.max_analyze_duration(20_000_000L);

        ret = avformat_find_stream_info(formatCtx, (PointerPointer<?>) null);
        if (ret < 0) {
            avformat_close_input(formatCtx);
            throw new VideoOpenException("avformat_find_stream_info failed for " + source + ": " + ffmpegError(ret));
        }
        int videoIdx = av_find_best_stream(formatCtx, AVMEDIA_TYPE_VIDEO, -1, -1, (AVCodec) null, 0);
        if (videoIdx < 0) {
            avformat_close_input(formatCtx);
            throw new VideoOpenException("No video stream in " + source + " (av_find_best_stream=" + videoIdx + ")");
        }
        AVStream videoStream = formatCtx.streams(videoIdx);
        AVCodecParameters params = videoStream.codecpar();
        AVCodec codec = avcodec_find_decoder(params.codec_id());
        if (codec == null) {
            avformat_close_input(formatCtx);
            throw new VideoOpenException("Unsupported codec id=" + params.codec_id());
        }
        AVCodecContext videoCodecCtx = avcodec_alloc_context3(codec);
        ret = avcodec_parameters_to_context(videoCodecCtx, params);
        if (ret < 0) {
            avcodec_free_context(videoCodecCtx);
            avformat_close_input(formatCtx);
            throw new VideoOpenException("avcodec_parameters_to_context failed: " + ffmpegError(ret));
        }
        ret = avcodec_open2(videoCodecCtx, codec, (AVDictionary) null);
        if (ret < 0) {
            avcodec_free_context(videoCodecCtx);
            avformat_close_input(formatCtx);
            throw new VideoOpenException("avcodec_open2 failed: " + ffmpegError(ret));
        }
        int w = videoCodecCtx.width();
        int h = videoCodecCtx.height();
        int srcPixFmt = videoCodecCtx.pix_fmt();
        if (srcPixFmt == AV_PIX_FMT_NONE) {
            avcodec_free_context(videoCodecCtx);
            avformat_close_input(formatCtx);
            throw new VideoOpenException("Stream pix_fmt is unspecified — try a longer / less truncated source");
        }
        // Output BGRA so the in-memory bytes (B,G,R,A) read back as a
        // little-endian 32-bit int give us exactly 0xAARRGGBB = the ARGB
        // that NativeImage.setColorArgb expects. Avoids per-pixel shuffling.
        SwsContext swsCtx = sws_getContext(
                w, h, srcPixFmt,
                w, h, AV_PIX_FMT_BGRA,
                SWS_BILINEAR, null, null, (DoublePointer) null);
        if (swsCtx == null) {
            avcodec_free_context(videoCodecCtx);
            avformat_close_input(formatCtx);
            throw new VideoOpenException("sws_getContext failed (src pix_fmt=" + srcPixFmt + ")");
        }
        AVFrame decodedFrame = av_frame_alloc();
        AVFrame rgbFrame = av_frame_alloc();
        AVPacket packet = av_packet_alloc();
        int byteCount = av_image_get_buffer_size(AV_PIX_FMT_BGRA, w, h, 1);
        BytePointer rgbBuffer = new BytePointer(av_malloc(byteCount));
        rgbBuffer.capacity(byteCount);
        av_image_fill_arrays(rgbFrame.data(), rgbFrame.linesize(),
                rgbBuffer, AV_PIX_FMT_BGRA, w, h, 1);
        double timeBase = av_q2d(videoStream.time_base());

        // Optional audio stream — failure to open audio is non-fatal; the
        // video keeps playing silently. We pick the best audio stream that
        // shares a program with the chosen video, matching FFmpeg defaults.
        AudioStream audio = null;
        int audioIdx = av_find_best_stream(formatCtx, AVMEDIA_TYPE_AUDIO, -1, videoIdx, (AVCodec) null, 0);
        if (audioIdx >= 0) {
            try {
                audio = AudioStream.open(formatCtx.streams(audioIdx), audioIdx);
            } catch (Throwable t) {
                // Swallow — better to play silent video than crash the open.
                audio = null;
            }
        }

        return new VideoPlayer(source, formatCtx, videoIdx, videoCodecCtx, swsCtx,
                decodedFrame, rgbFrame, packet, rgbBuffer, timeBase, audio);
    }

    public String getSource() { return source; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean isPaused() { return paused; }
    public boolean isEnded() { return ended.get(); }
    public boolean isRunning() { return running.get(); }
    public long frameVersion() { return frameVersion.get(); }
    public boolean hasAudio() { return audio != null; }

    /**
     * Render-thread tick. Currently used to drive the audio pump (push
     * decoded PCM into the OpenAL queue). The {@link VideoComponent} calls
     * this once per render frame.
     */
    public void pumpRenderTick() {
        if (audio != null) audio.pump();
    }

    /** Linear gain in [0, 1]. No-op on sources without audio. */
    public void setVolume(float v) {
        if (audio != null) audio.setVolume(v);
    }

    public float volume() {
        return audio != null ? audio.volume() : 0f;
    }

    public void setMuted(boolean muted) {
        if (audio != null) audio.setMuted(muted);
    }

    public boolean muted() {
        return audio != null && audio.muted();
    }

    /**
     * Current playback position in seconds along the source timeline.
     * Driven by the audio clock when audio is playing, wall-clock fallback
     * otherwise. Returns 0 before the first frame has been displayed.
     */
    public double currentTimeSeconds() {
        return currentClockNanos() / 1_000_000_000.0;
    }

    /**
     * Total duration in seconds, or {@link Double#POSITIVE_INFINITY} for
     * live / unbounded streams (where FFmpeg reports {@code AV_NOPTS_VALUE}).
     */
    public double durationSeconds() {
        if (formatCtx == null) return Double.POSITIVE_INFINITY;
        long dur = formatCtx.duration();
        if (dur == AV_NOPTS_VALUE || dur <= 0) return Double.POSITIVE_INFINITY;
        return dur / 1_000_000.0; // AV_TIME_BASE = 1_000_000
    }

    /**
     * When true, the decoder rewinds to time 0 on EOF instead of stopping.
     * Has no effect on live sources (av_seek_frame fails). Switch on/off
     * at any time — the next EOF respects the current value.
     */
    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public boolean isLooping() {
        return loop;
    }

    /**
     * Seek to {@code seconds} in the source timeline. Thread-safe — the
     * decoder thread performs the actual {@code av_seek_frame} on its next
     * read iteration, then flushes both codec contexts and (if present)
     * resets the audio queue + clock. The video clock anchor is reset so
     * pacing re-stabilizes around the new position.
     *
     * <p>Negative values are clamped to 0. Out-of-range seeks return without
     * crashing; FFmpeg simply rejects the seek.
     */
    public void seek(double seconds) {
        if (closed.get()) return;
        seekTargetMicros = Math.max(0L, (long) (seconds * 1_000_000.0));
        seekRequested = true;
        ended.set(false);
    }

    /**
     * Start (or resume) playback. Idempotent: calling on an already-running
     * tween just clears any pause flag and resumes.
     */
    public synchronized void play() {
        if (closed.get()) return;
        if (running.get()) {
            if (paused) {
                paused = false;
                if (pausedAtNanos > 0) {
                    pauseAccumNanos += System.nanoTime() - pausedAtNanos;
                    pausedAtNanos = -1L;
                }
                if (audio != null) audio.resume();
            }
            return;
        }
        running.set(true);
        decoderThread = new Thread(this::decodeLoop, "moddinglib-video-decoder");
        decoderThread.setDaemon(true);
        decoderThread.start();
    }

    /** Pause the wall-clock so PTS pacing stalls. Decoder thread keeps spinning lightly. */
    public synchronized void pause() {
        if (closed.get()) return;
        if (!paused) {
            paused = true;
            pausedAtNanos = System.nanoTime();
            if (audio != null) audio.pause();
        }
    }

    /**
     * Copy the latest decoded BGRA frame into {@code dest} as raw bytes, but
     * only if the current version differs from {@code lastVersion}. Returns
     * the version written (==lastVersion if nothing was copied).
     */
    public long readLatestFrame(byte[] dest, long lastVersion) {
        synchronized (frameLock) {
            long v = frameVersion.get();
            if (v == 0 || v == lastVersion) return v;
            int n = Math.min(dest.length, rgbView.capacity());
            rgbView.position(0);
            rgbView.get(dest, 0, n);
            return v;
        }
    }

    /**
     * Copy the latest decoded frame as packed ARGB ints into {@code dest}.
     * Each int is alpha-MSB / red / green / blue (the format
     * {@code NativeImage.setColorArgb} expects). Returns the frame version
     * actually written, or {@code lastVersion} if no new frame is available.
     *
     * <p>This is the fast path the {@code VideoComponent} uses — the buffer
     * is laid out as BGRA bytes by sws_scale, and the underlying ByteBuffer
     * is in little-endian order, so reading 4 bytes as one int yields ARGB
     * packed in one go (no per-pixel shifting in Java).
     */
    public long readLatestFrameArgb(int[] dest, long lastVersion) {
        synchronized (frameLock) {
            long v = frameVersion.get();
            if (v == 0 || v == lastVersion) return v;
            int pixels = width * height;
            int n = Math.min(dest.length, pixels);
            IntBuffer ints = rgbView.asIntBuffer();
            ints.position(0);
            ints.get(dest, 0, n);
            return v;
        }
    }

    /**
     * Fast path: native-to-native memcpy of the latest decoded frame into
     * the destination native memory address (typically a Minecraft
     * {@code NativeImage.pointer}). No JVM heap copy, no per-pixel JNI.
     *
     * <p>Returns the frame version actually written; equal to
     * {@code lastVersion} when no new frame is available.
     */
    public long copyLatestFrameToNative(long destAddr, long lastVersion) {
        if (destAddr == 0L) return lastVersion;
        synchronized (frameLock) {
            long v = frameVersion.get();
            if (v == 0 || v == lastVersion) return v;
            long bytes = (long) width * height * 4;
            // LWJGL's memCopy is a direct native-to-native memcpy by address;
            // bytedeco's BytePointer.address() returns the underlying long
            // pointer of our scaling buffer.
            MemoryUtil.memCopy(rgbBuffer.address(), destAddr, bytes);
            return v;
        }
    }

    private void decodeLoop() {
        try {
            while (running.get()) {
                if (seekRequested) {
                    seekRequested = false;
                    doSeek(seekTargetMicros);
                }
                if (paused) {
                    Thread.sleep(15);
                    continue;
                }
                int rd = av_read_frame(formatCtx, packet);
                if (rd < 0) {
                    // Drain decoders so the last frames render, then either
                    // loop-rewind or signal EOF.
                    avcodec_send_packet(videoCodecCtx, (AVPacket) null);
                    drainDecoder();
                    if (loop) {
                        doSeek(0L);
                        continue;
                    }
                    ended.set(true);
                    break;
                }
                if (packet.stream_index() == videoStreamIdx) {
                    if (avcodec_send_packet(videoCodecCtx, packet) >= 0) {
                        drainDecoder();
                    }
                } else if (audio != null && packet.stream_index() == audio.streamIndex()) {
                    audio.processPacket(packet);
                }
                av_packet_unref(packet);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            ended.set(true);
        } finally {
            running.set(false);
        }
    }

    /**
     * Run on the decoder thread. {@code av_seek_frame} with stream_index=-1
     * uses the AV_TIME_BASE_Q scale (microseconds), which matches what we
     * pass in here. AVSEEK_FLAG_BACKWARD seeks to the keyframe at or before
     * the requested time so decoders have something coherent to start from.
     */
    private void doSeek(long targetMicros) {
        int rc = av_seek_frame(formatCtx, -1, targetMicros, AVSEEK_FLAG_BACKWARD);
        if (rc < 0) return;
        avcodec_flush_buffers(videoCodecCtx);
        if (audio != null) {
            audio.flushCodec();
            audio.requestReset();
        }
        // Clock anchors: forget the wall-clock baseline so the fallback
        // re-zeroes itself, and reset accumulated pause time.
        startNanos = -1L;
        pauseAccumNanos = 0L;
        pausedAtNanos = paused ? System.nanoTime() : -1L;

        // If we land here while paused, the main decode loop is going to
        // skip every read (paused branch) so the on-screen frame stays at
        // the pre-seek position. Decode + publish a single frame inline
        // so the user sees the new spot. Done synchronously on the same
        // decoder thread, so no threading concerns.
        if (paused) {
            decodeOnePostSeekFrame();
        }
    }

    /**
     * Drain enough packets after a seek to emit exactly one video frame,
     * then publish it. Audio packets we encounter on the way are queued
     * normally so AL is primed for when the user unpauses. Called only
     * from the decoder thread (inside {@link #doSeek}).
     */
    private void decodeOnePostSeekFrame() {
        int packetsTried = 0;
        try {
            while (running.get() && packetsTried++ < 128) {
                int rd = av_read_frame(formatCtx, packet);
                if (rd < 0) return;
                int streamIdx = packet.stream_index();
                if (streamIdx == videoStreamIdx) {
                    int sent = avcodec_send_packet(videoCodecCtx, packet);
                    av_packet_unref(packet);
                    if (sent < 0) continue;
                    int recv = avcodec_receive_frame(videoCodecCtx, decodedFrame);
                    if (recv == 0) {
                        sws_scale(swsCtx,
                                decodedFrame.data(), decodedFrame.linesize(), 0, height,
                                rgbFrame.data(), rgbFrame.linesize());
                        synchronized (frameLock) {
                            frameVersion.incrementAndGet();
                        }
                        av_frame_unref(decodedFrame);
                        return;
                    }
                } else if (audio != null && streamIdx == audio.streamIndex()) {
                    audio.processPacket(packet);
                    av_packet_unref(packet);
                } else {
                    av_packet_unref(packet);
                }
            }
        } catch (Throwable ignored) {
            // Best-effort refresh; if anything goes wrong, leave the old
            // frame on screen. The next play() will recover.
        }
    }

    private void drainDecoder() throws InterruptedException {
        while (running.get()) {
            int recv = avcodec_receive_frame(videoCodecCtx, decodedFrame);
            if (recv == AVERROR_EAGAIN() || recv == AVERROR_EOF()) return;
            if (recv < 0) return;

            sws_scale(swsCtx, decodedFrame.data(), decodedFrame.linesize(), 0, height,
                    rgbFrame.data(), rgbFrame.linesize());

            long pts = decodedFrame.best_effort_timestamp();
            long frameTargetNanos = (pts == AV_NOPTS_VALUE)
                    ? 0L
                    : (long) (pts * timeBase * 1_000_000_000.0);

            // Wait until the master clock catches up to this frame. If the
            // user pauses mid-wait we exit the loop and just publish; the
            // outer loop will then handle the pause on the next iteration.
            while (running.get() && !paused) {
                long clockNanos = currentClockNanos();
                long deltaNanos = frameTargetNanos - clockNanos;
                if (deltaNanos <= 0) break;
                long sleepMs = Math.max(1, Math.min(20L, deltaNanos / 1_000_000L));
                Thread.sleep(sleepMs);
            }

            // Drop frames >100ms behind to catch up — but only when we're
            // actually playing. If paused (race during wait above), keep
            // the frame so the screen refreshes.
            if (!paused) {
                long clockAfter = currentClockNanos();
                if (frameTargetNanos != 0 && clockAfter - frameTargetNanos > 100_000_000L) {
                    av_frame_unref(decodedFrame);
                    continue;
                }
            }

            synchronized (frameLock) {
                frameVersion.incrementAndGet();
            }
            av_frame_unref(decodedFrame);
        }
    }

    /**
     * Current playback position in source-timeline nanoseconds. Prefers the
     * audio clock once it has actually started producing samples; falls back
     * to a wall-clock derived from {@link #startNanos} otherwise (handles
     * audio-less sources, audio init failure, and the brief startup window
     * before the first AL buffer plays).
     */
    private long currentClockNanos() {
        if (audio != null && audio.isClockStarted()) {
            return audio.audioClockNanos();
        }
        if (startNanos < 0) startNanos = System.nanoTime();
        return Math.max(0, System.nanoTime() - startNanos - pauseAccumNanos);
    }

    /**
     * Stop the decoder thread and free every native handle. Idempotent —
     * calling more than once does nothing.
     */
    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) return;
        running.set(false);
        if (decoderThread != null) {
            decoderThread.interrupt();
            try { decoderThread.join(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            decoderThread = null;
        }
        // Audio first — its codec context lives off the format context, but
        // it has its own AL state independent of the format demuxer.
        if (audio != null)         { audio.close(); audio = null; }
        if (packet != null)        { av_packet_free(packet); packet = null; }
        if (decodedFrame != null)  { av_frame_free(decodedFrame); decodedFrame = null; }
        if (rgbFrame != null)      { av_frame_free(rgbFrame); rgbFrame = null; }
        if (rgbBuffer != null)     { av_free(rgbBuffer); rgbBuffer = null; }
        if (swsCtx != null)        { sws_freeContext(swsCtx); swsCtx = null; }
        if (videoCodecCtx != null) { avcodec_free_context(videoCodecCtx); videoCodecCtx = null; }
        if (formatCtx != null)     { avformat_close_input(formatCtx); formatCtx = null; }
    }

    /** Thrown by {@link #open(String)} when any FFmpeg step refuses the source. */
    public static class VideoOpenException extends RuntimeException {
        public VideoOpenException(String message) { super(message); }
    }
}
