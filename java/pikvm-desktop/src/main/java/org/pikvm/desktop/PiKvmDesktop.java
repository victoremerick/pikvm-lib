package org.pikvm.desktop;

import org.pikvm.PiKvmClient;
import org.pikvm.config.PiKvmConfig;

/**
 * Convenience factory for PiKVM desktop (Java 17+) applications.
 *
 * <p>Wraps {@link PiKvmClient} with desktop-friendly defaults.
 * For image handling, use {@link StreamerDesktop} to decode snapshots into
 * {@link java.awt.image.BufferedImage}.
 * </p>
 */
public final class PiKvmDesktop {

    private PiKvmDesktop() {}

    /**
     * Creates a {@link PiKvmClient} with the given credentials.
     * WebSocket is initialised eagerly.
     */
    public static PiKvmClient create(String hostname, String username, String password) {
        return PiKvmClient.builder(hostname, username, password).build();
    }

    /** Creates a {@link PiKvmClient} with a custom schema and TLS option. */
    public static PiKvmClient create(String hostname,
                                      String username,
                                      String password,
                                      String schema,
                                      boolean certificateTrusted) {
        return PiKvmClient.builder(hostname, username, password)
            .schema(schema)
            .certificateTrusted(certificateTrusted)
            .build();
    }

    /** Creates a {@link PiKvmClient} with TOTP secret. */
    public static PiKvmClient createWithSecret(String hostname,
                                                String username,
                                                String password,
                                                String totpSecret) {
        return PiKvmClient.builder(hostname, username, password)
            .secret(totpSecret)
            .build();
    }
}
