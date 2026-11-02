package org.amnezia.awg.backend;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EndpointPortRandomizerTest {
    @Test
    public void mapsAll300IndicesToDistinctAllowedPorts() {
        final Set<Integer> ports = new HashSet<>();
        for (int i = 0; i < 300; ++i) {
            final int port = EndpointPortRandomizer.portForIndex(i);
            assertTrue(inAllowedRange(port));
            assertTrue(ports.add(port));
        }
        assertEquals(300, ports.size());
        assertEquals(6500, EndpointPortRandomizer.portForIndex(0));
        assertEquals(6649, EndpointPortRandomizer.portForIndex(149));
        assertEquals(8300, EndpointPortRandomizer.portForIndex(150));
        assertEquals(8449, EndpointPortRandomizer.portForIndex(299));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeIndex() {
        EndpointPortRandomizer.portForIndex(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOutOfRangeIndex() {
        EndpointPortRandomizer.portForIndex(300);
    }

    @Test
    public void rewritesOnlyMarkedPortAndPreservesOtherProperties() {
        final String config = "private_key=not-a-real-key\n"
                + "jc=5\ns1=12\nreplace_peers=true\n"
                + "public_key=not-a-real-peer-key\n"
                + "allowed_ip=0.0.0.0/0\nendpoint=203.0.113.12:6437\n"
                + "persistent_keepalive_interval=25-35\n";
        for (int i = 0; i < 100; ++i) {
            final String output = EndpointPortRandomizer.rewrite(config);
            assertTrue(output.startsWith(config.substring(0, config.indexOf("6437"))));
            assertTrue(output.endsWith("\npersistent_keepalive_interval=25-35\n"));
            assertTrue(inAllowedRange(extractPort(output)));
        }
        assertTrue(config.contains("endpoint=203.0.113.12:6437"));
    }

    @Test
    public void supportsBracketedIpv6() {
        final String output = EndpointPortRandomizer.rewrite("endpoint=[2001:db8::1]:6437\n");
        assertTrue(output.startsWith("endpoint=[2001:db8::1]:"));
        assertTrue(inAllowedRange(extractPort(output)));
    }

    @Test
    public void preservesCrlf() {
        final String output = EndpointPortRandomizer.rewrite("public_key=abc\r\nendpoint=203.0.113.12:6437\r\n");
        assertTrue(output.startsWith("public_key=abc\r\nendpoint=203.0.113.12:"));
        assertTrue(output.endsWith("\r\n"));
        assertTrue(inAllowedRange(extractPort(output)));
    }

    @Test
    public void doesNotChangeUnmarkedOrMissingEndpoint() {
        assertEquals("endpoint=203.0.113.12:51820\n",
                EndpointPortRandomizer.rewrite("endpoint=203.0.113.12:51820\n"));
        assertEquals("public_key=abc\n", EndpointPortRandomizer.rewrite("public_key=abc\n"));
        assertEquals("endpoint=203.0.113.12:16437\n",
                EndpointPortRandomizer.rewrite("endpoint=203.0.113.12:16437\n"));
    }

    @Test
    public void doesNotChangeAmbiguousOrMalformedEndpoints() {
        final String[] configs = {
                "endpoint=203.0.113.12:6437\nendpoint=203.0.113.13:6437\n",
                "endpoint=203.0.113.12:6437\nendpoint=broken\n",
                "endpoint=203.0.113.12:6437\nendpoint=host:99999\n",
                "endpoint=203.0.113.12:6437\nendpoint=2001:db8::1:51820\n",
                "endpoint=[2001:db8::1:6437\n",
                "endpoint=[host]:6437\n"
        };
        for (final String config : configs)
            assertEquals(config, EndpointPortRandomizer.rewrite(config));
    }

    @Test
    public void canRewriteOneMarkedPeerWithoutTouchingOtherPeer() {
        final String config = "public_key=one\nendpoint=203.0.113.12:51820\n"
                + "public_key=two\nendpoint=203.0.113.13:6437\n";
        final String result = EndpointPortRandomizer.rewrite(config);
        assertTrue(result.startsWith("public_key=one\nendpoint=203.0.113.12:51820\n"
                + "public_key=two\nendpoint=203.0.113.13:"));
        assertTrue(inAllowedRange(extractPort(result)));
    }

    private static int extractPort(final String config) {
        final int start = config.lastIndexOf("endpoint=");
        // For bracketed IPv6, use the final colon of the endpoint line.
        final int end = config.indexOf('\n', start);
        final String line = config.substring(start, end == -1 ? config.length() : end).trim();
        return Integer.parseInt(line.substring(line.lastIndexOf(':') + 1));
    }

    private static boolean inAllowedRange(final int port) {
        return (port >= 6500 && port <= 6649) || (port >= 8300 && port <= 8449);
    }
}
