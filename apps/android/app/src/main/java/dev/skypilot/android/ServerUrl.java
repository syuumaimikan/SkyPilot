package dev.skypilot.android;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** The app connects to one explicitly selected Web + API origin, never a raw API URL. */
final class ServerUrl {
    private ServerUrl() {}

    static String normalize(String input, boolean debug) {
        try {
            URI uri = new URI(input.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !uri.getRawPath().equals("/"))
                    || uri.getPort() == 0 || uri.getPort() > 65535) return null;
            scheme = scheme.toLowerCase(Locale.ROOT);
            host = host.toLowerCase(Locale.ROOT);
            if (!scheme.equals("https") && !(debug && scheme.equals("http") && isLocalHost(host))) return null;
            int port = uri.getPort();
            if ((scheme.equals("https") && port == 443) || (scheme.equals("http") && port == 80)) port = -1;
            return new URI(scheme, null, host, port, null, null, null).toASCIIString();
        } catch (URISyntaxException | NullPointerException e) {
            return null;
        }
    }

    static boolean sameOrigin(String server, String url) {
        if (server == null || url == null) return false;
        try {
            URI base = new URI(server);
            URI target = new URI(url);
            return target.getRawUserInfo() == null
                    && base.getScheme().equalsIgnoreCase(target.getScheme())
                    && base.getHost().equalsIgnoreCase(target.getHost())
                    && effectivePort(base) == effectivePort(target);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    static boolean isTailnetOrigin(String server) {
        if (server == null) return false;
        try {
            URI uri = new URI(server);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getHost().toLowerCase(Locale.ROOT).endsWith(".ts.net")
                    && uri.getRawUserInfo() == null;
        } catch (URISyntaxException e) { return false; }
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() != -1 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static boolean isLocalHost(String host) {
        if (host.equals("localhost") || host.equals("[::1]")) return true;
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            if (!parts[i].matches("0|[1-9][0-9]{0,2}")) return false;
            octets[i] = Integer.parseInt(parts[i]);
            if (octets[i] > 255) return false;
        }
        return octets[0] == 127 || octets[0] == 10
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168);
    }
}
