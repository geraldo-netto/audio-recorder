package io.fieldnote.recorder.audio;

import static org.junit.Assert.*;
import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import androidx.test.core.app.ApplicationProvider;
import io.fieldnote.recorder.api.Profile;
import java.io.IOException;
import java.util.Collections;
import org.junit.Test;

public class BoundaryTest {
    private final Context app = ApplicationProvider.getApplicationContext();
    private final AndroidRecording recording = new AndroidRecording(app.getContentResolver());

    @Test public void cancelledAndMissingCandidatesDoNotAdmit() {
        var p = new Profile("missing", Profile.Format.AAC, false, 48000, 128);
        assertThrows(IOException.class, () -> recording.capture(Collections.singletonList(p), () -> true, x -> fail()));
        assertThrows(IOException.class, () -> recording.capture(Collections.emptyList(), () -> false, x -> fail()));
    }

    @org.junit.Rule public androidx.test.rule.GrantPermissionRule microphone =
            androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO);

    @Test public void rejectedEncoderReleasesInputAndProducesNoClip() {
        var p = new Profile("invalid.encoder.fieldnote", Profile.Format.AAC, false, 48000, 128);
        assertThrows(IOException.class, () -> recording.capture(Collections.singletonList(p), () -> false, x -> fail()));
        assertEquals(android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG, Formats.container(Profile.Format.OPUS));
    }

    @Test public void decoderCannotBeAdmittedAsAnEncoder() {
        var aac = android.media.MediaFormat.createAudioFormat("audio/mp4a-latm", 48000, 1);
        String decoder = new android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
                .findDecoderForFormat(aac);
        assertNotNull(decoder);
        var p = new Profile(decoder, Profile.Format.AAC, false, 48000, 128);
        assertThrows(IOException.class, () -> recording.capture(Collections.singletonList(p), () -> false, x -> fail()));
    }

    @Test public void advertisedDefaultIdentifiesAnActualEncoder() throws Exception {
        var profiles = recording.profiles();
        assertFalse(profiles.isEmpty());
        Profile first = profiles.get(0);
        var codec = android.media.MediaCodec.createByCodecName(first.encoder());
        try {
            assertTrue(codec.getCodecInfo().isEncoder());
            assertEquals(codec.getCodecInfo().isHardwareAccelerated(), first.accelerated());
            var result = new android.os.Bundle();
            result.putString("stream", "Automatic " + first + "; advertised profiles=" + profiles.size() + "\n");
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(0, result);
        } finally {
            codec.release();
        }
    }

    @Test public void closingPendingDestinationDiscardsRow() throws Exception {
        int before = pending();
        try (var d = Destination.create(app.getContentResolver(), Profile.Format.AAC); var fd = d.open()) {
            assertTrue(fd.getFd() >= 0);
            assertEquals(before + 1, pending());
        }
        assertEquals(before, pending());
    }

    // FN-3: stop relinquishes the microphone before the sink is closed or published.
    @Test public void stopEndsNativeCaptureBeforeFileCleanup() throws Exception {
        try (var screen = androidx.test.core.app.ActivityScenario.launch(io.fieldnote.recorder.ui.RecorderActivity.class)) {
            Profile p = recording.profiles().get(0);
            try (var input = Input.open(p); var row = Destination.create(app.getContentResolver(), p.format());
                 var fd = row.open(); var output = new Output(fd.getFileDescriptor(), p.format())) {
                long end = android.os.SystemClock.elapsedRealtime() + 600;
                new Pump(input, p.rate()).run(output, () -> android.os.SystemClock.elapsedRealtime() >= end);
                assertEquals(android.media.AudioRecord.RECORDSTATE_STOPPED, input.mic.getRecordingState());
            }
        }
    }

    private int pending() {
        String filter = "is_pending=1 AND relative_path=?";
        try (Cursor rows = app.getContentResolver().query(MediaStore.setIncludePending(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI),
                new String[]{"_id"}, filter, new String[]{"Music/Fieldnote/"}, null)) {
            assertNotNull(rows);
            return rows.getCount();
        }
    }
}
