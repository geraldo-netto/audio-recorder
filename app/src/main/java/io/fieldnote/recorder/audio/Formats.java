package io.fieldnote.recorder.audio;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import io.fieldnote.recorder.api.Profile;

final class Formats {
    private Formats() {}

    static String mime(Profile.Format kind) {
        return kind == Profile.Format.AAC ? MediaFormat.MIMETYPE_AUDIO_AAC : MediaFormat.MIMETYPE_AUDIO_OPUS;
    }

    static MediaFormat encoder(Profile p) {
        MediaFormat f = MediaFormat.createAudioFormat(mime(p.format()), p.rate(), 1);
        f.setInteger(MediaFormat.KEY_BIT_RATE, p.kbps() * 1000);
        if (p.format() == Profile.Format.AAC)
            f.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        return f;
    }

    static int container(Profile.Format kind) {
        return kind == Profile.Format.AAC ? MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                : MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG;
    }
}
