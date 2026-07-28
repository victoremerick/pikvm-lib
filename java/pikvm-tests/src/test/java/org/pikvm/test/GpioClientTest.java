package org.pikvm.test;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pikvm.PiKvmClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GPIO client. Equivalent to {@code test_pikvm_gpio.py}.
 */
class GpioClientTest {

    private MockWebServer server;
    private PiKvmClient   client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = PiKvmClient.builder(server.getHostName() + ":" + server.getPort(),
                                      "user", "password")
            .schema("http").certificateTrusted(true).noWsClient().build();
    }

    @AfterEach
    void tearDown() throws Exception { client.close(); server.shutdown(); }

    @Test
    void testGetGpioState() {
        server.enqueue(new MockResponse().setBody(TestFixtures.GPIO_STATE_JSON));
        assertNotNull(client.getGpioState());
    }

    @Test
    void testSwitchGpioChannel() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        client.switchGpioChannel("led1", 1, 1);
        RecordedRequest req = server.takeRequest();
        String path = req.getPath();
        assertTrue(path.contains("channel=led1"), path);
        assertTrue(path.contains("state=1"), path);
        assertTrue(path.contains("wait=1"), path);
    }

    @Test
    void testPulseGpioChannel() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        client.pulseGpioChannel("relay1", 0.1, 1);
        RecordedRequest req = server.takeRequest();
        String path = req.getPath();
        assertTrue(path.contains("channel=relay1"), path);
        assertTrue(path.contains("delay="), path);
    }
}
