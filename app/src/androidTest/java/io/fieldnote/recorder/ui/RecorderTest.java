package io.fieldnote.recorder.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.*;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.Button;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.rule.GrantPermissionRule;
import io.fieldnote.recorder.R;
import io.fieldnote.recorder.api.Profile;
import io.fieldnote.recorder.api.Recording;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.Rule;
import org.junit.Test;

public class RecorderTest {
    @Rule public GrantPermissionRule microphone = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO);
    private final RecorderApp app = ApplicationProvider.getApplicationContext();

    private void until(ActivityScenario<RecorderActivity> scenario, BooleanSupplier predicate) {
        long deadline = SystemClock.elapsedRealtime() + 15000;
        boolean[] satisfied = {false};
        while (!satisfied[0] && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity(a -> satisfied[0] = predicate.getAsBoolean());
            SystemClock.sleep(30);
        }
        assertTrue("Expected recorder state", satisfied[0]);
    }

    @Test public void toggleSurvivesRecreationAndPublishesDecodableAudio() throws Exception {
        try (var scenario = ActivityScenario.launch(RecorderActivity.class)) {
            until(scenario, () -> app.session.loaded);
            onView(withId(R.id.profile)).perform(click());
            onView(withText(R.string.auto)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
            onView(withId(R.id.toggle)).perform(click());
            until(scenario, () -> app.session.phase == Session.Phase.RECORDING);
            scenario.recreate();
            scenario.onActivity(a -> {
                Button button = a.findViewById(R.id.toggle);
                assertEquals(a.getString(R.string.stop), button.getText());
                assertIcon(button, true);
                assertFalse(a.findViewById(R.id.profile).isEnabled());
                assertTrue(a.findViewById(R.id.root).getKeepScreenOn());
            });
            SystemClock.sleep(900);
            onView(withId(R.id.toggle)).perform(click());
            verifySaved(scenario);
        }
    }

    @Test public void backgroundCaptureStopsFromNotification() throws Exception {
        try (var scenario = ActivityScenario.launch(RecorderActivity.class)) {
            until(scenario, () -> app.session.loaded);
            Profile p = app.session.profiles.get(app.session.profiles.size() - 1);
            app.prefs.edit().putString("profile", p.key()).commit();
            onView(withId(R.id.toggle)).perform(click());
            until(scenario, () -> app.session.phase == Session.Phase.RECORDING);
            assertEquals(p, app.session.active);
            scenario.moveToState(Lifecycle.State.CREATED);
            SystemClock.sleep(1600);
            var manager = app.getSystemService(android.app.NotificationManager.class);
            var notifications = manager.getActiveNotifications();
            assertEquals("One active recording notification", 1, notifications.length);
            var stop = notifications[0].getNotification().actions[0].actionIntent;
            stop.send();
            scenario.moveToState(Lifecycle.State.RESUMED);
            verifySaved(scenario, 1500);
            assertEquals(0, manager.getActiveNotifications().length);
            onView(withId(R.id.toggle)).perform(click());
            until(scenario, () -> app.session.phase == Session.Phase.RECORDING);
            stop.send();
            SystemClock.sleep(550);
            scenario.onActivity(a -> assertEquals(Session.Phase.RECORDING, app.session.phase));
            onView(withId(R.id.toggle)).perform(click());
            verifySaved(scenario);
        } finally {
            app.prefs.edit().remove("profile").commit();
        }
    }

    // FN-5: a second recorder task becomes visible before the first activity stops.
    @Test public void anotherRecorderTaskKeepsTheActiveTake() throws Exception {
        try (var first = ActivityScenario.launch(RecorderActivity.class)) {
            until(first, () -> app.session.loaded);
            onView(withId(R.id.toggle)).perform(click());
            until(first, () -> app.session.phase == Session.Phase.RECORDING);
            first.moveToState(Lifecycle.State.CREATED);
            var intent = new android.content.Intent(app, RecorderActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            try (ActivityScenario<RecorderActivity> second = ActivityScenario.launch(intent)) {
                SystemClock.sleep(600);
                second.onActivity(a -> assertEquals(Session.Phase.RECORDING, app.session.phase));
                onView(withId(R.id.toggle)).perform(click());
                verifySaved(second);
            }
        }
    }

    // FN-4: MediaStore can change independently of this process.
    @Test public void latestRecordingReflectsRenameAndDeletion() throws Exception {
        try (var scene = ActivityScenario.launch(RecorderActivity.class)) {
            until(scene, () -> app.session.loaded);
            onView(withId(R.id.toggle)).perform(click());
            until(scene, () -> app.session.phase == Session.Phase.RECORDING);
            SystemClock.sleep(650);
            onView(withId(R.id.toggle)).perform(click());
            until(scene, () -> app.session.phase == Session.Phase.IDLE && app.session.clip != null);
            Uri uri = Uri.parse(app.session.clip.location());
            scene.moveToState(Lifecycle.State.CREATED);
            try {
                var update = new android.content.ContentValues();
                String name = "Renamed-" + app.session.clip.name();
                update.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
                assertEquals(1, app.getContentResolver().update(uri, update, null, null));
                scene.moveToState(Lifecycle.State.RESUMED);
                until(scene, () -> app.session.clip != null && name.equals(app.session.clip.name()));
            } finally {
                app.getContentResolver().delete(uri, null, null);
            }
            until(scene, () -> app.session.clip == null || !uri.toString().equals(app.session.clip.location()));
        }
    }

    private void verifySaved(ActivityScenario<RecorderActivity> scenario) throws Exception {
        verifySaved(scenario, 400);
    }

    private void verifySaved(ActivityScenario<RecorderActivity> scenario, int minimumMs) throws Exception {
        until(scenario, () -> app.session.phase == Session.Phase.IDLE);
        assertEquals("", app.session.message);
        Recording.Clip clip = app.session.clip;
        assertNotNull(clip);
        Uri uri = Uri.parse(clip.location());
        try {
            checkTrack(uri);
            checkPlayback(uri, minimumMs);
            scenario.onActivity(a -> {
                assertTrue(a.findViewById(R.id.play).isEnabled());
                Button toggle = a.findViewById(R.id.toggle);
                assertEquals(a.getString(R.string.record), toggle.getText());
                assertIcon(toggle, false);
            });
        } finally {
            app.getContentResolver().delete(uri, null, null);
        }
    }

    private void assertIcon(Button button, boolean square) {
        var icon = button.getCompoundDrawablesRelative()[0];
        var pixels = Bitmap.createBitmap(icon.getBounds().width(), icon.getBounds().height(), Bitmap.Config.ARGB_8888);
        try {
            icon.draw(new Canvas(pixels));
            assertTrue(Color.alpha(pixels.getPixel(pixels.getWidth() / 2, pixels.getHeight() / 2)) > 0);
            int corner = pixels.getWidth() / 5;
            assertEquals(square, Color.alpha(pixels.getPixel(corner, corner)) > 0);
        } finally {
            pixels.recycle();
        }
    }

    private void checkTrack(Uri uri) throws Exception {
        var extractor = new MediaExtractor();
        try {
            extractor.setDataSource(app, uri, null);
            assertEquals(1, extractor.getTrackCount());
            assertEquals(1, extractor.getTrackFormat(0).getInteger(MediaFormat.KEY_CHANNEL_COUNT));
            android.util.Log.i("FieldnoteTest", "Decoded track " + extractor.getTrackFormat(0));
        } finally {
            extractor.release();
        }
    }

    private void checkPlayback(Uri uri, int minimumMs) throws Exception {
        var player = new MediaPlayer();
        try {
            player.setDataSource(app, uri);
            player.setVolume(0, 0);
            player.prepare();
            assertTrue(player.getDuration() >= minimumMs);
            awaitPlayback(player);
        } finally {
            player.release();
        }
    }

    private void awaitPlayback(MediaPlayer player) throws Exception {
        var complete = new CountDownLatch(1);
        player.setOnCompletionListener(p -> complete.countDown());
        player.start();
        assertTrue("Playback completion", complete.await(12, TimeUnit.SECONDS));
    }

    // FN-7: an unprepared native player delivers an asynchronous error on start.
    @Test public void playbackOracleRejectsNativeError() {
        var player = new MediaPlayer();
        try {
            player.reset();
            assertThrows(AssertionError.class, () -> awaitPlayback(player));
        } finally {
            player.release();
        }
    }
}
