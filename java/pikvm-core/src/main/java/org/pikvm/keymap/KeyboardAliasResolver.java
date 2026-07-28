package org.pikvm.keymap;

/**
 * Resolves a key string to its HID keycode.
 * <p>
 * Mirrors the {@code _key_to_keycode()} logic in {@code pikvm_keyboard.py},
 * consulting {@link Keymaps#BASE}, {@link Keymaps#SHIFT}, and
 * {@link Keymaps#PYAUTOGUI} in order.
 * </p>
 */
public final class KeyboardAliasResolver {

    private KeyboardAliasResolver() {}

    /**
     * Converts a key string to its HID keycode string.
     *
     * @param key input key string (e.g. "a", "ctrl", "<Enter>", "5")
     * @return HID keycode string (e.g. "KeyA", "ControlLeft", "Enter", "Digit5")
     */
    public static String toKeycode(String key) {
        String padded = (key.contains("<") && key.contains(">")) ? key : "<" + key + ">";

        if (Keymaps.BASE.containsKey(key))    return Keymaps.BASE.get(key);
        if (Keymaps.BASE.containsKey(padded)) return Keymaps.BASE.get(padded);
        if (Keymaps.SHIFT.containsKey(key))   return Keymaps.SHIFT.get(key);
        if (Keymaps.SHIFT.containsKey(padded))return Keymaps.SHIFT.get(padded);
        if (Keymaps.PYAUTOGUI.containsKey(key)) return Keymaps.PYAUTOGUI.get(key);

        if (key.length() == 1 && Character.isDigit(key.charAt(0))) return "Digit" + key;
        if (" ".equals(key)) return "Space";
        if ("\"".equals(key)) return "Quote";

        return "Key" + key.toUpperCase();
    }

    /**
     * Returns true when sending this key requires a Shift modifier.
     * Equivalent to {@code _requires_shift()} in Python.
     */
    public static boolean requiresShift(String key) {
        if (key.length() == 1) {
            char c = key.charAt(0);
            if (Character.isUpperCase(c)) return true;
            if (c == '"') return true;
        }
        return Keymaps.SHIFT.containsKey(key);
    }
}
