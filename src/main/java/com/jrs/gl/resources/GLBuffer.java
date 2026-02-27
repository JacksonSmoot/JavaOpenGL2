package com.jrs.gl.resources;

public final class GLBuffer implements AutoCloseable {
    final int id = org.lwjgl.opengl.GL15.glGenBuffers();
    final int target;
    public GLBuffer(int target) { this.target = target; }
    public void bind() { org.lwjgl.opengl.GL15.glBindBuffer(target, id); }
    public static void unbind(int target) { org.lwjgl.opengl.GL15.glBindBuffer(target, 0); }
    @Override public void close() { org.lwjgl.opengl.GL15.glDeleteBuffers(id); }
}