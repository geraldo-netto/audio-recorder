package io.fieldnote.recorder.ui;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.net.Uri;

/** Owns foreground status from admission until the worker has drained and closed the take. */
public final class CaptureService extends Service {
    static final String START = "io.fieldnote.recorder.START";
    static final String STOP = "io.fieldnote.recorder.STOP";
    private final Runnable observe = this::finishIfIdle;
    private Session session;
    private String owner;

    static Intent command(Context context, String action, String take) {
        return new Intent(context, CaptureService.class).setAction(action)
                .setData(new Uri.Builder().scheme("fieldnote").authority("take").appendPath(take).build());
    }

    @Override public void onCreate() {
        super.onCreate();
        session = ((RecorderApp) getApplication()).session;
        session.subscribe(observe);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Uri data = intent == null ? null : intent.getData();
        if (data != null && session.take != null && session.take.equals(data.getLastPathSegment())) {
            if (START.equals(intent.getAction())) admit(session.take);
            else if (STOP.equals(intent.getAction())) session.stop();
        }
        finishIfIdle();
        return START_NOT_STICKY;
    }

    private void admit(String id) {
        owner = id;
        session.begin(() -> startForeground(1, CaptureNotification.create(this, id)));
    }

    private void finishIfIdle() {
        if (session.phase != Session.Phase.IDLE) return;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() {
        session.unsubscribe(observe);
        if (owner != null && owner.equals(session.take)) session.stop();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
