package io.fieldnote.recorder.audio;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import io.fieldnote.recorder.api.Profile;
import io.fieldnote.recorder.api.Recording.Clip;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** The row stays pending until the muxer and descriptor have both closed. */
final class Destination implements AutoCloseable {
    private final ContentResolver resolver;
    private final Uri uri;
    private final String name;
    private boolean published;

    private Destination(ContentResolver resolver, Uri uri, String name) {
        this.resolver = resolver;
        this.uri = uri;
        this.name = name;
    }

    static Destination create(ContentResolver resolver, Profile.Format kind) throws IOException {
        boolean aac = kind == Profile.Format.AAC;
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss-SSS"));
        String name = "Fieldnote " + stamp + (aac ? ".m4a" : ".ogg");
        var values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Audio.Media.MIME_TYPE, aac ? "audio/mp4" : "audio/ogg");
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Fieldnote/");
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
        if (uri == null) throw new IOException("Audio storage unavailable");
        return new Destination(resolver, uri, name);
    }

    ParcelFileDescriptor open() throws IOException {
        var fd = resolver.openFileDescriptor(uri, "rw");
        if (fd == null) throw new IOException("Could not open the recording");
        return fd;
    }

    Clip publish() throws IOException {
        var values = new ContentValues();
        values.put(MediaStore.Audio.Media.IS_PENDING, 0);
        if (resolver.update(uri, values, null, null) != 1) throw new IOException("Could not publish recording");
        published = true;
        return new Clip(uri.toString(), name);
    }

    @Override public void close() {
        if (!published) resolver.delete(uri, null, null);
    }
}
