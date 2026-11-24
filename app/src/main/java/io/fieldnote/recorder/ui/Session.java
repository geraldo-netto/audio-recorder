package io.fieldnote.recorder.ui;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import io.fieldnote.recorder.api.Profile;
import io.fieldnote.recorder.api.Recording;
import java.io.IOException;
import java.util.List;

/** One main-thread controller survives rotation. The worker only reads the volatile stop signal. */
final class Session {
    enum Phase { IDLE, STARTING, RECORDING, SAVING }
    final Recording backend;
    final Handler main = new Handler(Looper.getMainLooper());
    List<Profile> profiles = java.util.Collections.emptyList();
    Phase phase = Phase.IDLE;
    Profile active;
    long since;
    String message = "";
    Recording.Clip clip;
    private volatile boolean stopping;
    private final java.util.function.Consumer<Recording.Clip> remember;
    boolean loaded;
    Runnable changed = () -> {};

    Session(Recording backend, java.util.function.Consumer<Recording.Clip> remember) {
        this.backend = backend;
        this.remember = remember;
    }

    void discover() {
        new Thread(() -> {
            try {
                var found = backend.profiles();
                main.post(() -> { profiles = found; loaded = true; changed.run(); });
            } catch (RuntimeException e) {
                main.post(() -> { loaded = true; fail("Could not read device encoders"); });
            }
        }, "fieldnote-catalog").start();
    }

    void start(List<Profile> candidates) {
        if (phase != Phase.IDLE || candidates.isEmpty()) return;
        stopping = false;
        phase = Phase.STARTING;
        message = "";
        changed.run();
        new Thread(() -> capture(candidates), "fieldnote-capture").start();
    }

    private void capture(List<Profile> candidates) {
        try {
            var saved = backend.capture(candidates, () -> stopping,
                    p -> main.post(() -> began(p)));
            main.post(() -> { clip = saved; remember.accept(saved); settle(""); });
        } catch (IOException | RuntimeException e) {
            android.util.Log.w("Fieldnote", "Recording ended", e);
            main.post(() -> fail(e instanceof IOException ? e.getMessage() : "Check microphone access and free storage"));
        }
    }

    private void began(Profile p) {
        if (phase != Phase.STARTING) return;
        active = p;
        since = SystemClock.elapsedRealtime();
        phase = Phase.RECORDING;
        changed.run();
    }

    void stop() {
        if (phase != Phase.RECORDING && phase != Phase.STARTING) return;
        stopping = true;
        phase = Phase.SAVING;
        changed.run();
    }

    private void fail(String reason) {
        settle("Couldn’t record. " + reason + ".");
    }

    private void settle(String result) {
        active = null;
        phase = Phase.IDLE;
        message = result;
        changed.run();
    }
}
