package io.fieldnote.recorder.ui;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import io.fieldnote.recorder.api.Profile;
import io.fieldnote.recorder.api.Recording;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** One main-thread controller survives rotation. The worker only reads the volatile stop signal. */
final class Session {
    enum Phase { IDLE, STARTING, RECORDING, SAVING }
    private final Recording backend;
    private final Set<Runnable> listeners = new LinkedHashSet<>();
    private List<Profile> pending;
    String take;
    final Handler main = new Handler(Looper.getMainLooper());
    List<Profile> profiles = Collections.emptyList();
    Phase phase = Phase.IDLE;
    Profile active;
    long since;
    String message = "";
    Recording.Clip clip;
    private volatile boolean stopping;
    private final Runnable onPublished;
    boolean loaded;
    void subscribe(Runnable listener) { listeners.add(listener); }
    void unsubscribe(Runnable listener) { listeners.remove(listener); }
    void changed() { listeners.forEach(Runnable::run); }

    Session(Recording backend, Runnable onPublished) {
        this.backend = backend;
        this.onPublished = onPublished;
    }

    void discover() {
        new Thread(() -> {
            try {
                var found = backend.profiles();
                main.post(() -> { profiles = found; loaded = true; changed(); });
            } catch (RuntimeException e) {
                main.post(() -> { loaded = true; fail("Could not read device encoders"); });
            }
        }, "fieldnote-catalog").start();
    }

    void request(List<Profile> candidates, Consumer<String> launch) {
        String id = reserve(candidates);
        if (id == null) return;
        try {
            launch.accept(id);
        } catch (IllegalStateException | SecurityException e) {
            fail("Open Fieldnote and check microphone access");
        }
    }

    String reserve(List<Profile> candidates) {
        if (phase != Phase.IDLE || candidates.isEmpty()) return null;
        take = UUID.randomUUID().toString();
        pending = new ArrayList<>(candidates);
        stopping = false;
        phase = Phase.STARTING;
        message = "";
        changed();
        return take;
    }

    void begin(Runnable promote) {
        var candidates = pending;
        if (candidates == null) return;
        pending = null;
        try {
            promote.run();
        } catch (IllegalStateException | SecurityException e) {
            fail("Android could not start microphone recording");
            return;
        }
        new Thread(() -> capture(candidates), "fieldnote-capture").start();
    }

    private void capture(List<Profile> candidates) {
        try {
            backend.capture(candidates, () -> stopping,
                    p -> main.post(() -> began(p)));
            main.post(() -> { onPublished.run(); settle(""); });
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
        changed();
    }

    void stop() {
        if (phase != Phase.RECORDING && phase != Phase.STARTING) return;
        stopping = true;
        phase = Phase.SAVING;
        changed();
    }

    void fail(String reason) {
        settle("Couldn’t record. " + reason + ".");
    }

    private void settle(String result) {
        active = null;
        take = null;
        pending = null;
        phase = Phase.IDLE;
        message = result;
        changed();
    }
}
