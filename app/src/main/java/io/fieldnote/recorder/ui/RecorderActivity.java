package io.fieldnote.recorder.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;
import io.fieldnote.recorder.R;

public final class RecorderActivity extends Activity {
    private static final int MIC = 11;
    private final Runnable refresh = this::render;
    private RecorderApp app;
    private Session session;
    private ProfilePicker picker;
    private boolean visible;
    private boolean denied;
    private String localNotice = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.recorder);
        app = (RecorderApp) getApplication();
        session = app.session;
        picker = new ProfilePicker(this, app.prefs, session);
        if (state != null) {
            denied = state.getBoolean("denied");
            localNotice = state.getString("notice", "");
        }
        wireControls();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("denied", denied);
        state.putString("notice", localNotice);
    }

    private void wireControls() {
        findViewById(R.id.root).setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        findViewById(R.id.toggle).setOnClickListener(v -> toggle());
        findViewById(R.id.profile).setOnClickListener(v -> picker.show(this::render));
        findViewById(R.id.play).setOnClickListener(v -> play());
        findViewById(R.id.permission_settings).setOnClickListener(v -> startActivity(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
    }

    @Override protected void onStart() {
        super.onStart();
        visible = true;
        if (allowed() && denied) { denied = false; localNotice = ""; }
        app.show(refresh);
    }

    @Override protected void onStop() {
        visible = false;
        app.hide(refresh);
        super.onStop();
    }

    private boolean allowed() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void toggle() {
        if (session.phase != Session.Phase.IDLE) {
            session.stop();
            return;
        }
        denied = false;
        localNotice = "";
        String notification = android.os.Build.VERSION.SDK_INT >= 33 ? Manifest.permission.POST_NOTIFICATIONS : null;
        boolean notificationsGranted = notification == null || checkSelfPermission(notification) == PackageManager.PERMISSION_GRANTED;
        String[] needed = RecordingPermissions.request(notification, allowed(), notificationsGranted,
                app.prefs.getBoolean("asked-notifications", false));
        if (needed.length == 0) {
            app.record(picker.candidates());
            return;
        }
        if (java.util.Arrays.asList(needed).contains(notification))
            app.prefs.edit().putBoolean("asked-notifications", true).apply();
        requestPermissions(needed, MIC);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request != MIC || !visible || grants.length == 0) return;
        if (RecordingPermissions.microphoneGranted(allowed(), permissions, grants)) {
            app.record(picker.candidates());
        } else {
            denied = true;
            localNotice = getString(R.string.permission_denied);
            render();
        }
    }

    private void play() {
        var intent = new Intent(Intent.ACTION_VIEW, Uri.parse(session.clip.location()));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            localNotice = getString(R.string.no_player);
            render();
        }
    }

    private void render() {
        boolean idle = session.phase == Session.Phase.IDLE;
        findViewById(R.id.root).setKeepScreenOn(!idle);
        renderTransport(idle);
        Button profile = findViewById(R.id.profile);
        profile.setEnabled(idle && !session.profiles.isEmpty());
        profile.setText(picker.title());
        ((TextView) findViewById(R.id.detail)).setText(picker.detail());
        renderNotice();
        renderClip(idle);
    }

    private void renderTransport(boolean idle) {
        Button button = findViewById(R.id.toggle);
        button.setText(idle ? R.string.record : R.string.stop);
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(idle ? R.drawable.ic_record : R.drawable.ic_stop, 0, 0, 0);
        int tint = getColor(idle ? R.color.accent : R.color.stop_color);
        button.setBackgroundTintList(ColorStateList.valueOf(tint));
        button.setEnabled(!session.profiles.isEmpty() && session.phase != Session.Phase.SAVING);
        ((TextView) findViewById(R.id.status)).setText(status());
        renderTime();
    }

    private int status() {
        return switch (session.phase) {
            case IDLE -> idleStatus();
            case STARTING -> R.string.starting;
            case RECORDING -> R.string.recording;
            case SAVING -> R.string.saving;
        };
    }

    private int idleStatus() {
        if (!session.loaded) return R.string.checking;
        return session.profiles.isEmpty() ? R.string.no_profile : R.string.ready;
    }

    private void renderTime() {
        Chronometer timer = findViewById(R.id.timer);
        if (session.phase == Session.Phase.RECORDING) {
            timer.setBase(session.since);
            timer.start();
        } else {
            timer.stop();
            if (session.phase != Session.Phase.SAVING) timer.setBase(SystemClock.elapsedRealtime());
        }
    }

    private void renderNotice() {
        String text = localNotice.isEmpty() ? session.message : localNotice;
        TextView notice = findViewById(R.id.notice);
        notice.setText(text);
        notice.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        findViewById(R.id.permission_settings).setVisibility(denied ? View.VISIBLE : View.GONE);
    }

    private void renderClip(boolean idle) {
        findViewById(R.id.last_clip).setVisibility(session.clip == null ? View.GONE : View.VISIBLE);
        if (session.clip == null) return;
        ((TextView) findViewById(R.id.clip_name)).setText(session.clip.name());
        findViewById(R.id.play).setEnabled(idle);
    }
}
