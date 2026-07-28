package org.pikvm.mouse;

/** Mouse buttons recognised by the PiKVM API. */
public enum MouseButton {
    LEFT("left"),
    RIGHT("right"),
    MIDDLE("middle"),
    UP("up"),
    DOWN("down");

    private final String apiValue;

    MouseButton(String apiValue) { this.apiValue = apiValue; }

    public String getApiValue() { return apiValue; }
}
