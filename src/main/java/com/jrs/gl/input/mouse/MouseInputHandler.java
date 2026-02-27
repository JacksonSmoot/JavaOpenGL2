package com.jrs.gl.input.mouse;

import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

public final class MouseInputHandler {

    private final List<MouseInputListener> listeners = new ArrayList<>();
    private final long window;

    private double mx, my = 0;

    public MouseInputHandler(long window) {
        this.window = window;
        double[] pos = getMousePos(window);
        mx = (int) pos[0];
        my = (int) pos[1];
    }

    public void addListener(MouseInputListener l) { listeners.add(l); }
    public void removeListener(MouseInputListener l) { listeners.remove(l); }

    public double getMx(){
        return mx;
    }
    public double getMy(){
        return my;
    }
    // Create separate callback instances:
    public final GLFWMouseButtonCallbackI mouseButtonCallback = (win, button, action, mods) -> {
        double[] p = getMousePos(win);
        if (action == GLFW_PRESS) {
            for (var l : listeners)
                l.mouseButtonPressed(new MouseInputEvent(win, button, action, mods, p[0], p[1]));
        } else if (action == GLFW_RELEASE) {
            for (var l : listeners)
                l.mouseButtonReleased(new MouseInputEvent(win, button, action, mods, p[0], p[1]));
        }
    };

    public final GLFWCursorPosCallbackI cursorPosCallback = (win, x, y) -> {
        mx = x;
        my = y;
        for (var l : listeners)
            l.mouseMoved(new MouseInputEvent(win, 0, 0, 0, x, y));
    };

    public double[] getMousePos(long window) {
        // keep your existing version for now
        var xposBuffer = org.lwjgl.BufferUtils.createDoubleBuffer(1);
        var yposBuffer = org.lwjgl.BufferUtils.createDoubleBuffer(1);
        glfwGetCursorPos(window, xposBuffer, yposBuffer);
        return new double[]{xposBuffer.get(0), yposBuffer.get(0)};
    }
}