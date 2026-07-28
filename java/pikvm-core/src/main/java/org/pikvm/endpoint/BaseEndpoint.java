package org.pikvm.endpoint;

import org.pikvm.http.PiKvmHttpClient;

import java.util.Map;
import java.util.Set;

/**
 * Abstract base for all endpoint client classes.
 * <p>
 * Equivalent to {@code PiKVMEndpoints} in Python, which itself extends
 * {@code PiKVMAuxRequests}. Provides {@code get_endpoint_state} and
 * {@code set_endpoint} as shared building blocks.
 * </p>
 */
public abstract class BaseEndpoint {

    protected final PiKvmHttpClient httpClient;

    protected BaseEndpoint(PiKvmHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * GET the endpoint state and return the parsed {@code result} map.
     * Equivalent to {@code get_endpoint_state(endpoint_base, "")}.
     */
    protected Map<String, Object> getEndpointState(String endpointBase) {
        return httpClient.getInfos(endpointBase);
    }

    /**
     * GET the endpoint state with an appended sub-path.
     * Equivalent to {@code get_endpoint_state(endpoint_base, append_path)}.
     */
    protected Map<String, Object> getEndpointState(String endpointBase, String appendPath) {
        String path = joinPath(endpointBase, appendPath);
        return httpClient.getInfos(path);
    }

    /**
     * POST an action to an endpoint.
     * Equivalent to {@code set_endpoint(endpoint_base, append_path, valid_actions, action)}.
     *
     * @param endpointBase base path, e.g. {@code /api/atx}
     * @param appendPath   sub-path to append, e.g. {@code power}
     * @param validActions set of permitted action strings
     * @param action       the chosen action
     */
    protected void setEndpoint(String endpointBase,
                                String appendPath,
                                Set<String> validActions,
                                String action) {
        setEndpoint(endpointBase, appendPath, validActions, action, "action");
    }

    /**
     * POST an action to an endpoint with a custom action parameter name.
     * Equivalent to {@code set_endpoint(endpoint_base, append_path, valid_actions, action, action_name)}.
     */
    protected void setEndpoint(String endpointBase,
                                String appendPath,
                                Set<String> validActions,
                                String action,
                                String actionName) {
        String path = joinPath(endpointBase, appendPath);
        httpClient.posts(path, actionName, action, validActions);
    }

    // ── helper ────────────────────────────────────────────────────────────

    private static String joinPath(String base, String append) {
        if (append == null || append.isEmpty()) return base;
        String b = base.endsWith("/")   ? base.substring(0, base.length() - 1) : base;
        String a = append.startsWith("/") ? append.substring(1) : append;
        return b + "/" + a;
    }
}
