package dev.skypilot.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateSpecTest {
    private final String hash = "ab".repeat(32);
    private UpdateSpec spec(String path, String checksum, long bytes) { return new UpdateSpec(4, "0.4.0", "dev.skypilot.android", path, checksum, bytes, 26); }
    @Test public void onlyNewCompatibleVersions() {
        UpdateSpec release = spec("/api/android/SkyPilot-0.4.0.apk", hash, 100);
        assertTrue(release.newerThan(3, 26));
        assertFalse(release.newerThan(4, 36));
        assertFalse(release.newerThan(5, 36));
        assertFalse(release.newerThan(3, 25));
        assertEquals("https://pc.tail.ts.net:9443/api/android/SkyPilot-0.4.0.apk", release.downloadUrl("https://pc.tail.ts.net:9443"));
    }
    @Test public void rejectUntrustedPaths() {
        for (String path : new String[]{"https://evil.test/a.apk", "//evil.test/a.apk", "/api/android/../a.apk", "/api/android/%2e%2e/a.apk", "/api/android/a.apk?token=x", "/api/android/a.apk", "file:///x.apk"}) {
            assertThrows(IllegalArgumentException.class, () -> spec(path, hash, 100));
        }
    }
    @Test public void enforceSizeIdentityAndDigest() {
        assertThrows(IllegalArgumentException.class, () -> spec("/api/android/SkyPilot-x.apk", "bad", 100));
        assertThrows(IllegalArgumentException.class, () -> spec("/api/android/SkyPilot-x.apk", hash, 0));
        assertThrows(IllegalArgumentException.class, () -> spec("/api/android/SkyPilot-x.apk", hash, UpdateSpec.MAX_BYTES + 1));
        assertThrows(IllegalArgumentException.class, () -> new UpdateSpec(4, "0.4.0", "other.app", "/api/android/SkyPilot-x.apk", hash, 100, 26));
    }
    @Test public void updatesRequireHttpsEvenInDebug() {
        UpdateSpec release = spec("/api/android/SkyPilot-x.apk", hash, 100);
        assertThrows(IllegalArgumentException.class, () -> release.downloadUrl("http://192.168.1.1:3000"));
        assertThrows(IllegalArgumentException.class, () -> release.downloadUrl("https://user@pc.ts.net"));
    }
}
