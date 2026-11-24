package io.fieldnote.recorder.audio;

import io.fieldnote.recorder.domain.ProfilePolicy;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import io.fieldnote.recorder.api.Profile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class Catalog {
    private Catalog() {}

    static List<Profile> read() {
        var result = new ArrayList<Profile>();
        for (var codec : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (codec.isEncoder() && !codec.isAlias()) addCodec(result, codec);
        }
        return ProfilePolicy.ranked(result);
    }

    private static void addCodec(List<Profile> result, MediaCodecInfo codec) {
        for (var kind : Profile.Format.values()) {
            String mime = Formats.mime(kind);
            if (!Arrays.asList(codec.getSupportedTypes()).contains(mime)) continue;
            var caps = codec.getCapabilitiesForType(mime);
            for (int rate : new int[]{48000, 44100})
                addRate(result, codec, kind, caps, rate);
        }
    }

    private static void addRate(List<Profile> result, MediaCodecInfo codec, Profile.Format kind,
                                MediaCodecInfo.CodecCapabilities caps, int rate) {
        if (Input.bufferSize(rate) <= 0) return;
        for (int kbps : new int[]{128, 64}) {
            var p = new Profile(codec.getName(), kind, codec.isHardwareAccelerated(), rate, kbps);
            if (caps.isFormatSupported(Formats.encoder(p))) result.add(p);
        }
    }
}
