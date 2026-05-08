package org.triggersstudio.moddinglib.client.ui.video;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swresample.SwrContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.PointerPointer;
import org.lwjgl.openal.AL10;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swresample.*;

/**
 * Phase 2.1 audio pipeline. Decodes the audio stream of a media source,
 * resamples to a stable target format (S16LE / 44.1kHz / stereo) and pushes
 * the converted PCM into an OpenAL source queue.
 *
 * <p><b>Threading split:</b>
 * <ul>
 *   <li>Decoder thread (in {@link VideoPlayer}) calls {@link #processPacket}.
 *       That path runs FFmpeg decode + sample conversion and enqueues raw
 *       PCM byte arrays into a cross-thread queue.</li>
 *   <li>Render thread calls {@link #pump()} once per frame. That side does
 *       all OpenAL work — recycling processed buffers, queuing pending PCM
 *       chunks, restarting the source after underruns. Keeping AL on the
 *       render thread sidesteps Minecraft's per-thread AL context binding.</li>
 * </ul>
 *
 * <p><b>Sync (phase 2.2):</b> each PCM chunk produced by {@code processPacket}
 * carries its source PTS in nanoseconds. When the chunk is queued onto AL
 * we record an {@link InFlightBuffer}. Each {@link #pump()} reads
 * {@code AL_SAMPLE_OFFSET} on the head buffer and stamps a snapshot of
 * "current audio playback time, nanos in source timeline" into a volatile
 * field that the video decoder thread reads through
 * {@link #audioClockNanos()} to pace frames.
 */
public final class AudioStream implements AutoCloseable {

    private static final int OUT_SAMPLE_RATE = 44_100;
    private static final int OUT_CHANNELS = 2;
    private static final int BYTES_PER_SAMPLE = 2; // S16
    /** Per-buffer wall-clock duration — short keeps latency low, big avoids underrun. */
    private static final int BUFFER_MS = 100;
    private static final int BUFFER_SAMPLES = OUT_SAMPLE_RATE * BUFFER_MS / 1000;
    private static final int BUFFER_BYTES = BUFFER_SAMPLES * OUT_CHANNELS * BYTES_PER_SAMPLE;
    private static final int AL_BUFFER_COUNT = 8;
    /** Cap pendingChunks size to avoid memory blow-up if AL stalls. */
    private static final int MAX_PENDING_CHUNKS = 32;

    /** PCM chunk produced by the decoder, stamped with its source PTS. */
    private record PendingChunk(byte[] pcm, long startPtsNanos, long durationNanos) {}

    /** A buffer currently sitting in the AL source queue. Render-thread only. */
    private record InFlightBuffer(int alBufferId, long startPtsNanos, long durationNanos) {}

    private final int streamIndex;
    private final double timeBase;
    private AVCodecContext codecCtx;
    private SwrContext swrCtx;
    private AVFrame frame;
    private AVChannelLayout outLayout;
    private BytePointer convertOutBuffer;
    private final int convertOutCapacity;

    // OpenAL state — only touched from render thread (via pump()).
    private int alSource = -1;
    private int[] alBuffers;
    private final ArrayDeque<Integer> freeAlBuffers = new ArrayDeque<>();
    private boolean alInitialized = false;
    private boolean alFailed = false;

    // Cross-thread: decoder fills, render drains.
    private final ConcurrentLinkedDeque<PendingChunk> pendingChunks = new ConcurrentLinkedDeque<>();

    /** AL queue head bookkeeping — render thread only. */
    private final ArrayDeque<InFlightBuffer> alQueue = new ArrayDeque<>();

    /** Latest known audio clock value (nanos in source timeline). Updated on
     *  every {@link #pump()}; read from any thread. */
    private volatile long lastClockNanos = 0L;
    /** {@link System#nanoTime()} when {@code lastClockNanos} was sampled.
     *  Used to extrapolate between pumps. */
    private volatile long lastClockSampleAtNanos = System.nanoTime();
    /** {@code true} once at least one buffer has actually started playing. */
    private volatile boolean clockStarted = false;

    /** Tracks PTS for chunks whose AVFrame had AV_NOPTS_VALUE. */
    private long pendingNextStartPtsNanos = 0L;

    private float volume = 1.0f;
    private boolean muted = false;
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private volatile boolean closed = false;

    private AudioStream(int streamIndex, double timeBase, AVCodecContext codecCtx, SwrContext swrCtx,
                        AVFrame frame, AVChannelLayout outLayout,
                        BytePointer convertOutBuffer, int convertOutCapacity) {
        this.streamIndex = streamIndex;
        this.timeBase = timeBase;
        this.codecCtx = codecCtx;
        this.swrCtx = swrCtx;
        this.frame = frame;
        this.outLayout = outLayout;
        this.convertOutBuffer = convertOutBuffer;
        this.convertOutCapacity = convertOutCapacity;
    }

