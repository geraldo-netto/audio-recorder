package io.fieldnote.recorder.audio;

import android.media.MediaCodec;
import android.os.SystemClock;
import java.io.IOException;
import java.util.function.BooleanSupplier;

/** Input timestamps count captured PCM frames. At most one codec input slot is held. */
final class Pump {
    private final Input input;
    private final int rate;
    private int slot = -1;
    private long frames;
    private boolean ended;

    Pump(Input input, int rate) {
        this.input = input;
        this.rate = rate;
    }

    private boolean feed(boolean stop) throws IOException {
        if (ended) return false;
        if (slot < 0) slot = input.codec.dequeueInputBuffer(0);
        if (slot < 0) return false;
        int bytes = stop ? 0 : input.read(input.codec.getInputBuffer(slot), rate);
        if (bytes == 0 && !stop) return false;
        input.codec.queueInputBuffer(slot, 0, bytes, frames * 1_000_000L / rate,
                stop ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
        frames += bytes / 2;
        slot = -1;
        ended = stop;
        return true;
    }

    void run(Output output, BooleanSupplier stop) throws IOException {
        long progress = SystemClock.elapsedRealtime();
        while (true) {
            boolean queued = feed(stop.getAsBoolean());
            int event = output.drain(input.codec, 5000);
            if (event == Output.END) break;
            if (queued || event != MediaCodec.INFO_TRY_AGAIN_LATER) progress = SystemClock.elapsedRealtime();
            if (SystemClock.elapsedRealtime() - progress > 10000) throw new IOException("Audio device stopped responding");
        }
        output.finish(frames);
    }
}
