package io.fieldnote.recorder.domain;

import io.fieldnote.recorder.api.Profile;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Stateless ranking of the finite candidate list supplied by the device catalog. */
public final class ProfilePolicy {
    private ProfilePolicy() {}

    /** Prefer vendor-reported acceleration, then balanced mono, AAC, and 48 kHz. */
    public static List<Profile> ranked(List<Profile> profiles) {
        var order = Comparator.comparing(Profile::accelerated).reversed()
                .thenComparingInt(p -> -p.kbps()).thenComparing(Profile::format)
                .thenComparingInt(p -> -p.rate()).thenComparing(Profile::encoder);
        return profiles.stream().distinct().sorted(order).collect(Collectors.toList());
    }
}
