package com.jrs.gl.components;

import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.system.MemoryUtil.NULL;

public final class WindowBuilder {

    public static WindowBuilder defaultMacOS(){
        return new WindowBuilder()
                .visible(false).resizable(true)
                .glMajor(3).glMinor(3)
                .coreProfile(true).forwardCompat(true);
    }

    int width = 1280, height = 720;
    String title = "";
    boolean visible = false;

    boolean resizable = true;
    boolean decorated = true;
    boolean transparent = false;
    boolean floating = false;
    boolean mousePassthrough = false;

    int glMajor = 3, glMinor = 3;
    boolean coreProfile = true;
    boolean forwardCompat = true; // mac
    boolean vsync = true;

    public WindowBuilder visible(boolean visible){this.visible = visible;return this;}
    public WindowBuilder size(int w, int h) { this.width = w; this.height = h; return this; }
    public WindowBuilder resizable(boolean resizable){this.resizable = resizable; return this;}
    public WindowBuilder title(String t) { this.title = t; return this; }
    public WindowBuilder transparent(boolean v) { this.transparent = v; return this; }
    public WindowBuilder decorated(boolean v) { this.decorated = v; return this; }
    public WindowBuilder floating(boolean v) { this.floating = v; return this; }
    public WindowBuilder mousePassthrough(boolean v) { this.mousePassthrough = v; return this; }
    public WindowBuilder glVersion(int major, int minor) { this.glMajor = major; this.glMinor = minor; return this; }
    public WindowBuilder vsync(boolean v) { this.vsync = v; return this; }
    public WindowBuilder glMajor(int major) { this.glMajor = major; return this; }
    public WindowBuilder glMinor(int minor) { this.glMinor = minor; return this; }
    public WindowBuilder coreProfile(boolean v) { this.coreProfile = v; return this; }
    public WindowBuilder forwardCompat(boolean v) { this.forwardCompat = v; return this; }

    // The one "real" creation function
    long createHandle() {
        glfwDefaultWindowHints();

        glfwWindowHint(GLFW_VISIBLE, visible ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, resizable ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_DECORATED, decorated ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER, transparent ? GLFW_TRUE : GLFW_FALSE);

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, glMajor);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, glMinor);
        if (coreProfile) glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        if (forwardCompat) glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        long handle = glfwCreateWindow(width, height, title, NULL, NULL);

        if (handle == NULL) {
            CrashHandler.propException(new RuntimeException("Failed to create GLFW window"));
            System.exit(-1);
        }

        // runtime attributes (must be after create)
        if (floating) glfwSetWindowAttrib(handle, GLFW_FLOATING, GLFW_TRUE);
        if (mousePassthrough) glfwSetWindowAttrib(handle, GLFW_MOUSE_PASSTHROUGH, GLFW_TRUE);

        // context setup here OR in Window (pick one and be consistent)
        glfwMakeContextCurrent(handle);
        GL.createCapabilities();
        glfwSwapInterval(vsync ? 1 : 0);

        if(resizable){
            glfwSetFramebufferSizeCallback(handle, (win, fbW, fbH) -> {
                glViewport(0, 0, fbW, fbH);
            });
        }

        return handle;
    }
}