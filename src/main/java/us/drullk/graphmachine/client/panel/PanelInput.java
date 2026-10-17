package us.drullk.graphmachine.client.panel;

import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ScreenEvent;
import us.drullk.graphmachine.client.GraphMachineClient;
import us.drullk.graphmachine.client.render.GraphRenderer;

/**
 * Feeds mouse and keyboard events to ImGui while a screen is open and keeps them from the screen when
 * the panel wants them.
 *
 * <p>The panel is interactive only while some {@code Screen} is open (the chat, a menu), because that is
 * when the cursor is free. Clicks and scrolls over the panel strip are always taken; key presses and
 * typed characters are taken while an ImGui widget wants the keyboard, except Escape, which always
 * reaches the screen so it can close. The panel toggle key works inside screens too. The mouse position is refreshed once per frame from
 * {@link MouseHandler}, in framebuffer pixels, and parked far away while no screen is open.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class PanelInput {
    private static boolean wantMouse;
    private static boolean wantKeyboard;
    private static boolean wantText;

    private PanelInput() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(PanelInput::onMousePressed);
        gameBus.addListener(PanelInput::onMouseReleased);
        gameBus.addListener(PanelInput::onMouseDragged);
        gameBus.addListener(PanelInput::onMouseScrolled);
        gameBus.addListener(PanelInput::onKeyPressed);
        gameBus.addListener(PanelInput::onKeyReleased);
        gameBus.addListener(PanelInput::onCharTyped);
    }

    private static boolean interactive() {
        return PanelLayout.active() && GraphImGuiContext.hasContext() && Minecraft.getInstance().gui.screen() != null;
    }

    /** True when the window position is inside the panel strip. */
    public static boolean overPanel(double framebufferX) {
        return framebufferX >= Minecraft.getInstance().getWindow().getWidth();
    }

    private static double mouseFramebufferX() {
        return Minecraft.getInstance().mouseHandler.xpos() * PanelLayout.density();
    }

    /** Called by the compositor before each ImGui frame. */
    static void updateMousePosition(float density) {
        ImGuiIO io = ImGui.getIO();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() != null) {
            io.addMousePosEvent((float) (minecraft.mouseHandler.xpos() * density), (float) (minecraft.mouseHandler.ypos() * density));
        } else {
            io.addMousePosEvent(-Float.MAX_VALUE, -Float.MAX_VALUE);
        }
    }

    /** Called by the compositor after each ImGui frame; remembers what ImGui wants until the next frame. */
    static void afterFrame(ImGuiIO io) {
        wantMouse = io.getWantCaptureMouse();
        wantKeyboard = io.getWantCaptureKeyboard();
        wantText = io.getWantTextInput();
    }

    private static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!interactive()) {
            return;
        }
        ImGuiIO io = ImGui.getIO();
        ImGuiInput.modifiers(io, event.getMouseButtonEvent().modifiers());
        int button = ImGuiInput.mouseButton(event.getButton());
        if (button >= 0) {
            io.addMouseButtonEvent(button, true);
        }
        if (wantMouse || overPanel(mouseFramebufferX())) {
            event.setCanceled(true);
        }
    }

    private static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!interactive()) {
            return;
        }
        int button = ImGuiInput.mouseButton(event.getButton());
        if (button >= 0) {
            ImGui.getIO().addMouseButtonEvent(button, false);
        }
        if (wantMouse || overPanel(mouseFramebufferX())) {
            event.setCanceled(true);
        }
    }

    private static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (interactive() && wantMouse) {
            event.setCanceled(true);
        }
    }

    private static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (!interactive()) {
            return;
        }
        if (wantMouse || overPanel(mouseFramebufferX())) {
            ImGui.getIO().addMouseWheelEvent((float) event.getScrollDeltaX(), (float) event.getScrollDeltaY());
            event.setCanceled(true);
        }
    }

    private static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (GraphMachineClient.TOGGLE_PANEL.matches(event.getKeyEvent()) && GraphRenderer.shouldDraw()) {
            GraphMachineClient.togglePanel();
            event.setCanceled(true);
            return;
        }
        if (!interactive() || !wantKeyboard || event.getKeyEvent().isEscape()) {
            return;
        }
        ImGuiInput.keyEvent(ImGui.getIO(), event.getKeyEvent(), true);
        event.setCanceled(true);
    }

    private static void onKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        if (!interactive() || !wantKeyboard || event.getKeyEvent().isEscape()) {
            return;
        }
        ImGuiInput.keyEvent(ImGui.getIO(), event.getKeyEvent(), false);
        event.setCanceled(true);
    }

    private static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (!interactive() || !wantText) {
            return;
        }
        ImGui.getIO().addInputCharacter(event.getCodePoint());
        event.setCanceled(true);
    }
}
