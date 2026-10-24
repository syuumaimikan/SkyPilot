package dev.skypilot.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class ServerUrlTest {
    @Test public void tailscaleModeRequiresHttpsAndRealSuffix() {
        assertTrue(ServerUrl.isTailnetOrigin("https://pc.example.ts.net"));
        assertTrue(ServerUrl.isTailnetOrigin("https://PC.EXAMPLE.TS.NET:8443"));
        for (String url : new String[] {null, "http://pc.example.ts.net", "https://pc.ts.net.evil.test", "https://user@pc.ts.net", "http://192.168.1.1", "bad uri"}) {
            assertFalse(ServerUrl.isTailnetOrigin(url));
        }
    }
    @Test public void normalizesHttpsOrigins() {
        assertEquals("https://example.com", ServerUrl.normalize("  HTTPS://EXAMPLE.com:443/ ", false));
        assertEquals("https://example.com:8443", ServerUrl.normalize("https://example.com:8443", false));
    }

    @Test public void rejectsCredentialsAndNonRootUrls() {
        for (String url : new String[] {"https://user:pass@example.com", "https://example.com/api",
                "https://example.com?token=secret", "https://example.com/#fragment", "https://example.com/%2F",
                "https://example.com:0", "https://example.com:99999", "javascript:alert(1)", "file:///data", "example.com", ""}) {
            assertNull(url, ServerUrl.normalize(url, true));
        }
    }

    @Test public void releaseAlwaysRequiresHttps() {
        for (String url : new String[] {"http://localhost:3000", "http://10.0.2.2:3000", "http://192.168.1.5:3000"}) {
            assertNull(ServerUrl.normalize(url, false));
            assertNotNull(ServerUrl.normalize(url, true));
        }
    }

    @Test public void debugHttpIsRestrictedToLocalNetworks() {
        for (String host : new String[] {"172.16.0.1", "172.31.255.255", "127.0.0.1", "[::1]"}) {
            assertNotNull(ServerUrl.normalize("http://" + host, true));
        }
        for (String host : new String[] {"example.com", "8.8.8.8", "172.32.0.1", "192.169.1.1", "10.0.0.256", "010.0.0.1"}) {
            assertNull(ServerUrl.normalize("http://" + host, true));
        }
    }

    @Test public void navigationRequiresExactOrigin() {
        String base = "https://example.com";
        assertTrue(ServerUrl.sameOrigin(base, "https://EXAMPLE.COM:443/roadmap?x=1#node"));
        for (String url : new String[] {"http://example.com/", "https://example.com:8443/", "https://example.com.evil.test/",
                "https://evil.test/", "https://user@example.com", "data:text/html,hello", "//example.com/", "not a uri"}) {
            assertFalse(url, ServerUrl.sameOrigin(base, url));
        }
        assertFalse(ServerUrl.sameOrigin(null, base));
        assertFalse(ServerUrl.sameOrigin(base, null));
    }
}
