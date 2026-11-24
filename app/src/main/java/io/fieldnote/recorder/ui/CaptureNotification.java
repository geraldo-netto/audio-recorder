package io.fieldnote.recorder.ui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import io.fieldnote.recorder.R;

final class CaptureNotification {
    private static final String CHANNEL = "recording";
    private CaptureNotification() {}

    static Notification create(Context context, String take) {
        var channel = new NotificationChannel(CHANNEL, context.getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        var open = new Intent(context, RecorderActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        var stop = CaptureService.command(context, CaptureService.STOP, take);
        var action = PendingIntent.getService(context, 0, stop, PendingIntent.FLAG_IMMUTABLE);
        var builder = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle(context.getText(R.string.recording))
                .setContentText(context.getText(R.string.notification_detail)).setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE))
                .addAction(new Notification.Action.Builder(null, context.getText(R.string.stop), action).build());
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return builder.build();
    }
}
