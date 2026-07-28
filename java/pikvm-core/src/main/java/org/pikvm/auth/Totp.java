package org.pikvm.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.time.Instant;

/**
 * RFC 6238 TOTP implementation without external dependencies.
 * Behaviorally equivalent to {@code pyotp.TOTP(secret).now()}.
 */
final class Totp {

    private static final int    DIGITS  = 6;
    private static final int    PERIOD  = 30;
    private static final String ALPHA   = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {}

    /**
     * Generates a 6-digit TOTP token from a base-32 encoded secret.
     *
     * @param base32Secret base-32 encoded secret (case-insensitive, spaces/padding ignored)
     * @return 6-digit TOTP token as a zero-padded string
     */
    static String generate(String base32Secret) {
        try {
            byte[] key     = base32Decode(base32Secret.toUpperCase()
                                              .replaceAll("[= ]", ""));
            long   counter = Instant.now().getEpochSecond() / PERIOD;
            byte[] cBytes  = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash   = mac.doFinal(cBytes);
            int    offset = hash[hash.length - 1] & 0x0f;
            int    code   = ((hash[offset]     & 0x7f) << 24)
                          | ((hash[offset + 1] & 0xff) << 16)
                          | ((hash[offset + 2] & 0xff) <<  8)
                          |  (hash[offset + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", code % (int) Math.pow(10, DIGITS));
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate TOTP", e);
        }
    }

    private static byte[] base32Decode(String input) {
        int byteCount = input.length() * 5 / 8;
        byte[] output = new byte[byteCount];
        int buffer = 0, bitsLeft = 0, idx = 0;
        for (char c : input.toCharArray()) {
            int val = ALPHA.indexOf(c);
            if (val < 0) continue;
            buffer   = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                output[idx++] = (byte) ((buffer >> bitsLeft) & 0xff);
            }
        }
        return output;
    }
}
