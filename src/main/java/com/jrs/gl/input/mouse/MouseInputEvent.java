package com.jrs.gl.input.mouse;

public record MouseInputEvent(long window, int button, int action, int mods, double x, double y){}
