package org.amnezia.awg.backend;

import java.security.SecureRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rewrites a single marked endpoint in an ephemeral userspace configuration. */
final class EndpointPortRandomizer {
    private static final int MARKER_PORT = 6437;
    private static final int PORT_COUNT = 300;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern ENDPOINT_LINE = Pattern.compile(
            "endpoint=(\\[[^\\[\\]\\s]+\\]|[^:\\[\\]\\s]+):(\\d{1,5})");

    private EndpointPortRandomizer() { }

    static String rewrite(final String config) {
        int markedPortStart = -1;
        int markedPortEnd = -1;
        int lineStart = 0;

        while (lineStart < config.length()) {
            final int newline = config.indexOf('\n', lineStart);
            final int lineEnd = newline == -1 ? config.length() : newline;
            final int contentEnd = lineEnd > lineStart && config.charAt(lineEnd - 1) == '\r'
                    ? lineEnd - 1 : lineEnd;

            if (config.startsWith("endpoint=", lineStart)) {
                final Matcher matcher = ENDPOINT_LINE.matcher(config.substring(lineStart, contentEnd));
                // An unexpected endpoint means no rewriting anywhere in the config.
                if (!matcher.matches())
                    return config;

                final String host = matcher.group(1);
                if (host.startsWith("[") && !host.contains(":"))
                    return config;

                final int originalPort = Integer.parseInt(matcher.group(2));
                if (originalPort < 1 || originalPort > 65535)
                    return config;

                if (originalPort == MARKER_PORT) {
                    if (markedPortStart != -1)
                        return config; // Ambiguous: two or more marked endpoints.
                    markedPortStart = lineStart + matcher.start(2);
                    markedPortEnd = lineStart + matcher.end(2);
                }
            }

            if (newline == -1)
                break;
            lineStart = newline + 1;
        }

        if (markedPortStart == -1)
            return config;

        final int port = portForIndex(RANDOM.nextInt(PORT_COUNT));
        return config.substring(0, markedPortStart) + port + config.substring(markedPortEnd);
    }

    /** Deterministic mapping, kept separately testable. */
    static int portForIndex(final int index) {
        if (index < 0 || index >= PORT_COUNT)
            throw new IllegalArgumentException("Port index out of range");
        return index < 150 ? 6500 + index : 8300 + index - 150;
    }
}
