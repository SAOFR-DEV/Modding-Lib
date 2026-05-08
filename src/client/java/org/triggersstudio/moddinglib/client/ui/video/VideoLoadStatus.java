package org.triggersstudio.moddinglib.client.ui.video;

/**
 * Three-state load envelope used by {@code Components.Video(String, ...)} to
 * keep the screen responsive while {@link VideoPlayer#open(String)} runs on
 * a background thread.
 *
 * <p>Drives the {@code Dynamic} subtree: {@code LOADING} renders a skeleton,
 * {@code ERROR} a small error label, {@code READY} the actual VideoComponent
 * wrapping the resolved player.
 */
public final class VideoLoadStatus {

    public enum State { LOADING, READY, ERROR }

    public final State state;
    public final VideoPlayer player; // non-null when READY
    public final String error;       // non-null when ERROR

    private VideoLoadStatus(State state, VideoPlayer player, String error) {
        this.state = state;
        this.player = player;
        this.error = error;
    }

    public static VideoLoadStatus loading() {
        return new VideoLoadStatus(State.LOADING, null, null);
    }

    public static VideoLoadStatus ready(VideoPlayer player) {
        return new VideoLoadStatus(State.READY, player, null);
    }

    public static VideoLoadStatus error(String message) {
        return new VideoLoadStatus(State.ERROR, null, message != null ? message : "(unknown)");
    }
}
