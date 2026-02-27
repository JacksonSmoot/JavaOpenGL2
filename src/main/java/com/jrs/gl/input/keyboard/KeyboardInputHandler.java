package com.jrs.gl.input.keyboard;

import org.lwjgl.glfw.GLFWKeyCallback;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

public class KeyboardInputHandler extends GLFWKeyCallback {
    public static boolean[] keys = new boolean[65536]; // Max key count in GLFW

    private final long window;

    private final List<KeyboardInputListener> kbd_listeners = new ArrayList<>();

    public KeyboardInputHandler(long window) {
        this.window = window;
    }

    public void addListener(KeyboardInputListener kbd_listener) {
        kbd_listeners.add(kbd_listener);
    }

    public void removeListener(KeyboardInputListener kbd_listener) {
        kbd_listeners.remove(kbd_listener);
    }

    public ArrayList<KeyboardInputListener> getListeners() {
        return new ArrayList<>(kbd_listeners);
    }

    @Override
    public void invoke(long window, int key, int scancode, int action, int mods) {
        if (key >= 0 && key < keys.length) {
            // Update the boolean array based on the action (press, repeat, or release)
            keys[key] = action != GLFW_RELEASE;

            KeyboardInputEvent event = new KeyboardInputEvent(window, key, scancode, action, mods);
            if(action == GLFW_RELEASE) {
                for(KeyboardInputListener listener : kbd_listeners) {
                    listener.keyReleased(event);
                }
            }
            else if(action == GLFW_PRESS) {
                for(KeyboardInputListener listener : kbd_listeners) {
                    listener.keyPressed(event);
                }
            }
        }
    }

    // Helper method to check the state from other parts of your code
    public static boolean isKeyDown(int keycode) {
        return keys[keycode];
    }
}
