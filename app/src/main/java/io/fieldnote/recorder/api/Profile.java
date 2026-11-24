package io.fieldnote.recorder.api;

/** One advertised encoder configuration. The encoder identity is part of the selection. */
public record Profile(String encoder, Format format, boolean accelerated, int rate, int kbps) {
    public enum Format { AAC, OPUS }

    public Profile {
        if (encoder == null || encoder.trim().isEmpty() || format == null)
            throw new IllegalArgumentException("Missing encoder identity");
        if (rate != 48000 && rate != 44100)
            throw new IllegalArgumentException("Unsupported sample rate");
        if (kbps != 64 && kbps != 128)
            throw new IllegalArgumentException("Unsupported bit rate");
    }

    public String key() {
        return format.name() + "/" + rate + "/" + kbps + "/" + encoder;
    }
}
