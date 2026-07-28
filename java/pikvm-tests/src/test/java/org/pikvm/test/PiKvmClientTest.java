package org.pikvm.test;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pikvm.PiKvmClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PiKvmClient} façade – system info, auth checks.
 * Equivalent to {@code test_pikvm.py} in Python.
 */
class PiKvmClientTest {

    private MockWebServer server;
    private PiKvmClient   client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = PiKvmClient.builder(server.getHostName() + ":" + server.getPort(),
                                      "user", "password")
            .schema("http")
            .certificateTrusted(true)
            .noWsClient()
            .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        client.close();
        server.shutdown();
    }

    @Test
    void testGetSystemInfo() {
        server.enqueue(new MockResponse().setBody(TestFixtures.PIKVM_MOCK_INFO_JSON)
            .setResponseCode(200));
        Map<String, Object> info = client.getSystemInfo();
        assertNotNull(info);
        assertTrue(info.containsKey("system"));
    }

    @Test
    void testIsAuthenticatedTrue() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        assertTrue(client.isAuthenticated());
    }

    @Test
    void testIsAuthenticatedFalse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401));
        assertFalse(client.isAuthenticated());
    }

    @Test
    void testInvalidSchemaThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            PiKvmClient.builder("host", "user", "pass").schema("ftp").build()
        );
    }
}
