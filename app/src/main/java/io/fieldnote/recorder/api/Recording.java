package io.fieldnote.recorder.api;

import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Blocking worker-thread boundary. No Android types cross it. */
public interface Recording {
    record Clip(String location, String name) {}

    /** Advertised mono PCM16 combinations, ordered by automatic preference (hardware acceleration first). An empty list disables capture. */
    List<Profile> profiles();

    /**
     * Try candidates in order until input/codec startup succeeds. Fixed selections supply one.
     * Emit the admitted profile once. Poll stop, drain end-of-stream, and return a published clip.
     * Reject empty takes and failed finalization. The worker owns native handles through cleanup.
     */
    Clip capture(List<Profile> candidates, BooleanSupplier stop, Consumer<Profile> started)
            throws IOException;
}
