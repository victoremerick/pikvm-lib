package org.pikvm.atx;

/** Valid power actions for ATX subsystem. */
public enum AtxPowerAction {
    ON("on"),
    OFF("off"),
    OFF_HARD("off_hard"),
    RESET_HARD("reset_hard");

    private final String apiValue;

    AtxPowerAction(String apiValue) { this.apiValue = apiValue; }

    /** Returns the string value used in the PiKVM API. */
    public String getApiValue() { return apiValue; }
}
