package io.fieldnote.recorder.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import java.util.ArrayList;
import java.util.Arrays;

final class RecordingPermissions {
    private RecordingPermissions() {}

    static String[] request(String notification, boolean microphone, boolean notifications, boolean alreadyAsked) {
        var missing = new ArrayList<String>(2);
        if (!microphone) missing.add(Manifest.permission.RECORD_AUDIO);
        if (notification != null && !notifications && !alreadyAsked) missing.add(notification);
        return missing.toArray(new String[0]);
    }

    static boolean microphoneGranted(boolean allowed, String[] names, int[] grants) {
        int mic = Arrays.asList(names).indexOf(Manifest.permission.RECORD_AUDIO);
        return allowed && (mic < 0 || (mic < grants.length && grants[mic] == PackageManager.PERMISSION_GRANTED));
    }
}
