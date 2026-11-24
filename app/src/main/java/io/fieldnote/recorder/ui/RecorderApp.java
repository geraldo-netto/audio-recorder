package io.fieldnote.recorder.ui;

import android.app.Application;
import android.content.SharedPreferences;
import io.fieldnote.recorder.api.Recording.Clip;
import io.fieldnote.recorder.audio.AndroidRecording;

public final class RecorderApp extends Application {
    Session session;
    SharedPreferences prefs;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("recorder", MODE_PRIVATE);
        session = new Session(new AndroidRecording(getContentResolver()), this::remember);
        String uri = prefs.getString("latest-uri", "");
        if (!uri.isEmpty()) session.clip = new Clip(uri, prefs.getString("latest-name", ""));
        session.discover();
    }

    private void remember(Clip clip) {
        prefs.edit().putString("latest-uri", clip.location()).putString("latest-name", clip.name()).apply();
    }
}