    /**
     * Open the audio stream at {@code streamIndex} of {@code formatStream}.
     * The caller is responsible for matching the stream against
     * {@code packet.stream_index()} in their decoder loop.
     *
     * @throws RuntimeException with a descriptive message if any FFmpeg /
     *     resampler init step fails. Caller can choose to continue without
     *     audio in that case.
     */
    public static AudioStream open(AVStream formatStream, int streamIndex) {
        AVCodecParameters params = formatStream.codecpar();
        AVCodec codec = avcodec_find_decoder(params.codec_id());
        if (codec == null) {
            throw new RuntimeException("Audio codec id=" + params.codec_id() + " not found");
        }
        AVCodecContext codecCtx = avcodec_alloc_context3(codec);
        if (avcodec_parameters_to_context(codecCtx, params) < 0) {
            avcodec_free_context(codecCtx);
            throw new RuntimeException("avcodec_parameters_to_context (audio) failed");
        }
        if (avcodec_open2(codecCtx, codec, (AVDictionary) null) < 0) {
            avcodec_free_context(codecCtx);
            throw new RuntimeException("avcodec_open2 (audio) failed");
        }

        AVChannelLayout outLayout = new AVChannelLayout();
        av_channel_layout_default(outLayout, OUT_CHANNELS);

        // swr_alloc_set_opts2 wants a SwrContext** in C. In bytedeco we
        // pass an empty SwrContext and the API populates the underlying
        // pointer. The reference returned through the @ByPtrPtr param
        // is the one we use afterward.
        SwrContext[] swrHolder = new SwrContext[]{ null };
        SwrContext swrCtx = new SwrContext(null);
        int swrInit = swr_alloc_set_opts2(
                swrCtx,
                outLayout, AV_SAMPLE_FMT_S16, OUT_SAMPLE_RATE,
                codecCtx.ch_layout(), codecCtx.sample_fmt(), codecCtx.sample_rate(),
                0, null);
        if (swrInit < 0 || swr_init(swrCtx) < 0) {
            swr_free(swrCtx);
            av_channel_layout_uninit(outLayout);
            avcodec_free_context(codecCtx);
            throw new RuntimeException("swr init failed (rc=" + swrInit + ")");
        }

        AVFrame frame = av_frame_alloc();
        // Generous output buffer — 4× the steady-state to absorb sample-rate
        // upscaling (e.g. 22kHz source → 44.1kHz output produces 2× samples).
        int convertCapacity = BUFFER_BYTES * 4;
        BytePointer convertOutBuffer = new BytePointer(av_malloc(convertCapacity));
        convertOutBuffer.capacity(convertCapacity);

        double timeBase = av_q2d(formatStream.time_base());

        return new AudioStream(streamIndex, timeBase, codecCtx, swrCtx, frame, outLayout,
                convertOutBuffer, convertCapacity);
    }

    public int streamIndex() {
        return streamIndex;
    }

    /**
     * Decode an audio packet and queue its converted PCM for playback.
     * Called from the {@link VideoPlayer} decoder thread. Drops packets
     * silently when the queue is full (back-pressure: render thread will
     * catch up).
     */
    public void processPacket(AVPacket packet) {
        if (closed) return;
        if (avcodec_send_packet(codecCtx, packet) < 0) return;
        while (true) {
            int recv = avcodec_receive_frame(codecCtx, frame);
            if (recv == AVERROR_EAGAIN() || recv == AVERROR_EOF() || recv < 0) break;

            long framePtsTb = frame.best_effort_timestamp();
            long startPtsNanos = (framePtsTb == AV_NOPTS_VALUE)
                    ? pendingNextStartPtsNanos
                    : (long) (framePtsTb * timeBase * 1_000_000_000.0);

            // Bytedeco wants the destination as a PointerPointer for swr_convert.
            // For non-planar S16 stereo, only data plane 0 is filled.
            int outCapSamples = convertOutCapacity / (OUT_CHANNELS * BYTES_PER_SAMPLE);
            PointerPointer<BytePointer> outPlanes = new PointerPointer<>(1);
            outPlanes.put(0, convertOutBuffer);

            int convertedSamples = swr_convert(swrCtx,
                    outPlanes, outCapSamples,
                    frame.extended_data(), frame.nb_samples());

            outPlanes.close();
            if (convertedSamples > 0) {
                long durationNanos = (long) convertedSamples * 1_000_000_000L / OUT_SAMPLE_RATE;
                int bytes = convertedSamples * OUT_CHANNELS * BYTES_PER_SAMPLE;
                if (pendingChunks.size() < MAX_PENDING_CHUNKS) {
                    byte[] copy = new byte[bytes];
                    convertOutBuffer.position(0);
                    convertOutBuffer.get(copy);
                    pendingChunks.offerLast(new PendingChunk(copy, startPtsNanos, durationNanos));
                }
                pendingNextStartPtsNanos = startPtsNanos + durationNanos;
            }
            av_frame_unref(frame);
        }
    }

