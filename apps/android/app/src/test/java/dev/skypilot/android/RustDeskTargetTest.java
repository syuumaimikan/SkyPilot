package dev.skypilot.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class RustDeskTargetTest {
    @Test public void onlyPrivateTailnetTargets() {
        assertEquals("100.67.188.35:21118", RustDeskTarget.normalize(" 100.67.188.35 "));
        assertEquals("pc.tail123.ts.net:21118", RustDeskTarget.normalize("PC.TAIL123.TS.NET:21118"));
        assertEquals("100.64.0.0:65535", RustDeskTarget.normalize("100.64.0.0:65535"));
        assertEquals("100.127.255.255:21118", RustDeskTarget.normalize("100.127.255.255"));
        for (String target : new String[] {null, "", "123456789", "8.8.8.8", "192.168.1.1", "100.63.1.1", "100.128.1.1",
                "100.64.0.256", "100.064.0.1", "evil.ts.net.attacker.test", "pc.ts.net", "-pc.tail.ts.net", "https://pc.tail.ts.net",
                "100.64.0.1:0", "100.64.0.1:65536", "100.64.0.1:", "100.64.0.1:21118?password=secret", "user@pc.tail.ts.net", "pc.tail.ts.net/path"}) {
            assertNull(target, RustDeskTarget.normalize(target));
        }
    }

    @Test public void connectionLinksCannotConfigureRustDeskOrPassPasswords() {
        String link = "rustdesk://connection/new/100.67.188.35:21118";
        assertEquals(link, RustDeskTarget.link("100.67.188.35"));
        assertEquals("100.67.188.35:21118", RustDeskTarget.fromLink(link));
        for (String url : new String[] {null, "rustdesk://password/secret", "rustdesk://config/encoded", link + "?password=secret", link + "#fragment",
                "rustdesk://connection/new/8.8.8.8:21118", "rustdesk://connection/new/100.67.188.35%3A21118", "rustdesk://user@connection/new/100.67.188.35:21118"}) {
            assertNull(url, RustDeskTarget.fromLink(url));
        }
    }
}
