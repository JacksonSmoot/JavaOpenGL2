package com.jrs.gl.input.keyboard;

public record KeyboardInputEvent (long window, int key, int scancode, int action, int mods){}
