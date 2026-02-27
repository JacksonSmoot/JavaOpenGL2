package com.jrs.gl.input.mouse;

public interface MouseInputListener {
    void mouseButtonPressed(MouseInputEvent e);
    void mouseButtonReleased(MouseInputEvent e);
    void mouseMoved(MouseInputEvent e);
    void mouseDragged(MouseInputEvent e);
}
