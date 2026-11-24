package io.fieldnote.recorder.audio;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import io.fieldnote.recorder.api.Profile;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Sole owner of the mic and named encoder, confined to the capture worker. */
final class Input implements AutoCloseable {
    final AudioRecord mic;
    final MediaCodec codec;

    private Input(AudioRecord mic, MediaCodec codec) {
        this.mic = mic;
        this.codec = codec;
    }

    static int bufferSize(int rate) {
        return AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    }

    @SuppressLint("MissingPermission") // Activity requests RECORD_AUDIO immediately before admission.
    static Input open(Profile p) throws IOException {
        var mic = new AudioRecord(MediaRecorder.AudioSource.MIC, p.rate(), AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, Math.max(bufferSize(p.rate()), p.rate() / 5));
        try {
            var codec = MediaCodec.createByCodecName(p.encoder());
            var input = new Input(mic, codec);
            try {
                input.start(p);
                return input;
            } catch (RuntimeException e) {
                codec.release();
                throw e;
            }
        } catch (IOException | RuntimeException e) {
            mic.release();
            throw e;
        }
    }

    private void start(Profile p) {
        codec.configure(Formats.encoder(p), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.start();
        mic.startRecording();
        if (mic.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
            throw new IllegalStateException("Microphone unavailable");
    }

    int read(ByteBuffer target, int rate) throws IOException {
        int size = Math.min(target.remaining() / 2, rate / 50) * 2;
        int count = mic.read(target, size, AudioRecord.READ_NON_BLOCKING);
        if (count < 0) throw new IOException("Microphone returned no audio");
        return count;
    }

    @Override public void close() {
        try {
            mic.release();
        } finally {
            codec.release();
        }
    }
}
