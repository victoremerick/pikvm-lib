package org.pikvm.http;

import okhttp3.OkHttpClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * Utilities for configuring TLS on OkHttp clients.
 * <p>
 * <strong>Security note:</strong> Disabling certificate verification
 * ({@link #trustAllCerts(OkHttpClient.Builder)}) removes protection against
 * man-in-the-middle attacks. Use only when connecting to a PiKVM with a
 * self-signed certificate on a trusted network.
 * </p>
 */
public final class TlsHelper {

    private TlsHelper() {}

    /**
     * Configures the OkHttp builder to accept any TLS certificate.
     * Equivalent to Python websocket-client {@code sslopt={"cert_reqs": ssl.CERT_NONE}}
     * and {@code requests.packages.urllib3.disable_warnings()}.
     *
     * <p><strong>Risk:</strong> disables certificate chain and hostname validation; the
     * caller is responsible for ensuring the network path to the PiKVM device is trusted
     * (e.g. local LAN or VPN). This mode is intentionally supported in production to
     * accommodate PiKVM devices with factory self-signed certificates.
     */
    @SuppressWarnings("java:S4830") // TrustManager accepting all certs is intentional – see Javadoc
    public static OkHttpClient.Builder trustAllCerts(OkHttpClient.Builder builder) {
        try {
            X509TrustManager trustAll = new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] c, String t) {}
                @Override public void checkServerTrusted(X509Certificate[] c, String t) {}
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{trustAll}, new java.security.SecureRandom());
            builder.sslSocketFactory(ctx.getSocketFactory(), trustAll);
            builder.hostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure untrusted TLS", e);
        }
        return builder;
    }
}
