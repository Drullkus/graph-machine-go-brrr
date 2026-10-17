package us.drullk.graphmachine.client.panel;

import com.mojang.blaze3d.platform.InputConstants;
import imgui.ImGuiIO;
import imgui.flag.ImGuiKey;
import net.minecraft.client.input.KeyEvent;

import java.util.Arrays;

/**
 * Translates Minecraft's SDL3 input events into Dear ImGui input events.
 *
 * <p>Minecraft 26.3 reports keys as SDL scancodes (see {@link InputConstants}); the table here maps those to
 * {@link ImGuiKey} values. Modifier state is taken from the event's modifier mask so it stays right after a focus
 * change.
 *
 * <p>Ported from the Noodling mod, where Claude Code wrote it under the transcript
 * {@code transcript_2026-10-10_25b84f50-3f88-41a1-a942-98824ffe67dc.md} of that repository.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class ImGuiInput {

    private static final int[] KEY_MAP = new int[512];

    static {
        Arrays.fill(KEY_MAP, ImGuiKey.None);
        for (int i = 0; i < 26; i++) {
            KEY_MAP[InputConstants.KEY_A + i] = ImGuiKey.A + i;
        }
        for (int i = 0; i < 9; i++) {
            KEY_MAP[InputConstants.KEY_1 + i] = ImGuiKey._1 + i;
        }
        KEY_MAP[InputConstants.KEY_0] = ImGuiKey._0;
        for (int i = 0; i < 12; i++) {
            KEY_MAP[InputConstants.KEY_F1 + i] = ImGuiKey.F1 + i;
            KEY_MAP[InputConstants.KEY_F13 + i] = ImGuiKey.F13 + i;
        }
        for (int i = 0; i < 9; i++) {
            KEY_MAP[InputConstants.KEY_NUMPAD1 + i] = ImGuiKey.Keypad1 + i;
        }
        KEY_MAP[InputConstants.KEY_NUMPAD0] = ImGuiKey.Keypad0;
        map(InputConstants.KEY_RETURN, ImGuiKey.Enter);
        map(InputConstants.KEY_ESCAPE, ImGuiKey.Escape);
        map(InputConstants.KEY_BACKSPACE, ImGuiKey.Backspace);
        map(InputConstants.KEY_TAB, ImGuiKey.Tab);
        map(InputConstants.KEY_SPACE, ImGuiKey.Space);
        map(InputConstants.KEY_MINUS, ImGuiKey.Minus);
        map(InputConstants.KEY_EQUALS, ImGuiKey.Equal);
        map(InputConstants.KEY_LBRACKET, ImGuiKey.LeftBracket);
        map(InputConstants.KEY_RBRACKET, ImGuiKey.RightBracket);
        map(InputConstants.KEY_BACKSLASH, ImGuiKey.Backslash);
        map(InputConstants.KEY_SEMICOLON, ImGuiKey.Semicolon);
        map(InputConstants.KEY_APOSTROPHE, ImGuiKey.Apostrophe);
        map(InputConstants.KEY_GRAVE, ImGuiKey.GraveAccent);
        map(InputConstants.KEY_COMMA, ImGuiKey.Comma);
        map(InputConstants.KEY_PERIOD, ImGuiKey.Period);
        map(InputConstants.KEY_SLASH, ImGuiKey.Slash);
        map(InputConstants.KEY_CAPSLOCK, ImGuiKey.CapsLock);
        map(InputConstants.KEY_PRINTSCREEN, ImGuiKey.PrintScreen);
        map(InputConstants.KEY_SCROLLLOCK, ImGuiKey.ScrollLock);
        map(InputConstants.KEY_PAUSE, ImGuiKey.Pause);
        map(InputConstants.KEY_INSERT, ImGuiKey.Insert);
        map(InputConstants.KEY_HOME, ImGuiKey.Home);
        map(InputConstants.KEY_PAGEUP, ImGuiKey.PageUp);
        map(InputConstants.KEY_DELETE, ImGuiKey.Delete);
        map(InputConstants.KEY_END, ImGuiKey.End);
        map(InputConstants.KEY_PAGEDOWN, ImGuiKey.PageDown);
        map(InputConstants.KEY_RIGHT, ImGuiKey.RightArrow);
        map(InputConstants.KEY_LEFT, ImGuiKey.LeftArrow);
        map(InputConstants.KEY_DOWN, ImGuiKey.DownArrow);
        map(InputConstants.KEY_UP, ImGuiKey.UpArrow);
        map(InputConstants.KEY_NUMLOCK, ImGuiKey.NumLock);
        map(InputConstants.KEY_MULTIPLY, ImGuiKey.KeypadMultiply);
        map(InputConstants.KEY_ADD, ImGuiKey.KeypadAdd);
        map(InputConstants.KEY_NUMPADENTER, ImGuiKey.KeypadEnter);
        map(InputConstants.KEY_NUMPADEQUALS, ImGuiKey.KeypadEqual);
        map(InputConstants.KEY_NUMPADCOMMA, ImGuiKey.KeypadDecimal);
        map(InputConstants.KEY_LCONTROL, ImGuiKey.LeftCtrl);
        map(InputConstants.KEY_LSHIFT, ImGuiKey.LeftShift);
        map(InputConstants.KEY_LALT, ImGuiKey.LeftAlt);
        map(InputConstants.KEY_LGUI, ImGuiKey.LeftSuper);
        map(InputConstants.KEY_RCONTROL, ImGuiKey.RightCtrl);
        map(InputConstants.KEY_RSHIFT, ImGuiKey.RightShift);
        map(InputConstants.KEY_RALT, ImGuiKey.RightAlt);
        map(InputConstants.KEY_RGUI, ImGuiKey.RightSuper);
        // SDL scancodes that InputConstants has no name for.
        map(84, ImGuiKey.KeypadDivide);
        map(86, ImGuiKey.KeypadSubtract);
        map(99, ImGuiKey.KeypadDecimal);
        map(101, ImGuiKey.Menu);
    }

    private ImGuiInput() {
    }

    private static void map(int scancode, int imguiKey) {
        if (scancode >= 0 && scancode < KEY_MAP.length) {
            KEY_MAP[scancode] = imguiKey;
        }
    }

    /** The ImGui key for an SDL scancode, or {@link ImGuiKey#None}. */
    public static int key(int scancode) {
        return scancode >= 0 && scancode < KEY_MAP.length ? KEY_MAP[scancode] : ImGuiKey.None;
    }

    /** The ImGui mouse button index for a Minecraft button, or -1 for buttons ImGui does not track. */
    public static int mouseButton(int button) {
        return switch (button) {
            case InputConstants.MOUSE_BUTTON_LEFT -> 0;
            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
            case InputConstants.MOUSE_BUTTON_4 -> 3;
            case InputConstants.MOUSE_BUTTON_5 -> 4;
            default -> -1;
        };
    }

    /** Forwards a key press or release, including the modifier state carried by the event. */
    public static void keyEvent(ImGuiIO io, KeyEvent event, boolean down) {
        modifiers(io, event.modifiers());
        int key = key(event.key());
        if (key != ImGuiKey.None) {
            io.addKeyEvent(key, down);
        }
    }

    /** Forwards the modifier mask of a mouse or key event. */
    public static void modifiers(ImGuiIO io, int modifiers) {
        io.addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, (modifiers & InputConstants.MOD_CONTROL) != 0);
        io.addKeyEvent(ImGuiKey.ImGuiMod_Shift, (modifiers & InputConstants.MOD_SHIFT) != 0);
        io.addKeyEvent(ImGuiKey.ImGuiMod_Alt, (modifiers & InputConstants.MOD_ALT) != 0);
        io.addKeyEvent(ImGuiKey.ImGuiMod_Super, (modifiers & InputConstants.MOD_SUPER) != 0);
    }
}
