package io.fieldnote.recorder.audio;

import android.content.ContentResolver;
import io.fieldnote.recorder.api.Profile;
import io.fieldnote.recorder.api.Recording;
import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class AndroidRecording implements Recording {
    private final ContentResolver resolver;

    public AndroidRecording(ContentResolver resolver) {
        this.resolver = resolver;
    }

    @Override public List<Profile> profiles() {
        return Catalog.read();
    }

    @Override public Clip capture(List<Profile> candidates, BooleanSupplier stop, Consumer<Profile> started)
            throws IOException {
        Exception last = null;
        for (Profile p : candidates) {
            if (stop.getAsBoolean()) throw new IOException("Recording canceled");
            Input input;
            try {
                input = Input.open(p);
            } catch (IllegalStateException | IllegalArgumentException | IOException e) {
                last = e;
                continue;
            }
            try (input; var destination = Destination.create(resolver, p.format())) {
                try (var file = destination.open(); var output = new Output(file.getFileDescriptor(), p.format())) {
                    started.accept(p);
                    new Pump(input, p.rate()).run(output, stop);
                }
                return destination.publish();
            }
        }
        throw new IOException("Microphone or selected encoder unavailable", last);
    }
}
