package dev.codex.aaparkedyoutube;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class TrustedUrlPolicy {
    private static final String[] TRUSTED_HOSTS = {
            "youtube.com",
            "youtu.be",
            "google.com"
    };

    private TrustedUrlPolicy() {
    }

    static boolean isTrustedUrl(String rawUrl) {
        URI uri = parse(rawUrl);
        if (uri == null) {
            return false;
        }

        if ("about".equalsIgnoreCase(uri.getScheme())) {
            return "blank".equalsIgnoreCase(uri.getSchemeSpecificPart());
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }

        String host = normalizedHost(uri);
        if (host == null) {
            return false;
        }

        for (String trustedHost : TRUSTED_HOSTS) {
            if (isHostOrSubdomain(host, trustedHost)) {
                return true;
            }
        }
        return false;
    }

    static boolean isYouTubeUrl(String rawUrl) {
        URI uri = parse(rawUrl);
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }

        String host = normalizedHost(uri);
        return host != null
                && (isHostOrSubdomain(host, "youtube.com")
                || isHostOrSubdomain(host, "youtu.be"));
    }

    private static URI parse(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return null;
        }

        try {
            return new URI(rawUrl);
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private static String normalizedHost(URI uri) {
        String host = uri.getHost();
        return host == null ? null : host.toLowerCase(Locale.ROOT);
    }

    private static boolean isHostOrSubdomain(String host, String trustedHost) {
        return host.equals(trustedHost) || host.endsWith("." + trustedHost);
    }
}
