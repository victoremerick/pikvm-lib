package org.pikvm.test;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pikvm.PiKvmClient;
import org.pikvm.streamer.SnapshotResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for streamer client. Equivalent to {@code test_pikvm_streamer.py}.
 */
@ExtendWith(MockitoExtension.class)
class StreamerClientTest {

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
    void testGetStreamerState() {
        server.enqueue(new MockResponse().setBody(TestFixtures.STREAMER_STATE_JSON));
        assertNotNull(client.getStreamerState());
    }

    @Test
    void testCaptureSnapshotReturnsRawBytes() {
        okio.Buffer buf1 = new okio.Buffer().write(TestFixtures.MINIMAL_JPEG);
        server.enqueue(new MockResponse()
            .setBody(buf1)
            .setResponseCode(200)
            .addHeader("Content-Type", "image/jpeg"));
        SnapshotResult result = client.getStreamer().captureSnapshot(false, 0);
        assertNotNull(result.getImageBytes());
        assertTrue(result.getImageBytes().length > 0);
        assertFalse(result.isOcr());
    }

    @Test
    void testSnapshotResultOcr() {
        byte[] ocrBytes = "Hello PiKVM".getBytes();
        okio.Buffer buf2 = new okio.Buffer().write(ocrBytes);
        server.enqueue(new MockResponse()
            .setBody(buf2)
            .setResponseCode(200)
            .addHeader("Content-Type", "text/plain"));
        SnapshotResult result = client.getStreamer().captureSnapshot(true, 0);
        assertTrue(result.isOcr());
        assertEquals("Hello PiKVM", result.getOcrText());
    }
}
