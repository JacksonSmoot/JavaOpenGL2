package com.jrs.gl.components;

import com.jrs.gl.g2d.GL2D;
import com.jrs.gl.input.keyboard.KeyboardInputHandler;
import com.jrs.gl.input.keyboard.KeyboardInputListener;
import com.jrs.gl.input.mouse.MouseInputHandler;
import com.jrs.gl.input.mouse.MouseInputListener;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.awt.Dimension;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public final class Window implements AutoCloseable{

    private long handle;

    private GL2D gl2d;

    private WindowBuilder windowBuilder;

    KeyboardInputHandler keyboardInputHandler;
    MouseInputHandler mouseInputHandler;

    static {
        GLFWErrorCallback.createPrint(System.err).set();
        if ( !GLFW.glfwInit() )
            throw new IllegalStateException("Unable to initialize GLFW");
    }

    public Window(WindowBuilder builder) {
        this.windowBuilder = builder;
        this.handle = builder.createHandle();
        gl2d = new GL2D(this);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer fbW = stack.mallocInt(1);
            IntBuffer fbH = stack.mallocInt(1);
            glfwGetFramebufferSize(handle, fbW, fbH);

            int w = fbW.get(0);
            int h = fbH.get(0);

            glViewport(0, 0, w, h);
        }

        keyboardInputHandler  = new KeyboardInputHandler(handle);
        mouseInputHandler = new MouseInputHandler(handle);

        glfwSetKeyCallback(handle, keyboardInputHandler);

        glfwSetMouseButtonCallback(handle, mouseInputHandler.mouseButtonCallback);
        glfwSetCursorPosCallback(handle, mouseInputHandler.cursorPosCallback);
    }

    public WindowBuilder getWindowBuilder(){
        return windowBuilder;
    }

    public void rebuild(WindowBuilder windowBuilder) {
        this.windowBuilder = windowBuilder;
        rebuild();
    }

    public void rebuild() {
        // free resources
        keyboardInputHandler.close();
        gl2d.close();
        close();

        this.handle = windowBuilder.createHandle();
        gl2d = new GL2D(this);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer fbW = stack.mallocInt(1);
            IntBuffer fbH = stack.mallocInt(1);
            glfwGetFramebufferSize(handle, fbW, fbH);

            int w = fbW.get(0);
            int h = fbH.get(0);

            glViewport(0, 0, w, h);
        }

        keyboardInputHandler  = new KeyboardInputHandler(handle);
        mouseInputHandler = new MouseInputHandler(handle);

        glfwSetKeyCallback(handle, keyboardInputHandler);

        glfwSetMouseButtonCallback(handle, mouseInputHandler.mouseButtonCallback);
        glfwSetCursorPosCallback(handle, mouseInputHandler.cursorPosCallback);


    }

    public void addMouseListener(MouseInputListener listener) {
        mouseInputHandler.addListener(listener);
    }

    public void addKeyboardListener(KeyboardInputListener kbdListener){
        keyboardInputHandler.addListener(kbdListener);
    }

    public double getMx(){
        return mouseInputHandler.getMx();
    }

    public double getMy(){
        return mouseInputHandler.getMy();
    }

    public Dimension getFramebufferSize(){
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer fbW = stack.mallocInt(1);
            IntBuffer fbH = stack.mallocInt(1);
            glfwGetFramebufferSize(handle, fbW, fbH);
            return new Dimension(fbW.get(0), fbH.get(0));
        }
    }

    public void setMousePassthrough(boolean enable){
        glfwSetWindowAttrib(handle, GLFW_MOUSE_PASSTHROUGH, enable ? GLFW_TRUE : GLFW_FALSE);
    }

    public void updateViewport(){
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer fbW = stack.mallocInt(1);
            IntBuffer fbH = stack.mallocInt(1);
            glfwGetFramebufferSize(handle, fbW, fbH);

            int w = fbW.get(0);
            int h = fbH.get(0);

            glViewport(0, 0, w, h);
        }
    }

    public void makeContextCurrent(){
        glfwMakeContextCurrent(handle);
    }

    private final IntBuffer intBuffer1_1 = BufferUtils.createIntBuffer(1);
    private final IntBuffer intBuffer2_1 = BufferUtils.createIntBuffer(1);

    public Dimension getWindowSize() {
        intBuffer1_1.clear();
        intBuffer2_1.clear();
        glfwGetWindowSize(handle, intBuffer1_1, intBuffer2_1);
        return new Dimension(intBuffer1_1.get(), intBuffer2_1.get());
    }

    public void setTitle(String title){
        glfwSetWindowTitle(handle, title);
    }

    public String getTitle(){
        return glfwGetWindowTitle(handle);
    }

    public void setSize(int width, int height) {
        glfwSetWindowSize(handle, width, height);
    }

    public void setVisible(boolean state){
        if(state) glfwShowWindow(handle);
        else glfwHideWindow(handle);
    }

    public long getHandle(){
        return handle;
    }
    public GL2D getGL2D(){return gl2d;}

    public void swapBuffers() { glfwSwapBuffers(handle); }
    public void pollEvents() { glfwPollEvents(); }
    public boolean shouldClose() { return glfwWindowShouldClose(handle); }

    @Override
    public void close() {
        glfwDestroyWindow(handle);
    }
}
