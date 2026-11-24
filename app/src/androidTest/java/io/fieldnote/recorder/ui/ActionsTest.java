package io.fieldnote.recorder.ui;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.Instrumentation;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.net.Uri;
import android.view.View;
import android.view.WindowInsets;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import io.fieldnote.recorder.R;
import io.fieldnote.recorder.api.Recording;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;

public class ActionsTest {
    @Rule public GrantPermissionRule mic = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO);
    private final RecorderApp app = ApplicationProvider.getApplicationContext();
    private final Instrumentation runner = InstrumentationRegistry.getInstrumentation();

    @Test public void permissionDenialOffersSettingsAndHandlesInsets() {
        var sent = new AtomicReference<Intent>();
        var monitor = intercept(sent, () -> false);
        try (var scene = ActivityScenario.launch(RecorderActivity.class)) {
            scene.onActivity(a -> {
                a.onRequestPermissionsResult(11, new String[]{Manifest.permission.RECORD_AUDIO},
                        new int[]{PackageManager.PERMISSION_DENIED});
                View settings = a.findViewById(R.id.permission_settings);
                assertEquals(View.VISIBLE, settings.getVisibility());
                settings.performClick();
                assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, sent.get().getAction());
                View root = a.findViewById(R.id.root);
                root.dispatchApplyWindowInsets(new WindowInsets.Builder().setSystemWindowInsets(Insets.of(3, 5, 7, 11)).build());
                assertEquals(11, root.getPaddingBottom());
            });
        } finally {
            runner.removeMonitor(monitor);
        }
    }

    @Test public void playerReceivesOnlyReadAccessAndFailureIsVisible() {
        var original = app.session.clip;
        var sent = new AtomicReference<Intent>();
        var reject = new java.util.concurrent.atomic.AtomicBoolean();
        var monitor = intercept(sent, reject::get);
        try (var scene = ActivityScenario.launch(RecorderActivity.class)) {
            scene.onActivity(a -> {
                app.session.clip = new Recording.Clip("content://media/external/audio/media/123", "Test clip");
                app.session.changed.run();
                a.findViewById(R.id.play).performClick();
                assertEquals(Uri.parse(app.session.clip.location()), sent.get().getData());
                assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, sent.get().getFlags());
                reject.set(true);
                a.findViewById(R.id.play).performClick();
                TextView notice = a.findViewById(R.id.notice);
                assertEquals(a.getString(R.string.no_player), notice.getText());
            });
        } finally {
            runner.removeMonitor(monitor);
            runner.runOnMainSync(() -> app.session.clip = original);
        }
    }

    // FN-6: a disabled transport must not announce readiness.
    @Test public void idleStatusMatchesCatalogAvailability() {
        try (var scene = ActivityScenario.launch(RecorderActivity.class)) {
            scene.onActivity(a -> {
                var original = app.session.profiles;
                boolean loaded = app.session.loaded;
                try {
                    app.session.profiles = java.util.Collections.emptyList();
                    app.session.loaded = false;
                    app.session.changed.run();
                    TextView status = a.findViewById(R.id.status);
                    assertEquals(a.getText(R.string.checking), status.getText());
                    app.session.loaded = true;
                    app.session.changed.run();
                    assertEquals(a.getText(R.string.no_profile), status.getText());
                    assertFalse(a.findViewById(R.id.toggle).isEnabled());
                } finally {
                    app.session.loaded = loaded;
                    app.session.profiles = original;
                    app.session.changed.run();
                }
            });
        }
    }

    private Instrumentation.ActivityMonitor intercept(AtomicReference<Intent> sent, java.util.function.BooleanSupplier reject) {
        var monitor = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                if (intent.getComponent() != null) return null;
                sent.set(intent);
                if (reject.getAsBoolean()) throw new ActivityNotFoundException();
                return new Instrumentation.ActivityResult(0, null);
            }
        };
        runner.addMonitor(monitor);
        return monitor;
    }
}
