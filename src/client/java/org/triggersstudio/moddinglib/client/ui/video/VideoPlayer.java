package org.triggersstudio.moddinglib.client.ui.video;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecHWConfig;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOInterruptCB;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVBufferRef;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.lwjgl.system.MemoryUtil;
import org.triggersstudio.moddinglib.client.ui.components.VideoComponent;

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
    /** Source pix_fmt the {@code swsCtx} was created against — recreated
     *  lazily if the first hw-transferred frame turns out to use a
     *  different format (NV12 vs P010 vs ...). */
    private int swsSrcPixFmt = AV_PIX_FMT_NONE;
    private final int videoStreamIdx;
    private AVFrame decodedFrame;
    /** Sw destination for {@code av_hwframe_transfer_data}. Only allocated
     *  when {@link #hwDeviceCtx} is non-null (i.e. we're in hw-decode
     *  mode). */
    private AVFrame hwTransferFrame;
    private AVFrame rgbFrame;
    private AVPacket packet;
    private BytePointer rgbBuffer;
    private final ByteBuffer rgbView;

    /** {@code null} when running in pure software mode. Otherwise the
     *  hwdevice context handed off to {@code AVCodecContext.hw_device_ctx}.
     *  Owned and freed by {@link #close()}. */
    private AVBufferRef hwDeviceCtx;
    /** The pix_fmt FFmpeg will emit on the hw path — i.e. what we compare
     *  {@code decodedFrame.format()} against to detect "this frame needs
     *  transfer". {@code AV_PIX_FMT_NONE} when running pure sw. */
    private final int hwPixFmt;

    private Thread decoderThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean paused = false;
    private final AtomicBoolean ended = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    /** Flipped by {@link #close()} to make the FFmpeg interrupt callback
     *  return 1 — that aborts any in-flight blocking I/O (network read,
     *  protocol open) so the decoder thread exits in milliseconds rather
     *  than waiting for an OS-level timeout. */
    private final AtomicBoolean interrupted;
    /** Strong reference to the JavaCPP function pointer wired into
     *  {@code formatCtx.interrupt_callback}. Must outlive every
     *  {@code av_read_frame} / {@code avformat_*} call — if it gets GC'd
     *  while FFmpeg holds the C function pointer, the next invocation
     *  segfaults. */
    @SuppressWarnings("FieldCanBeLocal")
    private final AVIOInterruptCB.Callback_Pointer interruptCallback;

    private final Object frameLock = new Object();
    private final AtomicLong frameVersion = new AtomicLong(0);

    private long startNanos = -1L;
    private long pausedAtNanos = -1L;
    private long pauseAccumNanos = 0L;

    private AudioStream audio; // nullable — sources without audio still play

    /** When true, decoder rewinds to start on EOF instead of ending. */
    private volatile boolean loop = false;
    /** Source-time advance multiplier. {@code 1.0} = normal speed,
     *  {@code 2.0} = twice as fast, {@code 0.5} = half speed. Audio pitch
     *  follows (chipmunk mode — no formant correction). Clamped at the
     *  setter to {@code [0.25, 4.0]}. */
    private volatile double playbackRate = 1.0;
    /** Seek command from any thread → consumed by decoder thread.
     *  {@code -1} = no pending seek; otherwise the target in microseconds.
     *  Atomic so a rapid sequence of {@code seek()} calls always reaches the
     *  decoder as the latest value, never as a stale one read between the
     *  old "flag + target" pair. */
    private final AtomicLong pendingSeekMicros = new AtomicLong(-1L);
    /** PTS in nanoseconds of the most recently published video frame.
     *  Used by {@link #currentTimeSeconds()} as the canonical "where am I"
     *  reference — more reliable than the audio/wall master clock right
     *  after a seek-while-paused, where the audio clock has been reset and
     *  the wall clock has just been zeroed. */
    private volatile long lastPublishedPtsNanos = 0L;

    private VideoPlayer(String source, AVFormatContext formatCtx, int videoStreamIdx,
                        AVCodecContext videoCodecCtx,
                        AVFrame decodedFrame, AVFrame rgbFrame, AVFrame hwTransferFrame, AVPacket packet,
                        BytePointer rgbBuffer, double timeBase, AudioStream audio,
                        AtomicBoolean interrupted,
                        AVIOInterruptCB.Callback_Pointer interruptCallback,
                        AVBufferRef hwDeviceCtx, int hwPixFmt) {
        this.source = source;
        this.formatCtx = formatCtx;
        this.videoStreamIdx = videoStreamIdx;
        this.videoCodecCtx = videoCodecCtx;
        // sws context is created lazily on the first decoded frame so we can
        // size its input pix_fmt against whatever actually comes out of the
        // decoder (sw codec pix_fmt vs hw-transfer pix_fmt).
        this.swsCtx = null;
        this.decodedFrame = decodedFrame;
        this.rgbFrame = rgbFrame;
        this.hwTransferFrame = hwTransferFrame;
        this.packet = packet;
        this.rgbBuffer = rgbBuffer;
        this.width = videoCodecCtx.width();
        this.height = videoCodecCtx.height();
        this.timeBase = timeBase;
        // BGRA bytes read as little-endian int = ARGB packed.
        this.rgbView = rgbBuffer.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        this.audio = audio;
        this.interrupted = interrupted;
        this.interruptCallback = interruptCallback;
        this.hwDeviceCtx = hwDeviceCtx;
        this.hwPixFmt = hwPixFmt;
    }

    private record HardwareProbe(int deviceType, int hwPixFmt) {}

    /**
     * Walk the codec's hw configs in our preferred device-type order.
     * Returns the first hit that supports {@code HW_DEVICE_CTX} setup
     * (the cleanest hw path), or {@code null} when nothing matched.
     *
     * <p>Order rationale: CUDA is the most reliable cross-OS hw path
     * (NVIDIA GPU + nvdec drivers); D3D11VA is the modern Windows native;
     * DXVA2 is its legacy fallback; QSV covers Intel iGPUs; VideoToolbox
     * is macOS native; VAAPI is the Linux native.
     */
    private static HardwareProbe probeHardwareDecode(AVCodec codec) {
        int[] preferred = {
                AV_HWDEVICE_TYPE_CUDA,
                AV_HWDEVICE_TYPE_D3D11VA,
                AV_HWDEVICE_TYPE_DXVA2,
                AV_HWDEVICE_TYPE_QSV,
                AV_HWDEVICE_TYPE_VIDEOTOOLBOX,
                AV_HWDEVICE_TYPE_VAAPI,
        };
        for (int wantedType : preferred) {
            for (int i = 0; ; i++) {
                AVCodecHWConfig cfg = avcodec_get_hw_config(codec, i);
                if (cfg == null || cfg.isNull()) break;
                if ((cfg.methods() & AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX) != 0
                        && cfg.device_type() == wantedType) {
                    return new HardwareProbe(wantedType, cfg.pix_fmt());
                }
            }
        }
        return null;
    }

    private static final AtomicBoolean NETWORK_INIT_DONE = new AtomicBoolean(false);

    private static void ensureNetworkInit() {
        if (NETWORK_INIT_DONE.compareAndSet(false, true)) {
            avformat_network_init();
        }
    }

    private static String ffmpegError(int code) {
        try (BytePointer buf = new BytePointer(256)) {
            av_strerror(code, buf, 256);
            String msg = buf.getString();
            return (msg != null && !msg.isEmpty() ? msg : "(no message)") + " [code=" + code + "]";
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
        return open(source, false);
    }

    /**
     * Open a video source with optional hardware decoding. When
     * {@code hardware} is {@code true} the player probes the codec's
     * supported {@code AV_HWDEVICE_TYPE_*} configs (CUDA, D3D11VA, DXVA2,
     * QSV, VideoToolbox, VAAPI) and binds the first one whose device
     * creates successfully. If every probe fails the codec is opened in
     * software mode — no error is raised, the caller still gets a working
     * player. This is opt-in because the hw path is platform-dependent
     * and harder to validate; software stays the safe default.
     *
     * <p>Once hardware is bound, decoded frames live on the GPU; an
     * {@code av_hwframe_transfer_data} step in the decode loop brings them
     * back to system memory before sws_scale. The transfer itself costs a
     * VRAM→RAM copy per frame, so hardware decode is a clear win on H.264
     * / H.265 at 1080p+ where the decoder is the CPU bottleneck.
     */
    public static VideoPlayer open(String source, boolean hardware) {
        ensureNetworkInit();

        // Interrupt machinery — wired BEFORE avformat_open_input so a slow
        // DNS / TCP connect can be aborted by close() during the open
        // phase too. The callback returns 1 once `interrupted` flips,
        // making any in-flight blocking I/O return AVERROR_EXIT instead
        // of waiting on the OS timeout.
        AtomicBoolean interrupted = new AtomicBoolean(false);
        AVIOInterruptCB.Callback_Pointer interruptCallback =
                new AVIOInterruptCB.Callback_Pointer() {
                    @Override
                    public int call(Pointer opaque) {
                        return interrupted.get() ? 1 : 0;
                    }
                };

        // Locals tracked for cleanup-on-failure. Each becomes non-null as
        // its FFmpeg allocator succeeds; the catch block frees in reverse
        // order. fmtOpened tracks whether avformat_open_input has reached
        // the point where avformat_close_input is the right teardown
        // (FFmpeg already frees formatCtx itself when open fails).
        AVFormatContext formatCtx = null;
        AVCodecContext videoCodecCtx = null;
        AVFrame decodedFrame = null;
        AVFrame rgbFrame = null;
        AVFrame hwTransferFrame = null;
        AVPacket packet = null;
        BytePointer rgbBuffer = null;
        AudioStream audio = null;
        AVDictionary openOpts = null;
        AVBufferRef hwDeviceCtx = null;
        int hwPixFmt = AV_PIX_FMT_NONE;
        boolean fmtOpened = false;

        try {
            // avformat_alloc_context allocates the actual AVFormatContext
            // struct so we can set the interrupt callback on it BEFORE the
            // open. `new AVFormatContext(null)` only wraps a null native
            // pointer — accessing any field on that throws "The pointer
            // address is null". avformat_open_input still works on a
            // pre-allocated context (it just uses it instead of
            // allocating its own).
            formatCtx = avformat_alloc_context();
            if (formatCtx == null || formatCtx.isNull()) {
                formatCtx = null;
                throw new VideoOpenException("avformat_alloc_context returned null");
            }
            formatCtx.interrupt_callback().callback(interruptCallback);

            // Bump probe + analyze so the H.264 SPS lands inside the analysis
            // window even on slow / partial streams. Without this, FFmpeg can
            // return a stream with pix_fmt=NONE and the next sws_getContext
            // hits an assertion in libswscale.
            openOpts = new AVDictionary(null);
            av_dict_set(openOpts, "probesize", "20000000", 0);
            av_dict_set(openOpts, "analyzeduration", "20000000", 0);
            // Network read timeouts (microseconds). Different protocols
            // honor different keys, so set all three; unknown keys are
            // ignored. 5s is generous enough to absorb a normal RTT spike
            // without sitting on a dead socket forever.
            av_dict_set(openOpts, "rw_timeout", "5000000", 0);
            av_dict_set(openOpts, "stimeout",  "5000000", 0);
            av_dict_set(openOpts, "timeout",   "5000000", 0);

            int ret = avformat_open_input(formatCtx, source, null, openOpts);
            if (ret < 0) {
                // FFmpeg already freed formatCtx on open failure — null our ref so the catch block doesn't try to free it again.
                formatCtx = null;
                throw new VideoOpenException("avformat_open_input failed for " + source + ": " + ffmpegError(ret));
            }
            fmtOpened = true;

            // Mirror the bumped values on the context for find_stream_info too.
            formatCtx.probesize(20_000_000L);
            formatCtx.max_analyze_duration(20_000_000L);

            ret = avformat_find_stream_info(formatCtx, (PointerPointer<?>) null);
            if (ret < 0) {
                throw new VideoOpenException("avformat_find_stream_info failed for " + source + ": " + ffmpegError(ret));
            }
            int videoIdx = av_find_best_stream(formatCtx, AVMEDIA_TYPE_VIDEO, -1, -1, (AVCodec) null, 0);
            if (videoIdx < 0) {
                throw new VideoOpenException("No video stream in " + source + " (av_find_best_stream=" + videoIdx + ")");
            }
            AVStream videoStream = formatCtx.streams(videoIdx);
            AVCodecParameters params = videoStream.codecpar();
            AVCodec codec = avcodec_find_decoder(params.codec_id());
            if (codec == null) {
                throw new VideoOpenException("Unsupported codec id=" + params.codec_id());
            }
            videoCodecCtx = avcodec_alloc_context3(codec);
            if (videoCodecCtx == null) {
                throw new VideoOpenException("avcodec_alloc_context3 returned null");
            }
            ret = avcodec_parameters_to_context(videoCodecCtx, params);
            if (ret < 0) {
                throw new VideoOpenException("avcodec_parameters_to_context failed: " + ffmpegError(ret));
            }

            if (hardware) {
                HardwareProbe probe = probeHardwareDecode(codec);
                if (probe != null) {
                    try (PointerPointer<AVBufferRef> devicePtr = new PointerPointer<>(1)) {
                        int rc = av_hwdevice_ctx_create(devicePtr, probe.deviceType, null, null, 0);
                        if (rc >= 0) {
                            hwDeviceCtx = new AVBufferRef(devicePtr.get(0));
                            videoCodecCtx.hw_device_ctx(av_buffer_ref(hwDeviceCtx));
                            hwPixFmt = probe.hwPixFmt;
                        }
                    }
                }
            }

            ret = avcodec_open2(videoCodecCtx, codec, (AVDictionary) null);
            if (ret < 0) {
                throw new VideoOpenException("avcodec_open2 failed: " + ffmpegError(ret));
            }
            int w = videoCodecCtx.width();
            int h = videoCodecCtx.height();
            int srcPixFmt = videoCodecCtx.pix_fmt();
            if (srcPixFmt == AV_PIX_FMT_NONE && hwPixFmt == AV_PIX_FMT_NONE) {
                throw new VideoOpenException("Stream pix_fmt is unspecified — try a longer / less truncated source");
            }
            decodedFrame = av_frame_alloc();
            rgbFrame = av_frame_alloc();
            packet = av_packet_alloc();
            if (decodedFrame == null || rgbFrame == null || packet == null) {
                throw new VideoOpenException("av_frame_alloc / av_packet_alloc returned null");
            }
            if (hwDeviceCtx != null) {
                hwTransferFrame = av_frame_alloc();
                if (hwTransferFrame == null) {
                    throw new VideoOpenException("av_frame_alloc (hw transfer) returned null");
                }
            }
            int byteCount = av_image_get_buffer_size(AV_PIX_FMT_BGRA, w, h, 1);
            BytePointer rawBuf = new BytePointer(av_malloc(byteCount));
            if (rawBuf.isNull()) {
                throw new VideoOpenException("av_malloc(" + byteCount + ") returned null");
            }
            rgbBuffer = rawBuf;
            rgbBuffer.capacity(byteCount);
            av_image_fill_arrays(rgbFrame.data(), rgbFrame.linesize(),
                    rgbBuffer, AV_PIX_FMT_BGRA, w, h, 1);
            double timeBase = av_q2d(videoStream.time_base());

            /* Optional audio stream — failure to open audio is non-fatal; the video keeps playing silently.
            We pick the best audio stream that shares a program with the chosen video, matching FFmpeg defaults. */
            int audioIdx = av_find_best_stream(formatCtx, AVMEDIA_TYPE_AUDIO, -1, videoIdx, (AVCodec) null, 0);
            if (audioIdx >= 0) {
                try {
                    audio = AudioStream.open(formatCtx.streams(audioIdx), audioIdx);
                } catch (Exception t) {
                    // Swallow — better to play silent video than crash the open.
                    audio = null;
                }
            }

            return new VideoPlayer(source, formatCtx, videoIdx, videoCodecCtx,
                    decodedFrame, rgbFrame, hwTransferFrame, packet, rgbBuffer, timeBase, audio,
                    interrupted, interruptCallback, hwDeviceCtx, hwPixFmt);

        } catch (Exception t) {
            /* Reverse-order cleanup of partial allocations — anything that constructed successfully gets freed; anything that didn't is
            still null and the guard skips it. */
            if (audio != null) {
                try {
                    audio.close();
                } catch (Exception ignored) {}
            }
            if (rgbBuffer != null) av_free(rgbBuffer);
            if (rgbFrame != null) av_frame_free(rgbFrame);
            if (hwTransferFrame != null) av_frame_free(hwTransferFrame);
            if (decodedFrame != null) av_frame_free(decodedFrame);
            if (packet != null) av_packet_free(packet);
            if (hwDeviceCtx != null) av_buffer_unref(hwDeviceCtx);
            if (videoCodecCtx != null) avcodec_free_context(videoCodecCtx);
            if (formatCtx != null) {
                /* close_input both closes the opened input AND frees the context; free_context only frees the allocated struct.
                Pick based on whether we got past avformat_open_input. */
                if (fmtOpened) {
                    avformat_close_input(formatCtx);
                } else {
                    avformat_free_context(formatCtx);
                }
            }
            if (t instanceof RuntimeException re) throw re;
            throw new VideoOpenException("VideoPlayer.open(" + source + ") failed: " + t.getMessage());
        } finally {
            if (openOpts != null) av_dict_free(openOpts);
        }
    }

    public String getSource() {
        return source;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isEnded() {
        return ended.get();
    }

    public boolean isRunning() {
        return running.get();
    }

    public long frameVersion() {
        return frameVersion.get();
    }

    public boolean hasAudio() {
        return audio != null;
    }

    /** @return {@code true} when the codec is decoding on a GPU /
     *  hardware accelerator (CUDA, D3D11VA, etc.); {@code false} in
     *  pure-software mode. */
    public boolean hasHardwareDecode() { return hwDeviceCtx != null; }

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
     *
     * <p>Returns the audio playback time when audio is actually running
     * (smooth, sub-frame precision); otherwise the PTS of the last
     * published video frame. The latter is critical right after a seek
     * — the audio clock has been reset and the wall clock zeroed, but
     * we know what frame we just decoded, so we report that.
     */
    public double currentTimeSeconds() {
        if (audio != null && audio.isClockStarted() && !paused) {
            return audio.audioClockNanos() / 1_000_000_000.0;
        }
        return lastPublishedPtsNanos / 1_000_000_000.0;
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
     * Set the playback speed. {@code 1.0} = normal, {@code 2.0} = double
     * speed, {@code 0.5} = half. Clamped to {@code [0.25, 4.0]}.
     *
     * <p>Audio plays via OpenAL's {@code AL_PITCH} — there is no pitch
     * correction, so the audio sounds higher / lower at the extremes
     * (chipmunk effect at 2x). For pitch-preserving speed change a
     * dedicated time-stretch library (e.g. SoundTouch) would be needed —
     * outside this player's scope.
     *
     * <p>Video pacing scales the wall-clock fallback accordingly so frames
     * advance at the same rate as the source-time clock. When audio is
     * present, the audio clock naturally tracks rate'd consumption.
     */
    public void setPlaybackRate(double rate) {
        if (Double.isNaN(rate)) return;
        double clamped = Math.clamp(rate, 0.25, 4.0);
        this.playbackRate = clamped;
        if (audio != null) {
            audio.setPitch((float) clamped);
        }
        // Re-anchor wall-clock at the current source-time so the rate change doesn't visually jump the clock forward or backward.
        long nowSourceNanos = currentClockNanos();
        long wallEquiv = (long) (nowSourceNanos / clamped);
        startNanos = System.nanoTime() - wallEquiv;
        pauseAccumNanos = 0L;
    }

    public double getPlaybackRate() {
        return playbackRate;
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
        pendingSeekMicros.set(Math.max(0L, (long) (seconds * 1_000_000.0)));
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
            /* LWJGL's memCopy is a direct native-to-native memcpy by address;
            bytedeco's BytePointer.address() returns the underlying long pointer of our scaling buffer. */
            MemoryUtil.memCopy(rgbBuffer.address(), destAddr, bytes);
            return v;
        }
    }

    private void decodeLoop() {
        try {
            while (running.get()) {
                long pendingSeek = pendingSeekMicros.getAndSet(-1L);
                if (pendingSeek >= 0L) {
                    doSeek(pendingSeek);
                }
                if (paused) {
                    Thread.sleep(15);
                    continue;
                }
                int rd = av_read_frame(formatCtx, packet);
                if (rd < 0) {
                    // Drain decoders so the last frames render, then either loop-rewind or signal EOF.
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
        } catch (Exception t) {
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
        long targetNanosForAudio = targetMicros * 1_000L;
        if (audio != null) {
            audio.flushCodec();
            audio.requestReset(targetNanosForAudio);
        }
        /* Anchor the wall-clock to the seek target rather than zero. Without this, drainDecoder's pacing loop saw clockNanos = 0 against a
        frameTargetNanos far in the future and slept the full seek distance — i.e. seeking to +10s on a silent source froze the
        image for 10s of wall time. For audio-bearing sources it also covers the gap until the AL clock starts producing samples.
        The wall→source-time mapping respects playbackRate so seeking at 2x lands the clock at the right wall-time anchor. */
        long targetNanos = targetMicros * 1_000L;
        long wallEquiv = playbackRate == 1.0 ? targetNanos : (long) (targetNanos / playbackRate);
        startNanos = System.nanoTime() - wallEquiv;
        pauseAccumNanos = 0L;
        pausedAtNanos = paused ? System.nanoTime() : -1L;

        /* Seed the position with the seek target so currentTimeSeconds() reads correctly even before the next frame actually publishes
        (without this, a fast user retap of "+5s" would compute against a stale audio clock or a freshly-zeroed wall clock). */
        lastPublishedPtsNanos = targetNanos;

        /* If we land here while paused, the main decode loop is going to skip every read (paused branch) so the on-screen frame stays at
        the pre-seek position. Decode + publish a frame inline so the user sees the new spot. Done synchronously on the same decoder
        thread, so no threading concerns. */
        if (paused) {
            decodeUntilTargetFrame(targetNanos);
        }
    }

    /**
     * Drain packets after a seek until we decode a frame whose PTS reaches
     * the seek target, then publish it. {@code av_seek_frame} with
     * {@code AVSEEK_FLAG_BACKWARD} lands on the keyframe BEFORE the target,
     * so the first decoded frame is typically a few seconds early — naively
     * publishing it would show the wrong spot and overwrite
     * {@link #lastPublishedPtsNanos} with a value behind the seek. We scale
     * each intermediate frame into the shared {@code rgbBuffer} (silently
     * overwriting until we reach the target) and only bump
     * {@link #frameVersion} for the frame that's at-or-past target — that
     * way the render thread never observes the pre-target intermediates.
     *
     * <p>Audio packets we encounter on the way are pushed into the audio
     * pipeline so AL is primed for unpause. Called only from the decoder
     * thread (inside {@link #doSeek}).
     */
    private void decodeUntilTargetFrame(long targetNanos) {
        int packetsTried = 0;
        long latestPtsNanos = 0L;
        boolean haveCandidate = false;
        try {
            while (running.get() && packetsTried++ < 256) {
                int rd = av_read_frame(formatCtx, packet);
                if (rd < 0) break;
                int streamIdx = packet.stream_index();
                if (streamIdx == videoStreamIdx) {
                    int sent = avcodec_send_packet(videoCodecCtx, packet);
                    av_packet_unref(packet);
                    if (sent < 0) continue;
                    while ((avcodec_receive_frame(videoCodecCtx, decodedFrame)) == 0) {
                        long pts = decodedFrame.best_effort_timestamp();
                        long ptsNanos = (pts == AV_NOPTS_VALUE) ? 0L
                                : (long) (pts * timeBase * 1_000_000_000.0);
                        /* Overwrite the scratch buffer with every decode.
                        Render thread only observes it via frameVersion
                        changes, and we don't bump that until target or
                        the give-up path below — so intermediate writes
                        never flash on screen. */
                        boolean ok = scaleFrame();
                        av_frame_unref(decodedFrame);
                        if (!ok) continue;
                        latestPtsNanos = ptsNanos;
                        haveCandidate = true;
                        if (ptsNanos >= targetNanos) {
                            publishCurrentFrame(ptsNanos);
                            return;
                        }
                    }
                } else if (audio != null && streamIdx == audio.streamIndex()) {
                    audio.processPacket(packet);
                    av_packet_unref(packet);
                } else {
                    av_packet_unref(packet);
                }
            }
            /* Ran out of packets / hit the iteration cap without reaching
            target — publish whatever we last decoded so the user at
            least sees something close to where they asked. */
            if (haveCandidate) {
                publishCurrentFrame(latestPtsNanos);
            }
        } catch (Exception ignored) {
            // Best-effort refresh; if anything goes wrong, leave the old frame on screen. The next play() will recover.
        }
    }

    private void publishCurrentFrame(long ptsNanos) {
        synchronized (frameLock) {
            frameVersion.incrementAndGet();
            if (ptsNanos != 0) lastPublishedPtsNanos = ptsNanos;
        }
    }

    /**
     * Scale {@link #decodedFrame} into {@link #rgbFrame}. When running in
     * hw mode and the decoder emitted a hw frame (format matches
     * {@link #hwPixFmt}), copy from GPU to {@link #hwTransferFrame} first
     * and scale from there. The sws context is created / recreated lazily
     * so its source pix_fmt always matches whichever buffer we're feeding.
     *
     * @return {@code true} on success. On any failure (hw transfer error,
     *     sws context creation refused) the caller should treat the frame
     *     as undecoded — no scratch buffer write happened.
     */
    private boolean scaleFrame() {
        AVFrame src = decodedFrame;
        if (hwDeviceCtx != null && hwTransferFrame != null && decodedFrame.format() == hwPixFmt) {
            int rc = av_hwframe_transfer_data(hwTransferFrame, decodedFrame, 0);
            if (rc < 0) return false;
            src = hwTransferFrame;
        }
        int srcFmt = src.format();
        if (srcFmt == AV_PIX_FMT_NONE) return false;
        if (swsCtx == null || swsSrcPixFmt != srcFmt) {
            if (swsCtx != null) {
                sws_freeContext(swsCtx);
                swsCtx = null;
            }
            swsCtx = sws_getContext(
                    width, height, srcFmt,
                    width, height, AV_PIX_FMT_BGRA,
                    SWS_BILINEAR, null, null, (DoublePointer) null);
            swsSrcPixFmt = srcFmt;
            if (swsCtx == null) return false;
        }
        sws_scale(swsCtx,
                src.data(), src.linesize(), 0, height,
                rgbFrame.data(), rgbFrame.linesize());
        if (src == hwTransferFrame) {
            av_frame_unref(hwTransferFrame);
        }
        return true;
    }

    private void drainDecoder() throws InterruptedException {
        while (running.get()) {
            int recv = avcodec_receive_frame(videoCodecCtx, decodedFrame);
            if (recv == AVERROR_EAGAIN() || recv == AVERROR_EOF()) return;
            if (recv < 0) return;

            long pts = decodedFrame.best_effort_timestamp();
            long frameTargetNanos = (pts == AV_NOPTS_VALUE)
                    ? 0L
                    : (long) (pts * timeBase * 1_000_000_000.0);

            /* Pre-drop before sws_scale to avoid burning CPU scaling
            frames we know we'll throw away. Especially important right
            after a seek-while-playing: av_seek_frame(BACKWARD) drops
            us on the keyframe before the target, so up to ~GOP-many
            pre-target frames stream out and we'd otherwise scale every
            one of them just to discard them in the post-wait drop. */
            if (!paused && frameTargetNanos != 0) {
                long clockNow = currentClockNanos();
                if (clockNow - frameTargetNanos > 100_000_000L) {
                    av_frame_unref(decodedFrame);
                    continue;
                }
            }

            boolean scaled = scaleFrame();
            if (!scaled) {
                av_frame_unref(decodedFrame);
                continue;
            }

            /* Wait until the master clock catches up to this frame. If the
            user pauses mid-wait we exit the loop and just publish; the
            outer loop will then handle the pause on the next iteration. */
            while (running.get() && !paused) {
                long clockNanos = currentClockNanos();
                long deltaNanos = frameTargetNanos - clockNanos;
                if (deltaNanos <= 0) break;
                long sleepMs = Math.clamp(deltaNanos / 1_000_000L, 1, 20L);
                Thread.sleep(sleepMs);
            }

            /* Drop frames >100ms behind to catch up — but only when we're
            actually playing. If paused (race during wait above), keep
            the frame so the screen refreshes. */
            if (!paused) {
                long clockAfter = currentClockNanos();
                if (frameTargetNanos != 0 && clockAfter - frameTargetNanos > 100_000_000L) {
                    av_frame_unref(decodedFrame);
                    continue;
                }
            }

            synchronized (frameLock) {
                frameVersion.incrementAndGet();
                if (frameTargetNanos != 0) lastPublishedPtsNanos = frameTargetNanos;
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
     *
     * <p>When {@link #playbackRate} != 1, the wall-clock fallback multiplies
     * elapsed wall time by the rate so source-time advances faster (>1) or
     * slower (<1) per real-time second. The audio path needs no
     * compensation: {@code AL_SAMPLE_OFFSET} reflects source samples
     * consumed, and AL pitch'd playback consumes faster in real time so
     * the offset already tracks rate'd source time.
     */
    private long currentClockNanos() {
        if (audio != null && audio.isClockStarted()) {
            return audio.audioClockNanos();
        }
        if (startNanos < 0) startNanos = System.nanoTime();
        long wallElapsed = Math.max(0, System.nanoTime() - startNanos - pauseAccumNanos);
        if (playbackRate == 1.0) return wallElapsed;
        return (long) (wallElapsed * playbackRate);
    }

    /**
     * Stop the decoder thread and free every native handle. Idempotent —
     * calling more than once does nothing.
     */
    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) return;
        /* Flip the interrupt flag FIRST. The decoder thread may be parked
        inside av_read_frame() on a slow / dead network read; the
        FFmpeg interrupt callback (wired in open()) will return 1 on
        its next poll and that call returns AVERROR_EXIT instead of
        sitting on an OS timeout. Without this, the join below could
        race the native call: Java would free formatCtx while the C
        side was still reading from it → segfault. */
        interrupted.set(true);
        running.set(false);
        if (decoderThread != null) {
            decoderThread.interrupt();
            try {
                /* 5s is a backstop, not the expected wait — with the
                interrupt callback the thread typically exits within
                tens of milliseconds. If it ever blows the timeout
                we'd rather log + leak than hard-abort the JVM. */
                decoderThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            decoderThread = null;
        }

        /* Audio first — its codec context lives off the format context, but
        it has its own AL state independent of the format demuxer. */
        if (audio != null)         { audio.close(); audio = null; }
        if (packet != null)        { av_packet_free(packet); packet = null; }
        if (decodedFrame != null)  { av_frame_free(decodedFrame); decodedFrame = null; }
        if (hwTransferFrame != null) { av_frame_free(hwTransferFrame); hwTransferFrame = null; }
        if (rgbFrame != null)      { av_frame_free(rgbFrame); rgbFrame = null; }
        if (rgbBuffer != null)     { av_free(rgbBuffer); rgbBuffer = null; }
        if (swsCtx != null)        { sws_freeContext(swsCtx); swsCtx = null; }
        if (videoCodecCtx != null) { avcodec_free_context(videoCodecCtx); videoCodecCtx = null; }
        if (hwDeviceCtx != null)   { av_buffer_unref(hwDeviceCtx); hwDeviceCtx = null; }
        if (formatCtx != null)     { avformat_close_input(formatCtx); formatCtx = null; }
    }

    /** Thrown by {@link #open(String)} when any FFmpeg step refuses the source. */
    public static class VideoOpenException extends RuntimeException {
        public VideoOpenException(String message) { super(message); }
    }
}
