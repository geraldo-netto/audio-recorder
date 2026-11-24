package io.fieldnote.recorder.audio;

import android.media.MediaCodec;
import android.media.MediaMuxer;
import io.fieldnote.recorder.api.Profile;
import java.io.FileDescriptor;
import java.io.IOException;

/** Owns the muxer. A codec output buffer is borrowed only until its synchronous write returns. */
final class Output implements AutoCloseable {
    private final MediaMuxer muxer;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private int track = -1;
    private boolean written;

    Output(FileDescriptor fd, Profile.Format kind) throws IOException {
        muxer = new MediaMuxer(fd, Formats.container(kind));
    }

    int drain(MediaCodec codec, long waitUs) {
        int slot = codec.dequeueOutputBuffer(info, waitUs);
        if (slot == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            track = muxer.addTrack(codec.getOutputFormat());
            muxer.start();
        }
        if (slot < 0) return slot;
        try {
            if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                muxer.writeSampleData(track, codec.getOutputBuffer(slot), info);
                written = true;
            }
            return (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 ? END : slot;
        } finally {
            codec.releaseOutputBuffer(slot, false);
        }
    }

    static final int END = Integer.MIN_VALUE;

    void finish(long frames) throws IOException {
        if (!written || frames == 0) throw new IOException("Recording was too short to save");
        muxer.stop();
    }

    @Override public void close() {
        muxer.release();
    }
}
