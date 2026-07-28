package org.pikvm.demo;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps AWT {@link KeyEvent} virtual-key codes to PiKVM DOM key names.
 * <p>
 * Location-sensitive modifier keys (Shift, Control, Alt, Meta) are
 * correctly distinguished as left/right variants.
 * </p>
 */
final class AwtKeyMapper {

    private AwtKeyMapper() {}

    private static final Map<Integer, String> VK_MAP = new HashMap<>();

    static {
        // Letters A-Z
        for (int i = 0; i <= 25; i++) {
            VK_MAP.put(KeyEvent.VK_A + i, "Key" + (char) ('A' + i));
        }
        // Digits 0-9 (main row)
        for (int d = 0; d <= 9; d++) {
            VK_MAP.put(KeyEvent.VK_0 + d, "Digit" + d);
        }
        // Function keys F1–F12
        for (int f = 1; f <= 12; f++) {
            VK_MAP.put(KeyEvent.VK_F1 + (f - 1), "F" + f);
        }
        // Numpad 0-9
        for (int n = 0; n <= 9; n++) {
            VK_MAP.put(KeyEvent.VK_NUMPAD0 + n, "Numpad" + n);
        }
        // Navigation / editing
        VK_MAP.put(KeyEvent.VK_ESCAPE,        "Escape");
        VK_MAP.put(KeyEvent.VK_ENTER,         "Enter");
        VK_MAP.put(KeyEvent.VK_BACK_SPACE,    "Backspace");
        VK_MAP.put(KeyEvent.VK_TAB,           "Tab");
        VK_MAP.put(KeyEvent.VK_SPACE,         "Space");
        VK_MAP.put(KeyEvent.VK_DELETE,        "Delete");
        VK_MAP.put(KeyEvent.VK_INSERT,        "Insert");
        VK_MAP.put(KeyEvent.VK_HOME,          "Home");
        VK_MAP.put(KeyEvent.VK_END,           "End");
        VK_MAP.put(KeyEvent.VK_PAGE_UP,       "PageUp");
        VK_MAP.put(KeyEvent.VK_PAGE_DOWN,     "PageDown");
        VK_MAP.put(KeyEvent.VK_UP,            "ArrowUp");
        VK_MAP.put(KeyEvent.VK_DOWN,          "ArrowDown");
        VK_MAP.put(KeyEvent.VK_LEFT,          "ArrowLeft");
        VK_MAP.put(KeyEvent.VK_RIGHT,         "ArrowRight");
        // Lock keys
        VK_MAP.put(KeyEvent.VK_CAPS_LOCK,     "CapsLock");
        VK_MAP.put(KeyEvent.VK_NUM_LOCK,      "NumLock");
        VK_MAP.put(KeyEvent.VK_SCROLL_LOCK,   "ScrollLock");
        VK_MAP.put(KeyEvent.VK_PRINTSCREEN,   "PrintScreen");
        VK_MAP.put(KeyEvent.VK_PAUSE,         "Pause");
        // Numpad operators
        VK_MAP.put(KeyEvent.VK_ADD,           "NumpadAdd");
        VK_MAP.put(KeyEvent.VK_SUBTRACT,      "NumpadSubtract");
        VK_MAP.put(KeyEvent.VK_MULTIPLY,      "NumpadMultiply");
        VK_MAP.put(KeyEvent.VK_DIVIDE,        "NumpadDivide");
        VK_MAP.put(KeyEvent.VK_DECIMAL,       "NumpadDecimal");
        // Punctuation / symbols (US layout)
        VK_MAP.put(KeyEvent.VK_MINUS,         "Minus");
        VK_MAP.put(KeyEvent.VK_EQUALS,        "Equal");
        VK_MAP.put(KeyEvent.VK_OPEN_BRACKET,  "BracketLeft");
        VK_MAP.put(KeyEvent.VK_CLOSE_BRACKET, "BracketRight");
        VK_MAP.put(KeyEvent.VK_BACK_SLASH,    "Backslash");
        VK_MAP.put(KeyEvent.VK_SEMICOLON,     "Semicolon");
        VK_MAP.put(KeyEvent.VK_QUOTE,         "Quote");
        VK_MAP.put(KeyEvent.VK_BACK_QUOTE,    "Backquote");
        VK_MAP.put(KeyEvent.VK_COMMA,         "Comma");
        VK_MAP.put(KeyEvent.VK_PERIOD,        "Period");
        VK_MAP.put(KeyEvent.VK_SLASH,         "Slash");
    }

    /**
     * Converts an AWT {@link KeyEvent} to a PiKVM DOM key name.
     * <p>
     * Modifier keys are location-aware (e.g. left Shift → {@code "ShiftLeft"},
     * right Shift → {@code "ShiftRight"}). All other keys are looked up in a
     * static table.
     * </p>
     *
     * @param e the key event to translate
     * @return PiKVM DOM key name, or {@code null} if the key is not recognised
     */
    static String toDomKey(KeyEvent e) {
        int vk  = e.getKeyCode();
        int loc = e.getKeyLocation();

        switch (vk) {
            case KeyEvent.VK_SHIFT:
                return loc == KeyEvent.KEY_LOCATION_RIGHT ? "ShiftRight"   : "ShiftLeft";
            case KeyEvent.VK_CONTROL:
                return loc == KeyEvent.KEY_LOCATION_RIGHT ? "ControlRight" : "ControlLeft";
            case KeyEvent.VK_ALT:
                return loc == KeyEvent.KEY_LOCATION_RIGHT ? "AltRight"     : "AltLeft";
            case KeyEvent.VK_META:
            case KeyEvent.VK_WINDOWS:
                return loc == KeyEvent.KEY_LOCATION_RIGHT ? "MetaRight"    : "MetaLeft";
            default:
                return VK_MAP.get(vk);
        }
    }
}
