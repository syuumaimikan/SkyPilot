package dev.skypilot.android;

import java.net.URI;
import java.util.Locale;

/** Only direct connections to the private tailnet; never credentials or configuration links. */
final class RustDeskTarget {
    private RustDeskTarget() {}

    static String normalize(String input) {
        if (input == null) return null;
        String target = input.trim().toLowerCase(Locale.ROOT);
        String[] parts = target.split(":", -1);
        if (parts.length > 2 || parts.length == 0) return null;
        String host = parts[0];
        boolean tailnet = host.length() <= 253 && host.endsWith(".ts.net")
                && host.substring(0, host.length() - 7).contains(".")
                && host.matches("[a-z0-9]+(?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9]+(?:[a-z0-9-]*[a-z0-9])?)+");
        String[] octets = host.split("\\.", -1);
        if (!tailnet && octets.length == 4) {
            int[] values = new int[4];
            for (int i = 0; i < 4; i++) {
                if (!octets[i].matches("0|[1-9][0-9]{0,2}")) return null;
                values[i] = Integer.parseInt(octets[i]);
                if (values[i] > 255) return null;
            }
            tailnet = values[0] == 100 && values[1] >= 64 && values[1] <= 127;
        }
        if (!tailnet) return null;
        String port = parts.length == 2 ? parts[1] : "21118";
        if (!port.matches("[1-9][0-9]{0,4}")) return null;
        int number = Integer.parseInt(port);
        return number <= 65535 ? host + ":" + number : null;
    }

    static String link(String target) {
        String normalized = normalize(target);
        return normalized == null ? null : "rustdesk://connection/new/" + normalized;
    }

    static String fromLink(String url) {
        try {
            URI uri = new URI(url);
            if (!"rustdesk".equals(uri.getScheme()) || !"connection".equals(uri.getRawAuthority())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getRawPath() == null || !uri.getRawPath().startsWith("/new/")) return null;
            String target = normalize(uri.getRawPath().substring(5));
            return target != null && url.equals(link(target)) ? target : null;
        } catch (Exception e) { return null; }
    }
}
