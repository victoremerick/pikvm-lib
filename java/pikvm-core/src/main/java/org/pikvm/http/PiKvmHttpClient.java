package org.pikvm.http;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;
import okio.BufferedSink;
import org.pikvm.auth.AuthHeaderProvider;
import org.pikvm.config.PiKvmConfig;
import org.pikvm.exception.PiKvmApiException;
import org.pikvm.exception.PiKvmNetworkException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * HTTP client for PiKVM REST API.
 * <p>
 * Equivalent to {@code PiKVMAuxRequests} in Python. Provides {@code _get},
 * {@code _post}, {@code _get_infos}, and {@code _posts} semantics over OkHttp.
 * </p>
 */
public class PiKvmHttpClient {

    private static final Logger LOGGER     = Logger.getLogger(PiKvmHttpClient.class.getName());
    private static final MediaType OCTET   = MediaType.parse("application/octet-stream");

    private final PiKvmConfig        config;
    private final OkHttpClient       http;
    private final AuthHeaderProvider auth;
    private final Gson               gson   = new Gson();

    public PiKvmHttpClient(PiKvmConfig config) {
        this.config = config;
        this.auth   = new AuthHeaderProvider(config);
        OkHttpClient.Builder b = new OkHttpClient.Builder();
        if (!config.isCertificateTrusted()) {
            LOGGER.fine("Disabling SSL certificate verification");
            TlsHelper.trustAllCerts(b);
        }
        this.http = b.build();
    }

    /** Package-visible constructor for test injection. */
    PiKvmHttpClient(PiKvmConfig config, OkHttpClient http) {
        this.config = config;
        this.auth   = new AuthHeaderProvider(config);
        this.http   = http;
    }

    // ── URL construction ──────────────────────────────────────────────────

    private String buildUrl(String path, String options) {
        StringBuilder sb = new StringBuilder(config.getBaseUrl());
        if (!path.startsWith("/")) sb.append('/');
        sb.append(path);
        if (options != null && !options.isEmpty()) sb.append('?').append(options);
        LOGGER.fine("Calling: " + sb);
        return sb.toString();
    }

    private Request.Builder requestBuilder(String path, String options) {
        Request.Builder rb = new Request.Builder().url(buildUrl(path, options));
        auth.getHeaders().forEach(rb::addHeader);
        return rb;
    }

    // ── GET ───────────────────────────────────────────────────────────────

    /** Performs a GET and returns the raw OkHttp {@link Response} (caller must close). */
    public Response get(String path) throws IOException {
        return get(path, null);
    }

    /** Performs a GET with optional query string. */
    public Response get(String path, String options) throws IOException {
        return http.newCall(requestBuilder(path, options).get().build()).execute();
    }

    // ── POST ──────────────────────────────────────────────────────────────

    /** Performs a POST with an empty body and optional query string. */
    public Response post(String path, String options) throws IOException {
        return post(path, options, RequestBody.create(new byte[0]));
    }

    /** Performs a POST with an explicit body. */
    public Response post(String path, String options, RequestBody body) throws IOException {
        return http.newCall(requestBuilder(path, options).post(body).build()).execute();
    }

    /**
     * Performs a POST streaming the given {@link InputStream} as the request body.
     * Used for MSD image uploads.
     *
     * @param path          endpoint path
     * @param options       query string
     * @param inputStream   data to upload (closed by caller after return)
     * @param contentLength byte length of the stream, or {@code -1} if unknown
     */
    public Response postStream(String path,
                                String options,
                                InputStream inputStream,
                                long contentLength) throws IOException {
        RequestBody body = new RequestBody() {
            @Override public MediaType contentType() { return OCTET; }
            @Override public long contentLength()    { return contentLength; }
            @Override public void writeTo(BufferedSink sink) throws IOException {
                byte[] buf = new byte[8192];
                int    n;
                while ((n = inputStream.read(buf)) != -1) {
                    sink.write(buf, 0, n);
                }
            }
        };
        return post(path, options, body);
    }

    // ── High-level helpers ────────────────────────────────────────────────

    /**
     * GET + parse JSON and return the {@code "result"} field as a map.
     * Equivalent to {@code _get_infos()} in Python.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getInfos(String path) {
        try (Response resp = get(path)) {
            String body = resp.body() == null ? "{}" : resp.body().string();
            LOGGER.fine(body);
            Map<String, Object> root =
                gson.fromJson(body, new TypeToken<Map<String, Object>>() {}.getType());
            return (Map<String, Object>) root.get("result");
        } catch (IOException e) {
            throw new PiKvmNetworkException("GET " + path + " failed", e);
        }
    }

    /**
     * POST with a string action and a set of valid actions guard.
     * Equivalent to {@code _posts()} in Python.
     */
    public void posts(String path,
                      String actionName,
                      String action,
                      Set<String> validActions) {
        if (!validActions.contains(action)) {
            throw new PiKvmApiException(
                action + " is not a valid option. Valid options = " + validActions, 400);
        }
        try (Response resp = post(path, actionName + "=" + action)) {
            if (!resp.isSuccessful()) {
                throw new PiKvmApiException("POST " + path + " failed: " + resp.code(), resp.code());
            }
        } catch (IOException e) {
            throw new PiKvmNetworkException("POST " + path + " failed", e);
        }
    }

    /**
     * POST with an integer action (used by MSD connected control).
     */
    public void postsInt(String path,
                         String actionName,
                         int action,
                         Set<Integer> validActions) {
        if (!validActions.contains(action)) {
            throw new PiKvmApiException(
                action + " is not a valid option. Valid options = " + validActions, 400);
        }
        try (Response resp = post(path, actionName + "=" + action)) {
            if (!resp.isSuccessful()) {
                throw new PiKvmApiException("POST " + path + " failed: " + resp.code(), resp.code());
            }
        } catch (IOException e) {
            throw new PiKvmNetworkException("POST " + path + " failed", e);
        }
    }

    public PiKvmConfig   getConfig()       { return config; }
    public OkHttpClient  getOkHttpClient() { return http; }
}
