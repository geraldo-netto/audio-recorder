package io.fieldnote.recorder.ui;

import static org.junit.Assert.*;
import androidx.test.platform.app.InstrumentationRegistry;
import io.fieldnote.recorder.api.Profile;
import io.fieldnote.recorder.api.Recording;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.Test;

public class SessionTest {
    private final Profile profile = new Profile("unavailable", Profile.Format.AAC, false, 48000, 128);
    private final CountDownLatch finished = new CountDownLatch(1);
    private final Recording broken = new Recording() {
        @Override public List<Profile> profiles() {
            throw new IllegalStateException("codec service unavailable");
        }
        @Override public Clip capture(List<Profile> candidates, BooleanSupplier stop, Consumer<Profile> started)
                throws IOException {
            started.accept(profile);
            throw new IOException("storage full");
        }
    };

    @Test public void discoveryFailureIsVisible() throws Exception {
        var session = new Session(broken, c -> fail());
        run(() -> {
            session.changed = finished::countDown;
            session.discover();
        });
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertTrue(session.loaded);
        assertTrue(session.message.contains("encoders"));
    }

    @Test public void failureAndEarlyStopSettleWithoutPublishing() throws Exception {
        var session = new Session(broken, c -> fail());
        run(() -> {
            session.changed = () -> { if (session.phase == Session.Phase.IDLE) finished.countDown(); };
            session.start(Collections.emptyList());
            session.stop();
            assertEquals(Session.Phase.IDLE, session.phase);
            session.start(Collections.singletonList(profile));
            session.stop();
            session.start(Collections.singletonList(profile));
            assertEquals(Session.Phase.SAVING, session.phase);
        });
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertTrue(session.message.contains("storage full"));
        assertNull(session.active);
        assertNull(session.clip);
    }

    private void run(Runnable work) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(work);
    }
}
