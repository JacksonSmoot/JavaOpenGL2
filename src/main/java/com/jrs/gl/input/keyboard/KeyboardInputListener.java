package com.jrs.gl.input.keyboard;

public interface KeyboardInputListener {
    void keyPressed(KeyboardInputEvent e);
    void keyReleased(KeyboardInputEvent e);
    void keyHeld(KeyboardInputEvent e);
}
