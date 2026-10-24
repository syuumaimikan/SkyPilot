package dev.skypilot.android;

import java.net.URI;
import java.util.Locale;

/** Small, testable trust boundary around a release description. */
final class UpdateSpec {
    static final long MAX_BYTES = 64L * 1024 * 1024;
    final int versionCode, minSdk;
    final String versionName, packageName, path, sha256;
    final long bytes;

    UpdateSpec(int code, String name, String pkg, String path, String hash, long bytes, int minSdk) {
        if (code <= 0 || minSdk < 26 || minSdk > 100 || name == null || !name.matches("[0-9A-Za-z._-]{1,40}")
                || !"dev.skypilot.android".equals(pkg) || path == null
                || !path.matches("/api/android/SkyPilot-[0-9A-Za-z._-]+\\.apk")
                || hash == null || !hash.matches("[0-9a-fA-F]{64}") || bytes <= 0 || bytes > MAX_BYTES) {
            throw new IllegalArgumentException("Invalid Android release");
        }
        versionCode = code; versionName = name; packageName = pkg; this.path = path;
        sha256 = hash.toLowerCase(Locale.ROOT); this.bytes = bytes; this.minSdk = minSdk;
    }

    String downloadUrl(String server) {
        String origin = ServerUrl.normalize(server, false);
        if (origin == null) throw new IllegalArgumentException("Updates require HTTPS");
        String target = origin + path;
        if (!ServerUrl.sameOrigin(origin, target)) throw new IllegalArgumentException("Cross-origin release");
        return URI.create(target).toASCIIString();
    }

    boolean newerThan(int current, int sdk) { return versionCode > current && minSdk <= sdk; }
}
