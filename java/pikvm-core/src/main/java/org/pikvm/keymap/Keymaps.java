package org.pikvm.keymap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Keyboard maps ported from Python {@code keymaps.py}.
 * <ul>
 *   <li>{@link #BASE}      – base key map (special keys, punctuation, control sequences)</li>
 *   <li>{@link #SHIFT}     – characters that require Shift modifier</li>
 *   <li>{@link #PYAUTOGUI} – pyautogui-compatible alias map</li>
 * </ul>
 * All maps are immutable.
 */
public final class Keymaps {

    private Keymaps() {}

    /** Base keymap: input string → HID key code. */
    public static final Map<String, String> BASE;

    /** Shift keymap: character → HID key code (Shift must be held). */
    public static final Map<String, String> SHIFT;

    /** pyautogui-compatible alias map. */
    public static final Map<String, String> PYAUTOGUI;

    static {
        Map<String, String> base = new HashMap<>();
        base.put("\n",          "Enter");
        base.put("\b",          "Backspace");
        base.put("\t",          "Tab");
        base.put("\s",          "Space");
        base.put("-",            "Minus");
        base.put("=",            "Equal");
        base.put("[",            "BracketLeft");
        base.put("]",            "BracketRight");
        base.put("\\",         "Backslash");
        base.put(";",            "Semicolon");
        base.put("'",           "Quote");
        base.put("`",            "Backquote");
        base.put(",",            "Comma");
        base.put(".",            "Period");
        base.put("/",            "Slash");
        base.put("<Esc>",        "Escape");
        base.put("<Enter>",      "Enter");
        base.put("<CapsLock>",   "CapsLock");
        base.put("<F1>",         "F1");
        base.put("<F2>",         "F2");
        base.put("<F3>",         "F3");
        base.put("<F4>",         "F4");
        base.put("<F5>",         "F5");
        base.put("<F6>",         "F6");
        base.put("<F7>",         "F7");
        base.put("<F8>",         "F8");
        base.put("<F9>",         "F9");
        base.put("<F10>",        "F10");
        base.put("<F11>",        "F11");
        base.put("<F12>",        "F12");
        base.put("<PrintScreen>","PrintScreen");
        base.put("<Insert>",     "Insert");
        base.put("<Home>",       "Home");
        base.put("<PageUp>",     "PageUp");
        base.put("<Delete>",     "Delete");
        base.put("<End>",        "End");
        base.put("<PageDown>",   "PageDown");
        base.put("<ArrowRight>", "ArrowRight");
        base.put("<ArrowLeft>",  "ArrowLeft");
        base.put("<ArrowDown>",  "ArrowDown");
        base.put("<ArrowUp>",    "ArrowUp");
        base.put("<ControlLeft>","ControlLeft");
        base.put("<ShiftLeft>",  "ShiftLeft");
        base.put("<AltLeft>",    "AltLeft");
        base.put("<MetaLeft>",   "MetaLeft");
        base.put("<ControlRight>","ControlRight");
        base.put("<ShiftRight>", "ShiftRight");
        base.put("<AltRight>",   "AltRight");
        base.put("<MetaRight>",  "MetaRight");
        base.put("<Pause>",      "Pause");
        base.put("<Power>",      "Power");
        BASE = Collections.unmodifiableMap(base);

        Map<String, String> shift = new HashMap<>();
        shift.put("_", "Minus");
        shift.put("+", "Equal");
        shift.put("{", "BracketLeft");
        shift.put("}", "BracketRight");
        shift.put("|", "Backslash");
        shift.put(":", "Semicolon");
        shift.put("\"", "Quote");
        shift.put("~", "Backquote");
        shift.put("<", "Comma");
        shift.put(">", "Period");
        shift.put("?", "Slash");
        shift.put("!", "Digit1");
        shift.put("@", "Digit2");
        shift.put("#", "Digit3");
        shift.put("$", "Digit4");
        shift.put("%", "Digit5");
        shift.put("^", "Digit6");
        shift.put("&", "Digit7");
        shift.put("*", "Digit8");
        shift.put("(", "Digit9");
        shift.put(")", "Digit0");
        SHIFT = Collections.unmodifiableMap(shift);

        Map<String, String> pg = new HashMap<>();
        pg.put("\t",          "Tab");
        pg.put("\n",          "Enter");
        pg.put("\r",          "Enter");
        pg.put("'",           "Quote");
        pg.put(",",            "Comma");
        pg.put("-",            "Minus");
        pg.put(".",            "Period");
        pg.put("/",            "Slash");
        pg.put(";",            "Semicolon");
        pg.put("=",            "Equal");
        pg.put("[",            "BracketLeft");
        pg.put("\\",         "Backslash");
        pg.put("]",            "BracketRight");
        pg.put("`",            "Backquote");
        pg.put("add",          "NumpadAdd");
        pg.put("alt",          "AltLeft");
        pg.put("altleft",      "AltLeft");
        pg.put("altright",     "AltRight");
        pg.put("backspace",    "Backspace");
        pg.put("capslock",     "CapsLock");
        pg.put("convert",      "Convert");
        pg.put("ctrl",         "ControlLeft");
        pg.put("ctrlleft",     "ControlLeft");
        pg.put("ctrlright",    "ControlRight");
        pg.put("decimal",      "NumpadDecimal");
        pg.put("del",          "Delete");
        pg.put("delete",       "Delete");
        pg.put("divide",       "NumpadDivide");
        pg.put("down",         "ArrowDown");
        pg.put("end",          "End");
        pg.put("enter",        "Enter");
        pg.put("esc",          "Escape");
        pg.put("escape",       "Escape");
        pg.put("f1",           "F1");  pg.put("f2",  "F2");  pg.put("f3",  "F3");
        pg.put("f4",           "F4");  pg.put("f5",  "F5");  pg.put("f6",  "F6");
        pg.put("f7",           "F7");  pg.put("f8",  "F8");  pg.put("f9",  "F9");
        pg.put("f10",          "F10"); pg.put("f11", "F11"); pg.put("f12", "F12");
        pg.put("f13",          "F13"); pg.put("f14", "F14"); pg.put("f15", "F15");
        pg.put("f16",          "F16"); pg.put("f17", "F17"); pg.put("f18", "F18");
        pg.put("f19",          "F19"); pg.put("f20", "F20"); pg.put("f21", "F21");
        pg.put("f22",          "F22"); pg.put("f23", "F23"); pg.put("f24", "F24");
        pg.put("home",         "Home");
        pg.put("insert",       "Insert");
        pg.put("kana",         "KanaMode");
        pg.put("left",         "ArrowLeft");
        pg.put("multiply",     "NumpadMultiply");
        pg.put("nonconvert",   "NonConvert");
        pg.put("num0",         "Numpad0"); pg.put("num1", "Numpad1");
        pg.put("num2",         "Numpad2"); pg.put("num3", "Numpad3");
        pg.put("num4",         "Numpad4"); pg.put("num5", "Numpad5");
        pg.put("num6",         "Numpad6"); pg.put("num7", "Numpad7");
        pg.put("num8",         "Numpad8"); pg.put("num9", "Numpad9");
        pg.put("numlock",      "NumLock");
        pg.put("pagedown",     "PageDown");
        pg.put("pageup",       "PageUp");
        pg.put("pause",        "Pause");
        pg.put("pgdn",         "PageDown");
        pg.put("pgup",         "PageUp");
        pg.put("print",        "PrintScreen");
        pg.put("printscreen",  "PrintScreen");
        pg.put("prntscrn",     "PrintScreen");
        pg.put("prtsc",        "PrintScreen");
        pg.put("prtscr",       "PrintScreen");
        pg.put("return",       "Enter");
        pg.put("right",        "ArrowRight");
        pg.put("scrolllock",   "ScrollLock");
        pg.put("shift",        "ShiftLeft");
        pg.put("shiftleft",    "ShiftLeft");
        pg.put("shiftright",   "ShiftRight");
        pg.put("space",        "Space");
        pg.put("subtract",     "NumpadSubtract");
        pg.put("tab",          "Tab");
        pg.put("up",           "ArrowUp");
        pg.put("win",          "MetaLeft");
        pg.put("winleft",      "MetaLeft");
        pg.put("winright",     "MetaRight");
        pg.put("yen",          "IntlYen");
        pg.put("command",      "MetaLeft");
        pg.put("option",       "AltLeft");
        pg.put("optionleft",   "AltLeft");
        pg.put("optionright",  "AltRight");
        PYAUTOGUI = Collections.unmodifiableMap(pg);
    }
}
