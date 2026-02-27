package com.jrs.gl.resources;

public final class GLVertexArray implements AutoCloseable {
    final int id = org.lwjgl.opengl.GL30.glGenVertexArrays();
    public void bind() { org.lwjgl.opengl.GL30.glBindVertexArray(id); }
    public static void unbind() { org.lwjgl.opengl.GL30.glBindVertexArray(0); }
    @Override public void close() { org.lwjgl.opengl.GL30.glDeleteVertexArrays(id); }
}