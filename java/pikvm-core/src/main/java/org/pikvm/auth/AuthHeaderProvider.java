package org.pikvm.auth;

import org.pikvm.config.PiKvmConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides PiKVM authentication headers.
 * <p>
 * Equivalent to {@code BuildPiKVM._set_headers()} in Python.
 * When a TOTP secret is configured, the current TOTP token is appended to the password.
 * </p>
 */
public final class AuthHeaderProvider {

    private final PiKvmConfig config;

    public AuthHeaderProvider(PiKvmConfig config) {
        this.config = config;
    }

    /**
     * Returns fresh authentication headers.
     * Call this on every request so that the TOTP token is current.
     */
    public Map<String, String> getHeaders() {
        String passwd = config.getPassword();
        if (config.getSecret() != null && !config.getSecret().isEmpty()) {
            passwd += Totp.generate(config.getSecret());
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-KVMD-User",   config.getUsername());
        headers.put("X-KVMD-Passwd", passwd);
        return headers;
    }
}
