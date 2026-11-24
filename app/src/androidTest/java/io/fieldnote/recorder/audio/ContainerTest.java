package io.fieldnote.recorder.audio;

import static org.junit.Assert.*;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.SystemClock;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import io.fieldnote.recorder.api.Profile;
import java.io.File;
import java.io.FileOutputStream;
import java.util.EnumSet;
import org.junit.Test;

/** Isolates container finalization from microphone and lock-screen state using silent PCM. */
public class ContainerTest {
    @Test public void eachAdvertisedFormatMuxesReadableSamples() throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        var formats = EnumSet.noneOf(Profile.Format.class);
        for (var profile : Catalog.read()) {
            if (!formats.add(profile.format())) continue;
            File clip = File.createTempFile("fieldnote-container", ".audio", app.getCacheDir());
            try {
                encode(clip, profile);
                verify(clip, profile);
            } finally {
                assertTrue(clip.delete());
            }
        }
        assertFalse(formats.isEmpty());
        var result = new android.os.Bundle();
        result.putString("stream", "Verified containers for " + formats + "\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, result);
    }

    private void encode(File clip, Profile p) throws Exception {
        MediaCodec encoder = MediaCodec.createByCodecName(p.encoder());
        try (var fd = new FileOutputStream(clip); var output = new Output(fd.getFD(), p.format())) {
            encoder.configure(Formats.encoder(p), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            feedAndDrain(encoder, output, p.rate());
        } finally {
            encoder.release();
        }
    }

    private void feedAndDrain(MediaCodec encoder, Output output, int rate) throws Exception {
        int chunks = 0;
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (chunks <= 16 && feed(encoder, chunks, rate)) chunks++;
            if (output.drain(encoder, 5000) == Output.END) {
                output.finish(16L * 480);
                return;
            }
        }
        fail("Encoder did not produce end-of-stream");
    }

    private boolean feed(MediaCodec encoder, int chunks, int rate) {
        int slot = encoder.dequeueInputBuffer(0);
        if (slot < 0) return false;
        int size = chunks == 16 ? 0 : 960;
        encoder.getInputBuffer(slot).put(new byte[size]);
        encoder.queueInputBuffer(slot, 0, size, chunks * 480_000_000L / rate,
                size == 0 ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
        return true;
    }

    private void verify(File clip, Profile p) throws Exception {
        var extractor = new MediaExtractor();
        try {
            extractor.setDataSource(clip.toString());
            assertEquals(1, extractor.getTrackCount());
            var actual = extractor.getTrackFormat(0);
            assertEquals(Formats.mime(p.format()), actual.getString(MediaFormat.KEY_MIME));
            assertEquals(p.rate(), actual.getInteger(MediaFormat.KEY_SAMPLE_RATE));
            extractor.selectTrack(0);
            assertTrue(extractor.getSampleSize() > 0);
        } finally {
            extractor.release();
        }
    }
}
