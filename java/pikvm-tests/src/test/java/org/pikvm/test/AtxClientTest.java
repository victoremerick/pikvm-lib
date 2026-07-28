package org.pikvm.test;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pikvm.PiKvmClient;
import org.pikvm.atx.AtxButtonAction;
import org.pikvm.atx.AtxPowerAction;
import org.pikvm.exception.PiKvmApiException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ATX client. Equivalent to {@code test_pikvm_atx.py}.
 */
class AtxClientTest {

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
    void testGetAtxState() {
        server.enqueue(new MockResponse().setBody(TestFixtures.ATX_STATE_JSON));
        Map<String, Object> state = client.getAtxState();
        assertNotNull(state);
        assertTrue(state.containsKey("leds"));
    }

    @Test
    void testSetAtxPowerOn() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        client.setAtxPower(AtxPowerAction.ON);
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("action=on"), "path was: " + req.getPath());
    }

    @Test
    void testSetAtxPowerOff() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        client.setAtxPower(AtxPowerAction.OFF);
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("action=off"), "path was: " + req.getPath());
    }

    @Test
    void testClickAtxButtonPower() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        client.clickAtxButton(AtxButtonAction.POWER);
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("button=power"), "path was: " + req.getPath());
    }

    @Test
    void testClickAtxButtonReset() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        client.clickAtxButton(AtxButtonAction.RESET);
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("button=reset"), "path was: " + req.getPath());
    }
}
