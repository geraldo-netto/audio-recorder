package io.fieldnote.recorder.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import io.fieldnote.recorder.R;
import io.fieldnote.recorder.api.Profile;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class ProfilePicker {
    private final Activity activity;
    private final SharedPreferences prefs;
    private final Session session;

    ProfilePicker(Activity activity, SharedPreferences prefs, Session session) {
        this.activity = activity;
        this.prefs = prefs;
        this.session = session;
    }

    private Profile fixed() {
        String key = prefs.getString("profile", "");
        for (Profile p : session.profiles) if (p.key().equals(key)) return p;
        return null;
    }

    List<Profile> candidates() {
        Profile chosen = fixed();
        return chosen == null ? session.profiles : Collections.singletonList(chosen);
    }

    String title() {
        Profile chosen = fixed();
        return chosen == null ? activity.getString(R.string.auto) : format(chosen);
    }

    String detail() {
        if (!session.loaded) return activity.getString(R.string.checking);
        if (session.profiles.isEmpty()) return activity.getString(R.string.no_profile);
        Profile p = session.active == null ? candidates().get(0) : session.active;
        return format(p) + "\n" + engine(p);
    }

    private String format(Profile p) {
        String rate = java.text.NumberFormat.getNumberInstance(Locale.getDefault()).format(p.rate() / 1000.0);
        return activity.getString(R.string.profile_detail, p.format().name(), p.kbps(), rate);
    }

    private String engine(Profile p) {
        return activity.getString(p.accelerated() ? R.string.hardware : R.string.platform) + " · " + p.encoder();
    }

    void show(Runnable refresh) {
        var profiles = session.profiles;
        var labels = new String[profiles.size() + 1];
        labels[0] = activity.getString(R.string.auto);
        for (int i = 0; i < profiles.size(); i++)
            labels[i + 1] = format(profiles.get(i)) + "\n" + engine(profiles.get(i));
        new AlertDialog.Builder(activity).setTitle(R.string.profile)
                .setSingleChoiceItems(labels, profiles.indexOf(fixed()) + 1, (dialog, which) -> {
                    String key = which == 0 ? "" : profiles.get(which - 1).key();
                    prefs.edit().putString("profile", key).apply();
                    dialog.dismiss();
                    refresh.run();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }
}
