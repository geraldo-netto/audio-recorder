package io.fieldnote.recorder.ui;

import android.app.Application;
import android.content.SharedPreferences;
import io.fieldnote.recorder.api.Profile;
import io.fieldnote.recorder.audio.AndroidRecording;
import io.fieldnote.recorder.audio.RecentClips;
import java.util.List;

public final class RecorderApp extends Application {
    Session session;
    SharedPreferences prefs;
    private RecentClips recent;
    private int screens;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("recorder", MODE_PRIVATE);
        session = new Session(new AndroidRecording(getContentResolver()), () -> recent.onContentChanged());
        recent = new RecentClips(this, clip -> {
            session.clip = clip;
            session.changed();
        });
        session.discover();
    }

    void show(Runnable render) {
        session.subscribe(render);
        if (++screens == 1) {
            session.clip = null;
            recent.startLoading();
        }
        render.run();
    }

    void hide(Runnable render) {
        session.unsubscribe(render);
        if (--screens == 0) recent.reset();
    }

    void record(List<Profile> profiles) {
        session.request(profiles, take ->
                startForegroundService(CaptureService.command(this, CaptureService.START, take)));
    }
}
