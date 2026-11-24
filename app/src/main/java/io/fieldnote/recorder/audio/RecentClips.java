package io.fieldnote.recorder.audio;

import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.Media;
import io.fieldnote.recorder.api.Recording.Clip;
import java.util.function.Consumer;

/** The loader queries off-thread, observes changes, and closes its cursor on reset. */
public final class RecentClips extends CursorLoader {
    private final ForceLoadContentObserver changes = new ForceLoadContentObserver();

    public RecentClips(Context context, Consumer<Clip> changed) {
        super(context, Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                new String[]{Media._ID, Media.DISPLAY_NAME}, Media.OWNER_PACKAGE_NAME + "=? AND " + Media.IS_PENDING + "=0",
                new String[]{context.getPackageName()}, Media._ID + " DESC");
        registerListener(0, (loader, cursor) -> changed.accept(first(cursor)));
    }

    @Override protected void onStartLoading() {
        getContext().getContentResolver().registerContentObserver(MediaStore.AUTHORITY_URI, true, changes);
        super.onStartLoading();
    }

    @Override protected void onReset() {
        getContext().getContentResolver().unregisterContentObserver(changes);
        super.onReset();
    }

    private Clip first(Cursor rows) {
        if (rows == null || !rows.moveToFirst()) return null;
        var uri = ContentUris.withAppendedId(getUri(), rows.getLong(0));
        return new Clip(uri.toString(), rows.getString(1));
    }
}
