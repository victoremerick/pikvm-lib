package org.pikvm.gpio;

import org.pikvm.endpoint.BaseEndpoint;
import org.pikvm.exception.PiKvmNetworkException;
import org.pikvm.http.PiKvmHttpClient;
import okhttp3.Response;

import java.io.IOException;
import java.util.Map;

/**
 * GPIO channel control client.
 * <p>
 * Equivalent to {@code PiKVMGPIO} in Python.
 * </p>
 */
public class GpioClient extends BaseEndpoint {

    private static final String BASE_PATH   = "/api/gpio";
    private static final String SWITCH_PATH = "/api/gpio/switch";
    private static final String PULSE_PATH  = "/api/gpio/pulse";

    public GpioClient(PiKvmHttpClient httpClient) {
        super(httpClient);
    }

    /**
     * Returns current GPIO subsystem state.
     * Equivalent to {@code get_gpio_state()}.
     */
    public Map<String, Object> getGpioState() {
        return getEndpointState(BASE_PATH);
    }

    /**
     * Switches a GPIO channel to the given state.
     * Equivalent to {@code switch_gpio_channel(channel, state, wait)}.
     *
     * @param channel GPIO channel identifier
     * @param state   target state (1 = on, 0 = off)
     * @param wait    wait flag (1 = wait for completion, 0 = fire-and-forget; null omits parameter)
     */
    public void switchGpioChannel(String channel, int state, Integer wait) {
        StringBuilder opts = new StringBuilder("channel=").append(channel)
            .append("&state=").append(state);
        if (wait != null) opts.append("&wait=").append(wait);
        gpioPost(SWITCH_PATH, opts.toString());
    }

    /** Overload with defaults: state=1, wait=1 (matches Python defaults). */
    public void switchGpioChannel(String channel) {
        switchGpioChannel(channel, 1, 1);
    }

    /**
     * Pulses a GPIO channel.
     * Equivalent to {@code pulse_gpio_channel(channel, delay, wait)}.
     *
     * @param channel GPIO channel identifier
     * @param delay   pulse delay in seconds (null to omit)
     * @param wait    wait flag (null to omit)
     */
    public void pulseGpioChannel(String channel, Double delay, Integer wait) {
        StringBuilder opts = new StringBuilder("channel=").append(channel);
        if (delay != null) opts.append("&delay=").append(delay);
        if (wait  != null) opts.append("&wait=").append(wait);
        gpioPost(PULSE_PATH, opts.toString());
    }

    /** Overload with defaults: delay=0, wait=1. */
    public void pulseGpioChannel(String channel) {
        pulseGpioChannel(channel, 0.0, 1);
    }

    private void gpioPost(String path, String options) {
        try (Response resp = httpClient.post(path, options)) {
            if (!resp.isSuccessful()) {
                throw new PiKvmNetworkException(
                    "GPIO POST failed: " + resp.code() + " " + resp.message());
            }
        } catch (IOException e) {
            throw new PiKvmNetworkException("GPIO POST failed", e);
        }
    }
}
