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
        var session = new Session(broken, () -> fail());
        run(() -> {
            session.subscribe(finished::countDown);
            session.discover();
        });
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertTrue(session.loaded);
        assertTrue(session.message.contains("encoders"));
    }

    @Test public void failureAndEarlyStopSettleWithoutPublishing() throws Exception {
        var session = new Session(broken, () -> fail());
        run(() -> {
            session.subscribe(() -> { if (session.phase == Session.Phase.IDLE) finished.countDown(); });
            session.reserve(Collections.emptyList());
            session.stop();
            assertEquals(Session.Phase.IDLE, session.phase);
            session.reserve(Collections.singletonList(profile));
            session.begin(() -> {});
            session.stop();
            session.reserve(Collections.singletonList(profile));
            assertEquals(Session.Phase.SAVING, session.phase);
        });
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertTrue(session.message.contains("storage full"));
        assertNull(session.active);
        assertNull(session.clip);
    }

    @Test public void launchAndPromotionRejectionReleaseAdmission() {
        var session = new Session(broken, () -> fail());
        var candidates = Collections.singletonList(profile);
        run(() -> {
            session.request(candidates, take -> { throw new IllegalStateException(); });
            assertEquals(Session.Phase.IDLE, session.phase);
            assertTrue(session.message.contains("Open Fieldnote"));
            session.request(candidates, take -> session.begin(() -> { throw new SecurityException(); }));
            assertEquals(Session.Phase.IDLE, session.phase);
            assertNull(session.take);
            assertTrue(session.message.contains("could not start"));
            assertNull(new CaptureService().onBind(new android.content.Intent()));
        });
    }

    private void run(Runnable work) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(work);
    }
}
