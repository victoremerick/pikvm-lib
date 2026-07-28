package org.pikvm.config;

import java.util.Objects;

/**
 * Immutable configuration for a PiKVM device connection.
 * <p>
 * Equivalent to the constructor parameters of {@code BuildPiKVM} in Python.
 * </p>
 */
public final class PiKvmConfig {

    private final String hostname;
    private final String username;
    private final String password;
    /** Optional TOTP secret from {@code /etc/kvmd/totp.secret}. Null if 2FA not used. */
    private final String secret;
    /** "http" or "https". */
    private final String schema;
    /** Whether the server TLS certificate is from a trusted CA. */
    private final boolean certificateTrusted;

    private PiKvmConfig(Builder builder) {
        this.hostname          = Objects.requireNonNull(builder.hostname, "hostname");
        this.username          = Objects.requireNonNull(builder.username, "username");
        this.password          = Objects.requireNonNull(builder.password, "password");
        this.secret            = builder.secret;
        this.schema            = Objects.requireNonNull(builder.schema, "schema");
        this.certificateTrusted = builder.certificateTrusted;
        if (!"http".equals(schema) && !"https".equals(schema)) {
            throw new IllegalArgumentException("Schema must be \'http\' or \'https\', got: " + schema);
        }
    }

    public static Builder builder(String hostname, String username, String password) {
        return new Builder(hostname, username, password);
    }

    public String getHostname()          { return hostname; }
    public String getUsername()          { return username; }
    public String getPassword()          { return password; }
    public String getSecret()            { return secret; }
    public String getSchema()            { return schema; }
    public boolean isCertificateTrusted(){ return certificateTrusted; }

    /** Returns the base HTTP URL, e.g. {@code https://192.168.1.10}. */
    public String getBaseUrl() { return schema + "://" + hostname; }

    /** Returns the WebSocket base URL derived from the HTTP schema. */
    public String getWsBaseUrl() {
        String wsSchema = "https".equals(schema) ? "wss" : "ws";
        return wsSchema + "://" + hostname;
    }

    // ── Builder ──────────────────────────────────────────────────────────

    public static final class Builder {
        private final String hostname;
        private final String username;
        private final String password;
        private String secret            = null;
        private String schema            = "https";
        private boolean certificateTrusted = false;

        private Builder(String hostname, String username, String password) {
            this.hostname = hostname;
            this.username = username;
            this.password = password;
        }

        /** TOTP secret (base-32 encoded, as found in {@code /etc/kvmd/totp.secret}). */
        public Builder secret(String secret) {
            this.secret = secret;
            return this;
        }

        /** Protocol schema: "http" or "https" (default "https"). */
        public Builder schema(String schema) {
            this.schema = schema;
            return this;
        }

        /**
         * Set to {@code true} only when the PiKVM TLS certificate is issued by a trusted CA.
         * When {@code false} (default), certificate verification is disabled.
         * NOTE: disabling verification reduces security – use in trusted networks only.
         */
        public Builder certificateTrusted(boolean certificateTrusted) {
            this.certificateTrusted = certificateTrusted;
            return this;
        }

        public PiKvmConfig build() { return new PiKvmConfig(this); }
    }
}
