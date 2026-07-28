package org.pikvm.test;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pikvm.PiKvmClient;
import org.pikvm.msd.MsdParameters;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MSD client. Equivalent to {@code test_pikvm_msd.py}.
 */
class MsdClientTest {

    @TempDir Path tmpDir;

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
    void testGetMsdState() {
        server.enqueue(new MockResponse().setBody(TestFixtures.MSD_STATE_JSON));
        assertNotNull(client.getMsdState());
    }

    @Test
    void testUploadMsdImage() throws Exception {
        File iso = tmpDir.resolve("test.iso").toFile();
        Files.write(iso.toPath(), new byte[]{1, 2, 3});
        server.enqueue(new MockResponse().setResponseCode(200));
        client.uploadMsdImage(iso);
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("image=test.iso"), req.getPath());
    }

    @Test
    void testUploadMsdRemote() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        client.uploadMsdRemote("http://example.com/image.iso");
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("url="), req.getPath());
    }

    @Test
    void testConnectMsd() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        client.connectMsd();
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("connected=1"), req.getPath());
    }

    @Test
    void testDisconnectMsd() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        client.disconnectMsd();
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("connected=0"), req.getPath());
    }

    @Test
    void testRemoveMsdImage() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        client.removeMsdImage("test.iso");
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("image=test.iso"), req.getPath());
    }

    @Test
    void testResetMsd() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        client.resetMsd();
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("/api/msd/reset"), req.getPath());
    }

    @Test
    void testSetMsdParametersCdrom() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        client.setMsdParameters(MsdParameters.builder("boot.iso").cdrom(true).build());
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("cdrom=1"), req.getPath());
    }

    @Test
    void testSetMsdParametersFlash() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        client.setMsdParameters(MsdParameters.builder("data.img").cdrom(false).build());
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("cdrom=0"), req.getPath());
    }
}
