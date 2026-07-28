package org.pikvm.test;

/**
 * Shared test fixtures.
 * Equivalent to {@code mock_pikvm_response.py} in Python tests.
 */
public final class TestFixtures {

    private TestFixtures() {}

    public static final String PIKVM_MOCK_INFO_JSON = "{"
        + "\"ok\": true,"
        + "\"result\": {"
        + "  \"extras\": {"
        + "    \"ipmi\": {\"daemon\":\"kvmd-ipmi\",\"description\":\"Show IPMI information\"}"
        + "  },"
        + "  \"hw\": {"
        + "    \"health\": {"
        + "      \"temp\": {\"cpu\":36.511,\"gpu\":35.0}"
        + "    },"
        + "    \"platform\": {\"base\":\"Raspberry Pi 4 Model B Rev 1.1\",\"type\":\"rpi\"}"
        + "  },"
        + "  \"meta\": {"
        + "    \"server\": {\"host\":\"localhost.localdomain\"}"
        + "  },"
        + "  \"system\": {"
        + "    \"kvmd\": {\"version\":\"2.1\"},"
        + "    \"streamer\": {\"app\":\"ustreamer\",\"version\":\"2.1\"}"
        + "  }"
        + "}}";

    public static final String ATX_STATE_JSON = "{\"ok\":true,\"result\":{\"leds\":{\"hdd\":false,\"power\":true}}}";
    public static final String GPIO_STATE_JSON = "{\"ok\":true,\"result\":{\"scheme\":{},\"view\":{\"table\": []}}}";
    public static final String MSD_STATE_JSON  = "{\"ok\":true,\"result\":{\"storage\":{\"images\":{},\"uploading\":false}}}";
    public static final String STREAMER_STATE_JSON = "{\"ok\":true,\"result\":{\"limits\":{\"max_fps\":40}}}";

    /** Minimal 2×2 JPEG (for streamer image tests). */
    public static final byte[] MINIMAL_JPEG = new byte[]{
        (byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE0,
        0,16,0x4A,0x46,0x49,0x46,0,1,1,0,0,1,0,1,0,0,
        (byte)0xFF,(byte)0xC0,0,11,8, // SOF0
        0,2,0,2, // height=2, width=2
        1,1,17,0,(byte)0xFF,(byte)0xD9
    };
}
