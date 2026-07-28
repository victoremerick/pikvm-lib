package org.pikvm.atx;

/** Valid button actions for ATX click endpoint. */
public enum AtxButtonAction {
    POWER("power"),
    POWER_LONG("power_long"),
    RESET("reset");

    private final String apiValue;

    AtxButtonAction(String apiValue) { this.apiValue = apiValue; }

    public String getApiValue() { return apiValue; }
}
