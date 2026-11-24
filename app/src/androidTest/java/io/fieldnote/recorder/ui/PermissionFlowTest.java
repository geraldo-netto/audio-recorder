package io.fieldnote.recorder.ui;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.Manifest;
import android.os.Build;
import android.os.SystemClock;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import io.fieldnote.recorder.R;
import org.junit.Test;

public class PermissionFlowTest {
    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    @Test public void notificationConsentIsOptionalAndNeverBlocksMicrophoneConsent() {
        assertArrayEquals(new String[]{Manifest.permission.RECORD_AUDIO},
                RecordingPermissions.request(null, false, false, false));
        assertArrayEquals(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                RecordingPermissions.request(Manifest.permission.POST_NOTIFICATIONS, true, false, false));
        assertEquals(0, RecordingPermissions.request(Manifest.permission.POST_NOTIFICATIONS, true, false, true).length);
        assertTrue(RecordingPermissions.microphoneGranted(true, new String[]{Manifest.permission.POST_NOTIFICATIONS},
                new int[]{-1}));
        assertFalse(RecordingPermissions.microphoneGranted(true, new String[]{Manifest.permission.RECORD_AUDIO},
                new int[0]));
    }

    @Test public void firstTakeRecordsWithMicrophoneConsentAlone() {
        RecorderApp app = ApplicationProvider.getApplicationContext();
        try (var scene = ActivityScenario.launch(RecorderActivity.class)) {
            assertTrue(device.wait(Until.hasObject(By.res(app.getPackageName(), "toggle").enabled(true)), 10000));
            device.waitForIdle();
            onView(withId(R.id.toggle)).perform(click());
            answerPrompts(true, initialPromptCount());
            assertTrue(device.wait(Until.hasObject(By.text(app.getString(R.string.recording))), 10000));
            scene.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            SystemClock.sleep(650);
            scene.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
            scene.onActivity(a -> assertEquals(Session.Phase.RECORDING, app.session.phase));
            stopAndVerify(scene, app);
        }
    }

    private void stopAndVerify(ActivityScenario<RecorderActivity> scene, RecorderApp app) {
        onView(withId(R.id.toggle)).perform(click());
        assertTrue(device.wait(Until.hasObject(By.text(app.getString(R.string.latest))), 10000));
        var clip = app.session.clip;
        assertNotNull(clip);
        try {
            verifySavedState(scene);
        } finally {
            app.getContentResolver().delete(android.net.Uri.parse(clip.location()), null, null);
        }
    }

    private void verifySavedState(ActivityScenario<RecorderActivity> scene) {
        scene.onActivity(a -> {
            TextView status = a.findViewById(R.id.status);
            assertEquals(a.getText(R.string.ready), status.getText());
        });
    }

    // FN-9: permission denial must keep its recovery action across recreation.
    @Test public void deniedMicrophoneKeepsRecoveryActionAcrossRecreation() {
        RecorderApp app = ApplicationProvider.getApplicationContext();
        try (var scene = ActivityScenario.launch(RecorderActivity.class)) {
            assertTrue(device.wait(Until.hasObject(By.res(app.getPackageName(), "toggle").enabled(true)), 10000));
            device.waitForIdle();
            onView(withId(R.id.toggle)).perform(click());
            answerPrompts(false, initialPromptCount());
            var settings = By.res(app.getPackageName(), "permission_settings");
            assertTrue(device.wait(Until.hasObject(settings), 10000));
            scene.recreate();
            scene.onActivity(a -> {
                assertTrue(a.findViewById(R.id.permission_settings).isShown());
                TextView notice = a.findViewById(R.id.notice);
                assertEquals(a.getText(R.string.permission_denied), notice.getText());
            });
            onView(withId(R.id.toggle)).perform(click());
            answerPrompts(true, 1);
            assertTrue(device.wait(Until.hasObject(By.text(app.getString(R.string.recording))), 10000));
            SystemClock.sleep(650);
            stopAndVerify(scene, app);
        }
    }

    private int initialPromptCount() {
        return Build.VERSION.SDK_INT >= 33 ? 2 : 1;
    }

    private void answerPrompts(boolean allowMic, int count) {
        var buttons = By.res(java.util.regex.Pattern.compile(".*:id/permission_(allow_foreground_only|allow|deny)_button"));
        for (int i = 0; i < count; i++) {
            assertTrue("System permission dialog", device.wait(Until.hasObject(buttons), 10000));
            var mic = device.findObject(By.res(java.util.regex.Pattern.compile(".*:id/permission_allow_foreground_only_button")));
            if (Build.VERSION.SDK_INT < 30) mic = device.findObject(By.res(java.util.regex.Pattern.compile(".*:id/permission_allow_button")));
            var button = mic == null || !allowMic ? device.findObject(By.res(java.util.regex.Pattern.compile(".*:id/permission_deny_button"))) : mic;
            assertNotNull("Expected permission action", button);
            button.clickAndWait(Until.newWindow(), 2500);
        }
    }
}