    /**
     * Render-thread tick. Recycles processed AL buffers, queues pending
     * decoded chunks, ensures the source keeps playing across underruns,
     * and refreshes the clock snapshot used by {@link #audioClockNanos()}.
     */
    public void pump() {
        if (closed || alFailed) return;
        if (!alInitialized) {
            try {
                initAl();
            } catch (Throwable t) {
                alFailed = true;
                return;
            }
        }

        // Recycle buffers AL has finished playing.
        int processed = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_PROCESSED);
        for (int i = 0; i < processed; i++) {
            int bufId = AL10.alSourceUnqueueBuffers(alSource);
            freeAlBuffers.addLast(bufId);
            // Pop the matching head from our PTS queue so the next pump
            // reads sample offset against the right base.
            alQueue.pollFirst();
        }

        // Pump pending chunks into AL.
        while (!freeAlBuffers.isEmpty() && !pendingChunks.isEmpty()) {
            PendingChunk chunk = pendingChunks.pollFirst();
            int bufId = freeAlBuffers.pollFirst();
            ByteBuffer bb = ByteBuffer.allocateDirect(chunk.pcm.length).order(ByteOrder.nativeOrder());
            bb.put(chunk.pcm).flip();
            AL10.alBufferData(bufId, AL10.AL_FORMAT_STEREO16, bb, OUT_SAMPLE_RATE);
            AL10.alSourceQueueBuffers(alSource, bufId);
            alQueue.addLast(new InFlightBuffer(bufId, chunk.startPtsNanos, chunk.durationNanos));
        }

        if (!paused.get()) {
            // Restart on underrun — AL stops the source if its queue runs dry.
            int state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING) {
                int queued = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_QUEUED);
                if (queued > 0) {
                    AL10.alSourcePlay(alSource);
                }
            }
        }

        // Snapshot the clock for cross-thread reads.
        InFlightBuffer head = alQueue.peekFirst();
        if (head != null) {
            int sampleOffset = AL10.alGetSourcei(alSource, AL10.AL_SAMPLE_OFFSET);
            long offsetNanos = (long) sampleOffset * 1_000_000_000L / OUT_SAMPLE_RATE;
            lastClockNanos = head.startPtsNanos + offsetNanos;
            lastClockSampleAtNanos = System.nanoTime();
            if (sampleOffset > 0) clockStarted = true;
        }
    }

    private void initAl() {
        alSource = AL10.alGenSources();
        alBuffers = new int[AL_BUFFER_COUNT];
        AL10.alGenBuffers(alBuffers);
        for (int b : alBuffers) freeAlBuffers.addLast(b);
        AL10.alSourcef(alSource, AL10.AL_GAIN, muted ? 0f : volume);
        AL10.alSourcei(alSource, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE); // 2D, ignore listener position
        alInitialized = true;
    }

    /**
     * Current audio playback position in source-timeline nanoseconds.
     * Thread-safe (volatile snapshot updated on every {@link #pump()},
     * extrapolated forward with wall-clock between pumps for smoothness).
     * Returns 0 until the AL source actually starts producing samples.
     */
    public long audioClockNanos() {
        if (!clockStarted || paused.get() || !alInitialized || alFailed) {
            return lastClockNanos;
        }
        long delta = System.nanoTime() - lastClockSampleAtNanos;
        return lastClockNanos + Math.max(0, delta);
    }

    /** Whether the AL source has actually started producing samples. */
    public boolean isClockStarted() {
        return clockStarted;
    }

    /** Linear gain in [0, 1]. Above 1 amplifies (may clip). */
    public void setVolume(float v) {
        this.volume = Math.max(0f, v);
        applyVolume();
    }

    public float volume() {
        return volume;
    }

    public void setMuted(boolean m) {
        this.muted = m;
        applyVolume();
    }

    public boolean muted() {
        return muted;
    }

    private void applyVolume() {
        if (alInitialized && !alFailed) {
            AL10.alSourcef(alSource, AL10.AL_GAIN, muted ? 0f : volume);
        }
    }

    public void pause() {
        paused.set(true);
        if (alInitialized && !alFailed) {
            AL10.alSourcePause(alSource);
        }
    }

    public void resume() {
        paused.set(false);
        if (alInitialized && !alFailed) {
            AL10.alSourcePlay(alSource);
        }
    }

    @Override
    public void close() {
        closed = true;
        // OpenAL cleanup must run on the render thread (where the context
        // is current). Best-effort: if we're on render thread, do it inline;
        // otherwise the leak is bounded — Minecraft's exit clears the device.
        if (alInitialized && !alFailed) {
            try {
                AL10.alSourceStop(alSource);
                AL10.alDeleteSources(alSource);
                AL10.alDeleteBuffers(alBuffers);
            } catch (Throwable ignored) {
                // AL device might already be torn down.
            }
            alInitialized = false;
        }
        pendingChunks.clear();
        if (frame != null)            { av_frame_free(frame); frame = null; }
        if (swrCtx != null)           { swr_free(swrCtx); swrCtx = null; }
        if (convertOutBuffer != null) { av_free(convertOutBuffer); convertOutBuffer = null; }
        if (codecCtx != null)         { avcodec_free_context(codecCtx); codecCtx = null; }
        if (outLayout != null)        { av_channel_layout_uninit(outLayout); outLayout = null; }
    }
}
