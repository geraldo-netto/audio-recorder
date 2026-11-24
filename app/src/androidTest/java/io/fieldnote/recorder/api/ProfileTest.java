package io.fieldnote.recorder.api;

import io.fieldnote.recorder.domain.ProfilePolicy;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ProfileTest {
    private final Profile soft = new Profile("cpu", Profile.Format.AAC, false, 48000, 128);
    private final Profile fast = new Profile("chip", Profile.Format.OPUS, true, 48000, 64);

    @Test public void automaticPrefersAccelerationAndDropsDuplicates() {
        assertEquals(Arrays.asList(fast, soft), ProfilePolicy.ranked(Arrays.asList(soft, fast, soft)));
        assertEquals("OPUS/48000/64/chip", fast.key());
    }

    @Test public void stableRankingUsesQualityRateAndEncoder() {
        var low = new Profile("b", Profile.Format.AAC, false, 44100, 64);
        var high = new Profile("b", Profile.Format.AAC, false, 44100, 128);
        var other = new Profile("a", Profile.Format.AAC, false, 44100, 128);
        assertEquals(Arrays.asList(soft, other, high, low), ProfilePolicy.ranked(Arrays.asList(low, high, soft, other)));
    }

    @Test public void rejectsOutOfRangeAndMissingIdentity() {
        for (int bad : new int[]{Integer.MIN_VALUE, -1, 0, 44101, 47999, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> new Profile("cpu", Profile.Format.AAC, false, bad, 128));
        for (int bad : new int[]{Integer.MIN_VALUE, -1, 0, 63, 65, 127, 129, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> new Profile("cpu", Profile.Format.AAC, false, 44100, bad));
        for (String bad : new String[]{null, "", " "})
            assertThrows(IllegalArgumentException.class, () -> new Profile(bad, Profile.Format.AAC, false, 44100, 128));
        assertThrows(IllegalArgumentException.class, () -> new Profile("cpu", null, false, 44100, 128));
    }
}
