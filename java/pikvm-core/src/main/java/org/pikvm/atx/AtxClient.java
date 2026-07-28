package org.pikvm.atx;

import org.pikvm.endpoint.BaseEndpoint;
import org.pikvm.http.PiKvmHttpClient;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ATX power control client.
 * <p>
 * Equivalent to {@code PiKVMATX} in Python.
 * </p>
 */
public class AtxClient extends BaseEndpoint {

    private static final String BASE_PATH    = "/api/atx";
    private static final Set<String> POWER_ACTIONS =
        Arrays.stream(AtxPowerAction.values())
              .map(AtxPowerAction::getApiValue)
              .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> BUTTON_ACTIONS =
        Arrays.stream(AtxButtonAction.values())
              .map(AtxButtonAction::getApiValue)
              .collect(Collectors.toUnmodifiableSet());

    public AtxClient(PiKvmHttpClient httpClient) {
        super(httpClient);
    }

    /**
     * Returns the current ATX subsystem state.
     * Equivalent to {@code get_atx_state()}.
     */
    public Map<String, Object> getAtxState() {
        return getEndpointState(BASE_PATH);
    }

    /**
     * Sets ATX power.
     * Equivalent to {@code set_atx_power(action)}.
     *
     * @param action power action (on / off / off_hard / reset_hard)
     */
    public void setAtxPower(AtxPowerAction action) {
        setEndpoint(BASE_PATH, "power", POWER_ACTIONS, action.getApiValue());
    }

    /**
     * Clicks an ATX button.
     * Equivalent to {@code click_atx_button(button_name)}.
     *
     * @param button button to click (power / power_long / reset)
     */
    public void clickAtxButton(AtxButtonAction button) {
        setEndpoint(BASE_PATH, "click", BUTTON_ACTIONS, button.getApiValue(), "button");
    }
}
